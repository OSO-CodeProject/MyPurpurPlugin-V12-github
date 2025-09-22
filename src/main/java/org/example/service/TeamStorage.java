package org.example.service;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.example.config.PluginConfig;
import org.example.model.Team;
import org.jetbrains.annotations.NotNull;

/** Handles persistence of team data and keeps track of player memberships. */
public class TeamStorage {

  private final JavaPlugin plugin;
  private final PluginConfig pluginConfig;

  private final Map<UUID, Team> teams = new ConcurrentHashMap<>();
  private final Map<String, UUID> teamIdsByName = new ConcurrentHashMap<>();
  private final Map<UUID, UUID> playerTeams = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, UUID> resolvedProfileCache = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, CompletableFuture<UUID>> pendingProfileLookups =
      new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, PendingTeamLoad> pendingTeamLoads = new ConcurrentHashMap<>();

  private FileConfiguration teamsConfig;
  private File teamsFile;

  private final Set<UUID> dirtyTeams = ConcurrentHashMap.newKeySet();
  private volatile boolean deadlinesDirty;
  private BukkitTask autoSaveTask;
  private final Object saveLock = new Object();
  private Map<UUID, Long> deadlinesReference = Collections.emptyMap();
  private volatile boolean autoSaveEnabled;

  public TeamStorage(@NotNull JavaPlugin plugin, @NotNull PluginConfig pluginConfig) {
    this.plugin = plugin;
    this.pluginConfig = pluginConfig;
  }

  /** Loads team information from teams.yml and fills provided deadlines map. */
  public void loadTeams(Map<UUID, Long> deadlines) {
    synchronized (saveLock) {
      deadlinesReference = deadlines;
      teamsFile = new File(plugin.getDataFolder(), "teams.yml");
      if (!teamsFile.exists()) {
        try {
          teamsFile.getParentFile().mkdirs();
          teamsFile.createNewFile();
        } catch (IOException e) {
          plugin.getLogger().severe("Failed to create teams.yml: " + e.getMessage());
        }
      }
      teamsConfig = YamlConfiguration.loadConfiguration(teamsFile);
      teams.clear();
      teamIdsByName.clear();
      playerTeams.clear();
      pendingTeamLoads.clear();
      deadlines.clear();
      var section = teamsConfig.getConfigurationSection("teams");
      boolean legacyDataUpdated = false;
      if (section != null) {
        for (String teamIdStr : section.getKeys(false)) {
          UUID teamId;
          try {
            teamId = UUID.fromString(teamIdStr);
          } catch (IllegalArgumentException ex) {
            plugin
                .getLogger()
                .warning("Skipping team entry with invalid UUID '" + teamIdStr + "'.");
            continue;
          }
          String name = teamsConfig.getString("teams." + teamIdStr + ".name", "");
          if (name == null || name.isBlank()) {
            plugin
                .getLogger()
                .warning("Skipping team entry " + teamId + " because it does not define a name.");
            continue;
          }
          String leaderRaw = teamsConfig.getString("teams." + teamIdStr + ".leader", "");
          UUID leaderId = parseUuid(leaderRaw);
          boolean leaderFromName = false;
          String prefix = teamsConfig.getString("teams." + teamIdStr + ".prefix", "");
          String color =
              normalizeColorKey(teamsConfig.getString("teams." + teamIdStr + ".color", "WHITE"));
          List<String> rawMembers = teamsConfig.getStringList("teams." + teamIdStr + ".members");
          long deadline = teamsConfig.getLong("teams." + teamIdStr + ".deadline", 0L);
          if (leaderId == null) {
            if (leaderRaw == null || leaderRaw.isBlank()) {
              plugin
                  .getLogger()
                  .warning(
                      "Skipping team entry " + teamId + " because it does not define a leader.");
              continue;
            }
            leaderId =
                resolvePlayerUuid(
                    leaderRaw,
                    resolved ->
                        handlePendingLeaderResolution(
                            new PendingTeamLoad(
                                teamId,
                                teamIdStr,
                                name,
                                leaderRaw,
                                prefix,
                                color,
                                rawMembers,
                                deadline),
                            resolved));
            leaderFromName = leaderId != null;
          }
          if (leaderId == null) {
            PendingTeamLoad pending =
                new PendingTeamLoad(
                    teamId, teamIdStr, name, leaderRaw, prefix, color, rawMembers, deadline);
            pendingTeamLoads.put(teamId, pending);
            plugin
                .getLogger()
                .info(
                    "Queued asynchronous lookup for leader '"
                        + leaderRaw
                        + "' while loading team "
                        + teamId
                        + ". Team data will be restored once the profile resolves.");
            continue;
          }
          TeamLoadResult result =
              loadTeamFromConfig(
                  teamId,
                  teamIdStr,
                  name,
                  leaderId,
                  prefix,
                  color,
                  rawMembers,
                  deadline,
                  deadlines);
          if (result == null) {
            continue;
          }
          legacyDataUpdated = legacyDataUpdated || leaderFromName || result.convertedFromName();
        }
      }
      dirtyTeams.clear();
      deadlinesDirty = false;
      if (legacyDataUpdated) {
        saveTeams(deadlines);
      }
    }
  }

  /** Saves teams and deadlines back to teams.yml. */
  public void saveTeams(Map<UUID, Long> deadlines) {
    synchronized (saveLock) {
      if (teamsConfig == null || teamsFile == null) return;
      teamsConfig.set("teams", null);
      for (Map.Entry<UUID, Team> entry : teams.entrySet()) {
        UUID teamId = entry.getKey();
        Team team = entry.getValue();
        String path = "teams." + teamId;
        teamsConfig.set(path + ".name", team.getName());
        UUID leaderId = team.getLeaderId();
        teamsConfig.set(path + ".leader", leaderId != null ? leaderId.toString() : null);
        teamsConfig.set(
            path + ".members",
            team.getMembers().stream().map(UUID::toString).collect(Collectors.toList()));
        teamsConfig.set(path + ".prefix", team.getPrefix());
        teamsConfig.set(path + ".color", colorKeyFor(team.getColor()));
        Long deadline = deadlines.get(teamId);
        if (deadline != null) {
          teamsConfig.set(path + ".deadline", deadline);
        }
      }
      try {
        teamsConfig.save(teamsFile);
      } catch (IOException e) {
        plugin.getLogger().warning("Failed to save teams.yml: " + e.getMessage());
      }
    }
  }

  public Map<UUID, Team> getTeams() {
    return teams;
  }

  public void addTeam(@NotNull Team team) {
    synchronized (saveLock) {
      addTeamInternal(team, true);
    }
  }

  private void addTeamInternal(@NotNull Team team, boolean markDirty) {
    teams.put(team.getId(), team);
    String normalizedName = normalizeTeamKey(team.getName());
    if (normalizedName != null) {
      teamIdsByName.put(normalizedName, team.getId());
    }
    if (markDirty) {
      markTeamDirty(team);
    }
  }

  public void removeTeam(@NotNull Team team) {
    synchronized (saveLock) {
      teams.remove(team.getId());
      String normalizedName = normalizeTeamKey(team.getName());
      if (normalizedName != null) {
        teamIdsByName.remove(normalizedName);
      }
      markTeamDirty(team);
    }
  }

  public void updateTeamName(@NotNull Team team, @NotNull String newName) {
    synchronized (saveLock) {
      String normalizedNewNameValue = newName.trim();
      String currentName = team.getName();
      if (Objects.equals(currentName, normalizedNewNameValue)) {
        return;
      }
      String normalizedNewName = normalizeTeamKey(normalizedNewNameValue);
      if (normalizedNewName != null) {
        UUID existingId = teamIdsByName.get(normalizedNewName);
        if (existingId != null && !existingId.equals(team.getId())) {
          return;
        }
      }
      String normalizedCurrentName = normalizeTeamKey(currentName);
      if (normalizedCurrentName != null) {
        teamIdsByName.remove(normalizedCurrentName);
      }
      team.setName(normalizedNewNameValue);
      if (normalizedNewName != null) {
        teamIdsByName.put(normalizedNewName, team.getId());
      }
      markTeamDirty(team);
    }
  }

  public Map<UUID, UUID> getPlayerTeams() {
    return playerTeams;
  }

  public Team getTeamByName(String teamName) {
    String normalizedName = normalizeTeamKey(teamName);
    if (normalizedName == null) {
      return null;
    }
    UUID teamId = teamIdsByName.get(normalizedName);
    return teamId != null ? teams.get(teamId) : null;
  }

  public UUID getTeamIdByName(String teamName) {
    String normalizedName = normalizeTeamKey(teamName);
    return normalizedName != null ? teamIdsByName.get(normalizedName) : null;
  }

  public String getPlayerTeam(@NotNull Player player) {
    UUID id = playerTeams.get(player.getUniqueId());
    Team team = id != null ? teams.get(id) : null;
    return team != null ? team.getName() : null;
  }

  @NotNull
  public List<UUID> getTeamMembers(String teamName) {
    Team team = getTeamByName(teamName);
    return team != null ? new ArrayList<>(team.getMembers()) : List.of();
  }

  @NotNull
  public List<String> getTeamNames() {
    return teams.values().stream().map(Team::getName).collect(Collectors.toList());
  }

  public String getTeamPrefix(String teamName) {
    Team team = getTeamByName(teamName);
    return team != null ? team.getPrefix() : "";
  }

  @NotNull
  public NamedTextColor getTeamColor(String teamName) {
    Team team = getTeamByName(teamName);
    return team != null ? team.getColor() : NamedTextColor.WHITE;
  }

  public UUID getTeamLeaderId(String teamName) {
    Team team = getTeamByName(teamName);
    return team != null ? team.getLeaderId() : null;
  }

  public void markTeamDirty(@NotNull Team team) {
    markTeamDirty(team.getId());
  }

  public void markTeamDirty(@NotNull UUID teamId) {
    dirtyTeams.add(teamId);
    scheduleImmediateSaveIfDisabled();
  }

  public void markDeadlinesDirty() {
    deadlinesDirty = true;
    scheduleImmediateSaveIfDisabled();
  }

  private void scheduleImmediateSaveIfDisabled() {
    if (!autoSaveEnabled) {
      flushIfDirty(null);
    }
  }

  public synchronized void startAutoSave(long intervalSeconds, @NotNull Map<UUID, Long> deadlines) {
    synchronized (saveLock) {
      deadlinesReference = deadlines;
    }
    if (autoSaveTask != null) {
      autoSaveTask.cancel();
      autoSaveTask = null;
    }
    autoSaveEnabled = intervalSeconds > 0;
    if (!autoSaveEnabled) {
      flushIfDirty(null);
      return;
    }
    long ticks = Math.max(1L, intervalSeconds) * 20L;
    autoSaveTask =
        plugin
            .getServer()
            .getScheduler()
            .runTaskTimerAsynchronously(plugin, this::flushNow, ticks, ticks);
  }

  public synchronized void stopAutoSave() {
    if (autoSaveTask != null) {
      autoSaveTask.cancel();
      autoSaveTask = null;
    }
    autoSaveEnabled = false;
  }

  public void flushNow() {
    flushIfDirty(null);
  }

  public void flushIfDirty(Map<UUID, Long> deadlines) {
    if (!deadlinesDirty && dirtyTeams.isEmpty()) {
      return;
    }
    synchronized (saveLock) {
      if (!deadlinesDirty && dirtyTeams.isEmpty()) {
        return;
      }
      saveTeams(deadlines != null ? deadlines : deadlinesReference);
      dirtyTeams.clear();
      deadlinesDirty = false;
    }
  }

  private static final String DEFAULT_COLOR_KEY =
      Objects.requireNonNull(NamedTextColor.NAMES.key(NamedTextColor.WHITE));

  private static String colorKeyFor(NamedTextColor color) {
    if (color == null) {
      return DEFAULT_COLOR_KEY;
    }
    String key = NamedTextColor.NAMES.key(color);
    return key != null ? key : DEFAULT_COLOR_KEY;
  }

  private static String normalizeColorKey(String rawColor) {
    if (rawColor == null || rawColor.isBlank()) {
      return DEFAULT_COLOR_KEY;
    }
    String trimmed = rawColor.trim();
    NamedTextColor direct = NamedTextColor.NAMES.value(trimmed.toLowerCase(Locale.ROOT));
    if (direct != null) {
      String key = NamedTextColor.NAMES.key(direct);
      return key != null ? key : DEFAULT_COLOR_KEY;
    }
    if (trimmed.startsWith("NamedTextColor{")) {
      int nameIndex = trimmed.indexOf("name=");
      if (nameIndex >= 0) {
        int separatorIndex = trimmed.indexOf(',', nameIndex);
        int endIndex = separatorIndex >= 0 ? separatorIndex : trimmed.indexOf('}', nameIndex);
        if (endIndex > nameIndex + 5) {
          String candidate = trimmed.substring(nameIndex + 5, endIndex).trim();
          if (!candidate.isEmpty()) {
            NamedTextColor named = NamedTextColor.NAMES.value(candidate.toLowerCase(Locale.ROOT));
            if (named != null) {
              String key = NamedTextColor.NAMES.key(named);
              return key != null ? key : DEFAULT_COLOR_KEY;
            }
          }
        }
      }
    }
    return DEFAULT_COLOR_KEY;
  }

  private static String normalizeTeamKey(String name) {
    return name != null ? name.trim().toLowerCase(Locale.ROOT) : null;
  }

  private UUID parseUuid(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(value.trim());
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  private TeamLoadResult loadTeamFromConfig(
      UUID teamId,
      String teamIdStr,
      String name,
      UUID leaderId,
      String prefix,
      String color,
      List<String> rawMembers,
      long deadline,
      Map<UUID, Long> deadlines) {
    Team team = new Team(teamId, name != null ? name.trim() : null, leaderId, prefix, color);
    List<UUID> memberIds = new ArrayList<>();
    boolean convertedMembers = false;
    for (String rawMember : rawMembers) {
      UUID memberId = parseUuid(rawMember);
      boolean convertedFromName = false;
      if (memberId == null) {
        if (rawMember == null || rawMember.isBlank()) {
          plugin
              .getLogger()
              .warning(
                  "Skipping member entry '"
                      + rawMember
                      + "' for team "
                      + teamId
                      + " because it could not be converted to UUID.");
          continue;
        }
        memberId =
            resolvePlayerUuid(
                rawMember, resolved -> handleDeferredMember(team, rawMember, resolved));
        convertedFromName = memberId != null;
      }
      if (memberId == null) {
        continue;
      }
      if (!memberIds.contains(memberId)) {
        memberIds.add(memberId);
      }
      if (convertedFromName) {
        convertedMembers = true;
      }
    }
    if (!memberIds.contains(leaderId)) {
      memberIds.add(0, leaderId);
      convertedMembers = true;
    } else if (!memberIds.isEmpty() && !leaderId.equals(memberIds.get(0))) {
      memberIds.remove(leaderId);
      memberIds.add(0, leaderId);
      convertedMembers = true;
    }
    if (memberIds.isEmpty()) {
      memberIds.add(leaderId);
      convertedMembers = true;
    }
    team.setMembers(memberIds);
    if (deadline > 0L) {
      deadlines.put(team.getId(), deadline);
    }
    addTeamInternal(team, false);
    for (UUID member : team.getMembers()) {
      playerTeams.put(member, team.getId());
    }
    if (convertedMembers) {
      markTeamDirty(team);
    }
    return new TeamLoadResult(team, convertedMembers);
  }

  private UUID resolvePlayerUuid(String value, java.util.function.Consumer<UUID> onResolved) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String trimmed = value.trim();
    String cacheKey = trimmed.toLowerCase(Locale.ROOT);
    UUID cached = resolvedProfileCache.get(cacheKey);
    if (cached != null) {
      return cached;
    }
    OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayerIfCached(trimmed);
    if (offlinePlayer != null && offlinePlayer.getUniqueId() != null) {
      UUID uuid = offlinePlayer.getUniqueId();
      resolvedProfileCache.put(cacheKey, uuid);
      return uuid;
    }
    CompletableFuture<UUID> future =
        pendingProfileLookups.computeIfAbsent(
            cacheKey,
            key ->
                startProfileLookup(trimmed)
                    .whenComplete(
                        (uuid, throwable) -> {
                          pendingProfileLookups.remove(cacheKey);
                          if (throwable != null) {
                            plugin
                                .getLogger()
                                .warning(
                                    "Failed to resolve profile for '"
                                        + trimmed
                                        + "': "
                                        + throwable.getMessage());
                          } else if (uuid != null) {
                            resolvedProfileCache.put(cacheKey, uuid);
                          }
                        }));
    future.whenComplete(
        (uuid, throwable) -> {
          UUID result = uuid;
          if (throwable != null) {
            result = null;
          }
          if (onResolved != null) {
            UUID finalResult = result;
            plugin.getServer().getScheduler().runTask(plugin, () -> onResolved.accept(finalResult));
          }
        });
    return null;
  }

  private CompletableFuture<UUID> startProfileLookup(String playerName) {
    CompletableFuture<UUID> result = new CompletableFuture<>();
    plugin
        .getServer()
        .getAsyncScheduler()
        .runNow(
            plugin,
            task -> {
              try {
                var profile = plugin.getServer().createProfile(playerName);
                if (profile.completeFromCache(true, true) && profile.getId() != null) {
                  result.complete(profile.getId());
                  return;
                }
                profile
                    .update()
                    .whenComplete(
                        (updated, throwable) -> {
                          if (throwable != null) {
                            result.completeExceptionally(throwable);
                            return;
                          }
                          UUID uuid = null;
                          if (updated != null && updated.getId() != null) {
                            uuid = updated.getId();
                          } else if (profile.getId() != null) {
                            uuid = profile.getId();
                          }
                          result.complete(uuid);
                        });
              } catch (Throwable throwable) {
                result.completeExceptionally(throwable);
              }
            });
    return result;
  }

  private void handlePendingLeaderResolution(PendingTeamLoad pending, UUID resolvedLeader) {
    if (resolvedLeader == null) {
      plugin
          .getLogger()
          .warning(
              "Skipping team entry "
                  + pending.teamId
                  + " because player name '"
                  + pending.leaderRaw
                  + "' could not be resolved.");
      pendingTeamLoads.remove(pending.teamId);
      return;
    }
    pendingTeamLoads.remove(pending.teamId);
    synchronized (saveLock) {
      TeamLoadResult result =
          loadTeamFromConfig(
              pending.teamId,
              pending.teamIdString,
              pending.name,
              resolvedLeader,
              pending.prefix,
              pending.color,
              pending.rawMembers,
              pending.deadline,
              deadlinesReference);
      if (result != null) {
        Team team = result.team();
        teamsConfig.set("teams." + pending.teamIdString + ".leader", resolvedLeader.toString());
        teamsConfig.set(
            "teams." + pending.teamIdString + ".members",
            team.getMembers().stream().map(UUID::toString).collect(Collectors.toList()));
        markTeamDirty(team);
      }
    }
  }

  private void handleDeferredMember(Team team, String rawValue, UUID resolvedUuid) {
    if (resolvedUuid == null) {
      plugin
          .getLogger()
          .warning(
              "Skipping member entry '"
                  + rawValue
                  + "' for team "
                  + team.getId()
                  + " because it could not be converted to UUID.");
      return;
    }
    synchronized (saveLock) {
      List<UUID> members = new ArrayList<>(team.getMembers());
      if (!members.contains(resolvedUuid)) {
        members.add(resolvedUuid);
        team.setMembers(members);
        playerTeams.put(resolvedUuid, team.getId());
        teamsConfig.set(
            "teams." + team.getId() + ".members",
            members.stream().map(UUID::toString).collect(Collectors.toList()));
        markTeamDirty(team);
      }
    }
  }

  private static final class PendingTeamLoad {
    private final UUID teamId;
    private final String teamIdString;
    private final String name;
    private final String leaderRaw;
    private final String prefix;
    private final String color;
    private final List<String> rawMembers;
    private final long deadline;

    private PendingTeamLoad(
        UUID teamId,
        String teamIdString,
        String name,
        String leaderRaw,
        String prefix,
        String color,
        List<String> rawMembers,
        long deadline) {
      this.teamId = teamId;
      this.teamIdString = teamIdString;
      this.name = name;
      this.leaderRaw = leaderRaw != null ? leaderRaw.trim() : null;
      this.prefix = prefix;
      this.color = color;
      this.rawMembers = new ArrayList<>(rawMembers);
      this.deadline = deadline;
    }
  }

  private record TeamLoadResult(Team team, boolean convertedFromName) {}
}

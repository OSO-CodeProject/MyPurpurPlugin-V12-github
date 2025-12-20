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
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.example.config.PluginConfig;
import org.example.model.PendingInvite;
import org.example.model.PendingRequest;
import org.example.model.Team;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Handles persistence of team data and keeps track of player memberships. */
public class TeamStorage {

  private final JavaPlugin plugin;
  private final PluginConfig pluginConfig;

  private final Map<UUID, Team> teams = new ConcurrentHashMap<>();
  private final Map<String, UUID> teamIdsByName = new ConcurrentHashMap<>();
  private final Map<UUID, UUID> playerTeams = new ConcurrentHashMap<>();
  private final Map<UUID, Team> playerTeamCache = new ConcurrentHashMap<>();
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
  private Collection<PendingInvite> pendingInvites = List.of();
  private Collection<PendingRequest> pendingRequests = List.of();
  private volatile boolean invitesDirty;
  private volatile boolean requestsDirty;

  public TeamStorage(@NotNull JavaPlugin plugin, @NotNull PluginConfig pluginConfig) {
    this.plugin = plugin;
    this.pluginConfig = pluginConfig;
  }

  /** Loads team information from teams.yml and fills provided deadlines map. */
  public void loadTeams(Map<UUID, Long> deadlines) {
    loadTeams(deadlines, null);
  }

  public void loadTeams(Map<UUID, Long> deadlines, @Nullable MembershipService membershipService) {
    synchronized (saveLock) {
      deadlinesReference = deadlines;
      teamsFile = new File(plugin.getDataFolder(), "teams.yml");
      if (!teamsFile.exists()) {
        try {
          File parent = teamsFile.getParentFile();
          if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin
                .getLogger()
                .severe("Failed to create directory for teams.yml at " + parent.getAbsolutePath());
            return;
          }
          teamsFile.createNewFile();
        } catch (IOException e) {
          plugin.getLogger().severe("Failed to create teams.yml: " + e.getMessage());
        }
      }
      teamsConfig = YamlConfiguration.loadConfiguration(teamsFile);
      teams.clear();
      teamIdsByName.clear();
      playerTeams.clear();
      playerTeamCache.clear();
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
      PendingData<PendingInvite> inviteData = loadPendingInvites();
      PendingData<PendingRequest> requestData = loadPendingRequests();
      pendingInvites = List.copyOf(inviteData.values());
      pendingRequests = List.copyOf(requestData.values());
      if (membershipService != null) {
        membershipService.loadPendingInvites(pendingInvites);
        membershipService.loadPendingRequests(pendingRequests);
      }
      dirtyTeams.clear();
      deadlinesDirty = false;
      invitesDirty = false;
      requestsDirty = false;
      if (legacyDataUpdated || inviteData.updated() || requestData.updated()) {
        saveTeams(deadlines);
        invitesDirty = false;
        requestsDirty = false;
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
      teamsConfig.set("invites", null);
      for (PendingInvite invite : pendingInvites) {
        String base = "invites." + invite.getTeamId() + "." + invite.getTargetPlayerId();
        teamsConfig.set(base + ".teamName", invite.getTeamName());
        teamsConfig.set(base + ".inviterId", invite.getInviterId().toString());
        teamsConfig.set(base + ".inviterName", invite.getInviterName());
        teamsConfig.set(base + ".targetName", invite.getTargetName());
        teamsConfig.set(base + ".createdAt", invite.getCreatedAt());
        if (invite.getExpiresAt() != null) {
          teamsConfig.set(base + ".expiresAt", invite.getExpiresAt());
        } else {
          teamsConfig.set(base + ".expiresAt", null);
        }
      }
      teamsConfig.set("requests", null);
      for (PendingRequest request : pendingRequests) {
        String base = "requests." + request.getTeamId() + "." + request.getPlayerId();
        teamsConfig.set(base + ".teamName", request.getTeamName());
        teamsConfig.set(base + ".playerName", request.getPlayerName());
        teamsConfig.set(base + ".createdAt", request.getCreatedAt());
        if (request.getExpiresAt() != null) {
          teamsConfig.set(base + ".expiresAt", request.getExpiresAt());
        } else {
          teamsConfig.set(base + ".expiresAt", null);
        }
      }
      try {
        teamsConfig.save(teamsFile);
      } catch (IOException e) {
        plugin.getLogger().warning("Failed to save teams.yml: " + e.getMessage());
      }
    }
  }

  public void saveInvites(@NotNull Collection<PendingInvite> invites) {
    synchronized (saveLock) {
      pendingInvites = filterActiveInvites(invites);
      invitesDirty = true;
    }
    scheduleImmediateSaveIfDisabled();
  }

  public void saveRequests(@NotNull Collection<PendingRequest> requests) {
    synchronized (saveLock) {
      pendingRequests = filterActiveRequests(requests);
      requestsDirty = true;
    }
    scheduleImmediateSaveIfDisabled();
  }

  public @NotNull Map<UUID, Team> getTeams() {
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
      for (UUID memberId : team.getMembers()) {
        clearPlayerTeam(memberId);
      }
      UUID leaderId = team.getLeaderId();
      if (leaderId != null) {
        clearPlayerTeam(leaderId);
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

  public @NotNull Map<UUID, UUID> getPlayerTeams() {
    return playerTeams;
  }

  public @Nullable Team getTeamByName(@Nullable String teamName) {
    String normalizedName = normalizeTeamKey(teamName);
    if (normalizedName == null) {
      return null;
    }
    UUID teamId = teamIdsByName.get(normalizedName);
    return teamId != null ? teams.get(teamId) : null;
  }

  public @Nullable UUID getTeamIdByName(@Nullable String teamName) {
    String normalizedName = normalizeTeamKey(teamName);
    return normalizedName != null ? teamIdsByName.get(normalizedName) : null;
  }

  public @Nullable String getPlayerTeam(@NotNull Player player) {
    Team team = playerTeamCache.get(player.getUniqueId());
    return team != null ? team.getName() : null;
  }

  public void assignPlayerToTeam(@NotNull UUID playerId, @NotNull Team team) {
    cachePlayerTeam(playerId, team);
  }

  public void clearPlayerTeam(@NotNull UUID playerId) {
    playerTeams.remove(playerId);
    playerTeamCache.remove(playerId);
  }

  private void cachePlayerTeam(@NotNull UUID playerId, @NotNull Team team) {
    playerTeams.put(playerId, team.getId());
    playerTeamCache.put(playerId, team);
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

  public @NotNull String getTeamPrefix(@Nullable String teamName) {
    Team team = getTeamByName(teamName);
    return team != null ? team.getPrefix() : "";
  }

  public @NotNull NamedTextColor getTeamColor(@Nullable String teamName) {
    Team team = getTeamByName(teamName);
    return team != null ? team.getColor() : NamedTextColor.WHITE;
  }

  public @Nullable UUID getTeamLeaderId(@Nullable String teamName) {
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
    if (!deadlinesDirty && dirtyTeams.isEmpty() && !invitesDirty && !requestsDirty) {
      return;
    }
    synchronized (saveLock) {
      if (!deadlinesDirty && dirtyTeams.isEmpty() && !invitesDirty && !requestsDirty) {
        return;
      }
      saveTeams(deadlines != null ? deadlines : deadlinesReference);
      dirtyTeams.clear();
      deadlinesDirty = false;
      invitesDirty = false;
      requestsDirty = false;
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
    team.setMembers(rawMembers);
    List<UUID> memberIds = new ArrayList<>(team.getMembers());
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
    } else if (!memberIds.isEmpty() && !leaderId.equals(memberIds.getFirst())) {
      memberIds.remove(leaderId);
      memberIds.add(0, leaderId);
      convertedMembers = true;
    }
    if (memberIds.isEmpty()) {
      memberIds.add(leaderId);
      convertedMembers = true;
    }
    team.setMembersByUuid(memberIds);
    if (deadline > 0L) {
      deadlines.put(team.getId(), deadline);
    }
    addTeamInternal(team, false);
    for (UUID member : team.getMembers()) {
      cachePlayerTeam(member, team);
    }
    if (convertedMembers) {
      markTeamDirty(team);
    }
    return new TeamLoadResult(team, convertedMembers);
  }

  private PendingData<PendingInvite> loadPendingInvites() {
    if (teamsConfig == null) {
      return new PendingData<>(List.of(), false);
    }
    ConfigurationSection invitesSection = teamsConfig.getConfigurationSection("invites");
    if (invitesSection == null) {
      return new PendingData<>(List.of(), false);
    }
    List<PendingInvite> invites = new ArrayList<>();
    boolean updated = false;
    for (String teamIdKey : invitesSection.getKeys(false)) {
      UUID teamId = parseUuid(teamIdKey);
      if (teamId == null) {
        plugin
            .getLogger()
            .warning("Skipping invite section with invalid team id '" + teamIdKey + "'.");
        updated = true;
        continue;
      }
      ConfigurationSection teamInvites = invitesSection.getConfigurationSection(teamIdKey);
      if (teamInvites == null) {
        continue;
      }
      for (String playerIdKey : teamInvites.getKeys(false)) {
        UUID playerId = parseUuid(playerIdKey);
        if (playerId == null) {
          plugin
              .getLogger()
              .warning(
                  "Skipping invite entry for team "
                      + teamId
                      + " with invalid player id '"
                      + playerIdKey
                      + "'.");
          updated = true;
          continue;
        }
        String basePath = "invites." + teamIdKey + "." + playerIdKey;
        String teamName = teamsConfig.getString(basePath + ".teamName");
        String inviterIdRaw = teamsConfig.getString(basePath + ".inviterId");
        UUID inviterId = parseUuid(inviterIdRaw);
        String inviterName = teamsConfig.getString(basePath + ".inviterName");
        String targetName = teamsConfig.getString(basePath + ".targetName");
        long createdAt = teamsConfig.getLong(basePath + ".createdAt", 0L);
        Long expiresAt = teamsConfig.isSet(basePath + ".expiresAt")
            ? teamsConfig.getLong(basePath + ".expiresAt")
            : null;
        if (teamName == null
            || inviterId == null
            || inviterName == null
            || targetName == null
            || createdAt <= 0L) {
          plugin
              .getLogger()
              .warning(
                  "Skipping invite entry for team "
                      + teamId
                      + " because it lacks required fields.");
          updated = true;
          continue;
        }
        PendingInvite invite =
            PendingInvite.restored(
                teamId, playerId, inviterId, teamName, inviterName, targetName, createdAt, expiresAt);
        if (invite.isExpired()) {
          updated = true;
          continue;
        }
        invites.add(invite);
      }
    }
    return new PendingData<>(invites, updated);
  }

  private PendingData<PendingRequest> loadPendingRequests() {
    if (teamsConfig == null) {
      return new PendingData<>(List.of(), false);
    }
    ConfigurationSection requestsSection = teamsConfig.getConfigurationSection("requests");
    if (requestsSection == null) {
      return new PendingData<>(List.of(), false);
    }
    List<PendingRequest> requests = new ArrayList<>();
    boolean updated = false;
    for (String teamIdKey : requestsSection.getKeys(false)) {
      UUID teamId = parseUuid(teamIdKey);
      if (teamId == null) {
        plugin
            .getLogger()
            .warning("Skipping requests section with invalid team id '" + teamIdKey + "'.");
        updated = true;
        continue;
      }
      ConfigurationSection teamRequests = requestsSection.getConfigurationSection(teamIdKey);
      if (teamRequests == null) {
        continue;
      }
      for (String playerIdKey : teamRequests.getKeys(false)) {
        UUID playerId = parseUuid(playerIdKey);
        if (playerId == null) {
          plugin
              .getLogger()
              .warning(
                  "Skipping request entry for team "
                      + teamId
                      + " with invalid player id '"
                      + playerIdKey
                      + "'.");
          updated = true;
          continue;
        }
        String basePath = "requests." + teamIdKey + "." + playerIdKey;
        String teamName = teamsConfig.getString(basePath + ".teamName");
        String playerName = teamsConfig.getString(basePath + ".playerName");
        long createdAt = teamsConfig.getLong(basePath + ".createdAt", 0L);
        Long expiresAt = teamsConfig.isSet(basePath + ".expiresAt")
            ? teamsConfig.getLong(basePath + ".expiresAt")
            : null;
        if (teamName == null || playerName == null || createdAt <= 0L) {
          plugin
              .getLogger()
              .warning(
                  "Skipping request entry for team "
                      + teamId
                      + " because it lacks required fields.");
          updated = true;
          continue;
        }
        PendingRequest request =
            PendingRequest.restored(teamId, playerId, teamName, playerName, createdAt, expiresAt);
        if (request.isExpired()) {
          updated = true;
          continue;
        }
        requests.add(request);
      }
    }
    return new PendingData<>(requests, updated);
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
        team.setMembersByUuid(members);
        cachePlayerTeam(resolvedUuid, team);
        teamsConfig.set(
            "teams." + team.getId() + ".members",
            members.stream().map(UUID::toString).collect(Collectors.toList()));
        markTeamDirty(team);
      }
    }
  }

  private List<PendingInvite> filterActiveInvites(Collection<PendingInvite> invites) {
    if (invites.isEmpty()) {
      return List.of();
    }
    List<PendingInvite> active = new ArrayList<>();
    for (PendingInvite invite : invites) {
      if (invite != null && !invite.isExpired()) {
        active.add(invite);
      }
    }
    return active.isEmpty() ? List.of() : List.copyOf(active);
  }

  private List<PendingRequest> filterActiveRequests(Collection<PendingRequest> requests) {
    if (requests.isEmpty()) {
      return List.of();
    }
    List<PendingRequest> active = new ArrayList<>();
    for (PendingRequest request : requests) {
      if (request != null && !request.isExpired()) {
        active.add(request);
      }
    }
    return active.isEmpty() ? List.of() : List.copyOf(active);
  }

  private record PendingData<T>(List<T> values, boolean updated) {}

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

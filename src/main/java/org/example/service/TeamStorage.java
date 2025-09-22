package org.example.service;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
                .warning(
                    "Skipping team entry " + teamId + " because it does not define a name.");
            continue;
          }
          String leaderRaw = teamsConfig.getString("teams." + teamIdStr + ".leader", "");
          UUID leaderId = parseUuid(leaderRaw);
          boolean leaderFromName = false;
          if (leaderId == null) {
            leaderId = resolvePlayerUuid(leaderRaw);
            leaderFromName = leaderId != null;
          }
          if (leaderId == null) {
            plugin
                .getLogger()
                .warning(
                    "Skipping team entry " + teamId + " because it does not define a leader.");
            continue;
          }
          String prefix = teamsConfig.getString("teams." + teamIdStr + ".prefix", "");
          String color =
              normalizeColorKey(teamsConfig.getString("teams." + teamIdStr + ".color", "WHITE"));
          List<String> rawMembers = teamsConfig.getStringList("teams." + teamIdStr + ".members");
          List<UUID> memberIds = new ArrayList<>();
          boolean convertedMembers = false;
          for (String rawMember : rawMembers) {
            UUID memberId = parseUuid(rawMember);
            boolean convertedFromName = false;
            if (memberId == null) {
              memberId = resolvePlayerUuid(rawMember);
              convertedFromName = memberId != null;
            }
            if (memberId == null) {
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
          Team team = new Team(teamId, name != null ? name.trim() : null, leaderId, prefix, color);
          team.setMembers(memberIds);
          long deadline = teamsConfig.getLong("teams." + teamIdStr + ".deadline", 0L);
          if (deadline > 0L) {
            deadlines.put(team.getId(), deadline);
          }
          addTeamInternal(team, false);
          for (UUID member : team.getMembers()) {
            playerTeams.put(member, team.getId());
          }
          legacyDataUpdated = legacyDataUpdated || leaderFromName || convertedMembers;
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

  private UUID resolvePlayerUuid(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String trimmed = value.trim();
    OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayerIfCached(trimmed);
    if (offlinePlayer == null) {
      plugin
          .getLogger()
          .warning(
              "Cannot resolve player name '"
                  + trimmed
                  + "' because no cached profile is available.");
      return null;
    }
    return offlinePlayer.getUniqueId();
  }
}

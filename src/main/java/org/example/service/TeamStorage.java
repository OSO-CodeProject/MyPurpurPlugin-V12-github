package org.example.service;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.example.config.PluginConfig;
import org.example.database.DatabaseManager;
import org.example.database.TeamRepository;
import org.example.model.PendingInvite;
import org.example.model.PendingRequest;
import org.example.model.Team;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Handles persistence of team data via SQLite and keeps track of player memberships in memory. */
public class TeamStorage {

  private final JavaPlugin plugin;
  private final PluginConfig pluginConfig;

  private final Map<UUID, Team> teams = new ConcurrentHashMap<>();
  private final Map<String, UUID> teamIdsByName = new ConcurrentHashMap<>();
  private final Map<UUID, UUID> playerTeams = new ConcurrentHashMap<>();
  private final Map<UUID, Team> playerTeamCache = new ConcurrentHashMap<>();

  // State File for Pending Invites & Requests
  private FileConfiguration stateConfig;
  private File stateFile;

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

  private final DatabaseManager dbManager;
  private final TeamRepository repository;

  public TeamStorage(@NotNull JavaPlugin plugin, @NotNull PluginConfig pluginConfig) {
    this.plugin = plugin;
    this.pluginConfig = pluginConfig;
    this.dbManager = new DatabaseManager(plugin);
    this.dbManager.connect();
    this.repository = new TeamRepository(this.dbManager, plugin);
  }

  /** Loads team information from SQLite and fills provided deadlines map. */
  public void loadTeams(Map<UUID, Long> deadlines) {
    loadTeams(deadlines, null);
  }

  public void loadTeams(Map<UUID, Long> deadlines, @Nullable MembershipService membershipService) {
    synchronized (saveLock) {
      deadlinesReference = deadlines;
      deadlines.clear();

      // Load Invites and Requests from a separate state.yml file since they are temporary
      stateFile = new File(plugin.getDataFolder(), "state.yml");
      if (!stateFile.exists()) {
        try {
          stateFile.createNewFile();
        } catch (IOException e) {
          plugin
              .getLogger()
              .warning("Could not create state.yml for tracking invites: " + e.getMessage());
        }
      }
      stateConfig = YamlConfiguration.loadConfiguration(stateFile);

      teams.clear();
      teamIdsByName.clear();
      playerTeams.clear();
      playerTeamCache.clear();

      List<Team> loadedTeams = repository.loadAllTeams(deadlines);
      for (Team team : loadedTeams) {
        addTeamInternal(team, false);
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
    }
  }

  /** Saves dirty teams to SQLite asynchronously, and volatile data to state.yml */
  public void saveTeams(Map<UUID, Long> deadlines) {
    synchronized (saveLock) {
      // Prepare list of dirty teams to avoid locking for too long
      List<Team> toSave = new ArrayList<>();
      List<Long> deadlinesToSave = new ArrayList<>();
      for (UUID teamId : dirtyTeams) {
        Team team = teams.get(teamId);
        if (team != null) {
          toSave.add(team);
          deadlinesToSave.add(deadlines.getOrDefault(teamId, 0L));
        }
      }
      dirtyTeams.clear();

      // Save to SQLite asynchronously
      if (!toSave.isEmpty()) {
        plugin
            .getServer()
            .getScheduler()
            .runTaskAsynchronously(
                plugin,
                () -> {
                  for (int i = 0; i < toSave.size(); i++) {
                    repository.saveTeam(toSave.get(i), deadlinesToSave.get(i));
                  }
                });
      }

      if (invitesDirty || requestsDirty) {
        saveStateSync();
        invitesDirty = false;
        requestsDirty = false;
      }
    }
  }

  private void saveStateSync() {
    if (stateConfig == null || stateFile == null) return;
    stateConfig.set("invites", null);
    for (PendingInvite invite : pendingInvites) {
      String base = "invites." + invite.getTeamId() + "." + invite.getTargetPlayerId();
      stateConfig.set(base + ".teamName", invite.getTeamName());
      stateConfig.set(base + ".inviterId", invite.getInviterId().toString());
      stateConfig.set(base + ".inviterName", invite.getInviterName());
      stateConfig.set(base + ".targetName", invite.getTargetName());
      stateConfig.set(base + ".createdAt", invite.getCreatedAt());
      stateConfig.set(base + ".expiresAt", invite.getExpiresAt());
    }
    stateConfig.set("requests", null);
    for (PendingRequest request : pendingRequests) {
      String base = "requests." + request.getTeamId() + "." + request.getPlayerId();
      stateConfig.set(base + ".teamName", request.getTeamName());
      stateConfig.set(base + ".playerName", request.getPlayerName());
      stateConfig.set(base + ".createdAt", request.getCreatedAt());
      stateConfig.set(base + ".expiresAt", request.getExpiresAt());
    }
    try {
      stateConfig.save(stateFile);
    } catch (IOException e) {
      plugin.getLogger().warning("Failed to save state.yml: " + e.getMessage());
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
    for (UUID memberId : team.getMembers()) {
      playerTeams.put(memberId, team.getId());
      playerTeamCache.put(memberId, team);
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
      dirtyTeams.remove(team.getId());

      plugin
          .getServer()
          .getScheduler()
          .runTaskAsynchronously(
              plugin,
              () -> {
                repository.deleteTeam(team.getId());
              });
      scheduleImmediateSaveIfDisabled();
    }
  }

  public void markTeamDirty(@NotNull Team team) {
    synchronized (saveLock) {
      dirtyTeams.add(team.getId());
    }
    scheduleImmediateSaveIfDisabled();
  }

  public void markDeadlinesDirty() {
    deadlinesDirty = true;
    scheduleImmediateSaveIfDisabled();
  }

  private void scheduleImmediateSaveIfDisabled() {
    if (!autoSaveEnabled) {
      plugin
          .getServer()
          .getScheduler()
          .runTaskAsynchronously(plugin, () -> saveTeams(deadlinesReference));
    }
  }

  public @Nullable Team getTeam(@NotNull UUID teamId) {
    return teams.get(teamId);
  }

  public @Nullable Team getTeamByName(@NotNull String teamName) {
    String normalized = normalizeTeamKey(teamName);
    if (normalized == null) return null;
    UUID id = teamIdsByName.get(normalized);
    return id != null ? teams.get(id) : null;
  }

  public void setPlayerTeam(@NotNull UUID playerId, @NotNull UUID teamId) {
    Team team = teams.get(teamId);
    if (team != null) {
      playerTeams.put(playerId, teamId);
      playerTeamCache.put(playerId, team);
    }
  }

  public void assignPlayerToTeam(@NotNull UUID playerId, @NotNull Team team) {
    playerTeams.put(playerId, team.getId());
    playerTeamCache.put(playerId, team);
  }

  public void updateTeamName(@NotNull Team team, @NotNull String newName) {
    synchronized (saveLock) {
      String oldNormalized = normalizeTeamKey(team.getName());
      if (oldNormalized != null) {
        teamIdsByName.remove(oldNormalized);
      }
      team.setName(newName);
      String newNormalized = normalizeTeamKey(newName);
      if (newNormalized != null) {
        teamIdsByName.put(newNormalized, team.getId());
      }
      markTeamDirty(team);
    }
  }

  public void clearPlayerTeam(@NotNull UUID playerId) {
    playerTeams.remove(playerId);
    playerTeamCache.remove(playerId);
  }

  public String getPlayerTeam(@NotNull Player player) {
    Team team = playerTeamCache.get(player.getUniqueId());
    return team != null ? team.getName() : null;
  }

  public UUID getPlayerTeamId(@NotNull UUID playerId) {
    return playerTeams.get(playerId);
  }

  public @Nullable Team getPlayerTeamObj(@NotNull UUID playerId) {
    return playerTeamCache.get(playerId);
  }

  public @NotNull Map<UUID, UUID> getPlayerTeams() {
    return playerTeams;
  }

  public @Nullable UUID getTeamIdByName(@NotNull String teamName) {
    String normalized = normalizeTeamKey(teamName);
    return normalized != null ? teamIdsByName.get(normalized) : null;
  }

  public @Nullable UUID getTeamLeaderId(@NotNull String teamName) {
    Team team = getTeamByName(teamName);
    return team != null ? team.getLeaderId() : null;
  }

  public @NotNull List<UUID> getTeamMembers(String teamName) {
    Team team = getTeamByName(teamName);
    return team != null ? team.getMembers() : Collections.emptyList();
  }

  public @NotNull List<String> getTeamNames() {
    return teams.values().stream().map(Team::getName).collect(Collectors.toList());
  }

  public String getTeamPrefix(String teamName) {
    Team team = getTeamByName(teamName);
    return team != null ? team.getPrefix() : "";
  }

  public @NotNull NamedTextColor getTeamColor(String teamName) {
    Team team = getTeamByName(teamName);
    return team != null ? team.getColor() : NamedTextColor.WHITE;
  }

  public void startAutoSave(long intervalSeconds, Map<UUID, Long> deadlines) {
    this.deadlinesReference = deadlines;
    if (autoSaveTask != null) {
      autoSaveTask.cancel();
    }
    autoSaveEnabled = intervalSeconds > 0;
    if (autoSaveEnabled) {
      autoSaveTask =
          plugin
              .getServer()
              .getScheduler()
              .runTaskTimerAsynchronously(
                  plugin,
                  () -> {
                    if (!dirtyTeams.isEmpty() || deadlinesDirty || invitesDirty || requestsDirty) {
                      saveTeams(deadlinesReference);
                      deadlinesDirty = false;
                    }
                  },
                  intervalSeconds * 20L,
                  intervalSeconds * 20L);
    }
  }

  public void stopAutoSave() {
    if (autoSaveTask != null) {
      autoSaveTask.cancel();
    }
    autoSaveEnabled = false;
  }

  public void flushNow() {
    saveTeams(deadlinesReference);
  }

  public void onDisable() {
    stopAutoSave();
    flushNow(); // Final save

    if (dbManager != null) {
      dbManager.disconnect();
    }
  }

  private String normalizeTeamKey(String name) {
    return name == null ? null : name.toLowerCase(Locale.ROOT);
  }

  private List<PendingInvite> filterActiveInvites(Collection<PendingInvite> invites) {
    long now = System.currentTimeMillis();
    return invites.stream()
        .filter(i -> i.getExpiresAt() == null || i.getExpiresAt() > now)
        .collect(Collectors.toList());
  }

  private List<PendingRequest> filterActiveRequests(Collection<PendingRequest> requests) {
    long now = System.currentTimeMillis();
    return requests.stream()
        .filter(r -> r.getExpiresAt() == null || r.getExpiresAt() > now)
        .collect(Collectors.toList());
  }

  private PendingData<PendingInvite> loadPendingInvites() {
    List<PendingInvite> result = new ArrayList<>();
    if (stateConfig == null || !stateConfig.contains("invites")) {
      return new PendingData<>(result, false);
    }
    boolean updated = false;
    long now = System.currentTimeMillis();
    var section = stateConfig.getConfigurationSection("invites");
    if (section != null) {
      for (String teamIdStr : section.getKeys(false)) {
        UUID teamId;
        try {
          teamId = UUID.fromString(teamIdStr);
        } catch (IllegalArgumentException e) {
          continue;
        }
        var targetSection = stateConfig.getConfigurationSection("invites." + teamIdStr);
        if (targetSection != null) {
          for (String targetIdStr : targetSection.getKeys(false)) {
            UUID targetId;
            try {
              targetId = UUID.fromString(targetIdStr);
            } catch (IllegalArgumentException e) {
              continue;
            }
            String base = "invites." + teamIdStr + "." + targetIdStr;
            String teamName = stateConfig.getString(base + ".teamName", "");
            UUID inviterId;
            try {
              inviterId = UUID.fromString(stateConfig.getString(base + ".inviterId", ""));
            } catch (IllegalArgumentException e) {
              continue;
            }
            String inviterName = stateConfig.getString(base + ".inviterName", "");
            String targetName = stateConfig.getString(base + ".targetName", "");
            long createdAt = stateConfig.getLong(base + ".createdAt", now);
            Long expiresAt =
                stateConfig.contains(base + ".expiresAt")
                    ? stateConfig.getLong(base + ".expiresAt")
                    : null;
            if (expiresAt != null && expiresAt <= now) {
              updated = true;
              continue;
            }
            result.add(
                PendingInvite.restored(
                    teamId,
                    targetId,
                    inviterId,
                    teamName,
                    inviterName,
                    targetName,
                    createdAt,
                    expiresAt));
          }
        }
      }
    }
    return new PendingData<>(result, updated);
  }

  private PendingData<PendingRequest> loadPendingRequests() {
    List<PendingRequest> result = new ArrayList<>();
    if (stateConfig == null || !stateConfig.contains("requests")) {
      return new PendingData<>(result, false);
    }
    boolean updated = false;
    long now = System.currentTimeMillis();
    var section = stateConfig.getConfigurationSection("requests");
    if (section != null) {
      for (String teamIdStr : section.getKeys(false)) {
        UUID teamId;
        try {
          teamId = UUID.fromString(teamIdStr);
        } catch (IllegalArgumentException e) {
          continue;
        }
        var playerSection = stateConfig.getConfigurationSection("requests." + teamIdStr);
        if (playerSection != null) {
          for (String playerIdStr : playerSection.getKeys(false)) {
            UUID playerId;
            try {
              playerId = UUID.fromString(playerIdStr);
            } catch (IllegalArgumentException e) {
              continue;
            }
            String base = "requests." + teamIdStr + "." + playerIdStr;
            String teamName = stateConfig.getString(base + ".teamName", "");
            String playerName = stateConfig.getString(base + ".playerName", "");
            long createdAt = stateConfig.getLong(base + ".createdAt", now);
            Long expiresAt =
                stateConfig.contains(base + ".expiresAt")
                    ? stateConfig.getLong(base + ".expiresAt")
                    : null;
            if (expiresAt != null && expiresAt <= now) {
              updated = true;
              continue;
            }
            result.add(
                PendingRequest.restored(
                    teamId, playerId, teamName, playerName, createdAt, expiresAt));
          }
        }
      }
    }
    return new PendingData<>(result, updated);
  }

  private record PendingData<T>(List<T> values, boolean updated) {}
}

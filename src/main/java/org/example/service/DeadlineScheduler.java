package org.example.service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.example.config.PluginConfig;
import org.example.listener.TeamChatListener;
import org.example.model.Team;
import org.jetbrains.annotations.NotNull;

/** Handles deadline checking and enforcement of team sizes. */
public class DeadlineScheduler {

  private final JavaPlugin plugin;
  private final PluginConfig pluginConfig;
  private final TeamStorage storage;
  private final Map<UUID, Long> deadlines = new ConcurrentHashMap<>();
  private BukkitTask task;

  public DeadlineScheduler(
      @NotNull JavaPlugin plugin,
      @NotNull PluginConfig pluginConfig,
      @NotNull TeamStorage storage) {
    this.plugin = plugin;
    this.pluginConfig = pluginConfig;
    this.storage = storage;
  }

  public Map<UUID, Long> getDeadlines() {
    return deadlines;
  }

  public synchronized void start() {
    long seconds = pluginConfig.getDeadlineNotifyPeriodSeconds();
    long period = 20L * Math.max(1, seconds);
    if (task != null) {
      task.cancel();
    }
    task =
        plugin
            .getServer()
            .getScheduler()
            .runTaskTimer(plugin, this::checkDeadlines, period, period);
  }

  public synchronized void stop() {
    if (task != null) {
      task.cancel();
      task = null;
    }
  }

  public void enforceTeamSizes() {
    int max = pluginConfig.getMaxMembers();
    boolean changed = false;
    if (max <= 0) {
      if (!deadlines.isEmpty()) {
        deadlines.clear();
        changed = true;
      }
      if (changed) {
        storage.markDeadlinesDirty();
      }
      return;
    }
    for (Map.Entry<UUID, Team> entry : storage.getTeams().entrySet()) {
      Team team = entry.getValue();
      int size = team.getMembers().size();
      if (size > max) {
        Long previous =
            deadlines.putIfAbsent(
                entry.getKey(),
                System.currentTimeMillis() + pluginConfig.getGracePeriodMinutes() * 60L * 1000L);
        if (previous == null) {
          changed = true;
        }
      } else {
        if (deadlines.remove(entry.getKey()) != null) {
          changed = true;
        }
      }
    }
    if (changed) {
      storage.markDeadlinesDirty();
    }
  }

  public void checkDeadlines() {
    int max = pluginConfig.getMaxMembers();
    boolean changed = false;
    if (max <= 0) {
      if (!deadlines.isEmpty()) {
        deadlines.clear();
        changed = true;
      }
      if (changed) {
        storage.markDeadlinesDirty();
      }
      return;
    }
    long now = System.currentTimeMillis();
    Iterator<Map.Entry<UUID, Long>> it = deadlines.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<UUID, Long> entry = it.next();
      if (entry.getValue() <= now) {
        removeExtraPlayers(entry.getKey(), max);
        it.remove();
        changed = true;
      }
    }
    if (changed) {
      storage.markDeadlinesDirty();
    }
  }

  private void removeExtraPlayers(UUID teamId, int max) {
    Team team = storage.getTeams().get(teamId);
    if (team == null) return;
    List<String> members = new ArrayList<>(team.getMembers());
    boolean changed = false;
    while (members.size() > max) {
      String removed = members.remove(members.size() - 1);
      team.removeMember(removed);
      storage.getPlayerTeams().remove(removed);
      changed = true;
      Player player = plugin.getServer().getPlayer(removed);
      if (player != null) {
        plugin
            .getServer()
            .getPluginManager()
            .callEvent(new TeamChatListener.PlayerPrefixUpdateEvent(player, null));
      }
    }
    if (changed) {
      storage.markTeamDirty(team);
    }
  }

  public Long getTeamDeadline(String teamName) {
    UUID id = storage.getTeamIdByName(teamName);
    return id != null ? deadlines.get(id) : null;
  }
}

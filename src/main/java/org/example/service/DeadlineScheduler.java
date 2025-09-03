package org.example.service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
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

  public void start() {
    long seconds = pluginConfig.getDeadlineNotifyPeriodSeconds();
    long period = 20L * Math.max(1, seconds);
    plugin.getServer().getScheduler().runTaskTimer(plugin, this::checkDeadlines, period, period);
  }

  public void enforceTeamSizes() {
    int max = pluginConfig.getMaxMembers();
    if (max <= 0) {
      deadlines.clear();
      return;
    }
    for (Map.Entry<UUID, Team> entry : storage.getTeams().entrySet()) {
      Team team = entry.getValue();
      int size = team.getMembers().size();
      if (size > max) {
        deadlines.putIfAbsent(
            entry.getKey(),
            System.currentTimeMillis() + pluginConfig.getGracePeriodMinutes() * 60L * 1000L);
      } else {
        deadlines.remove(entry.getKey());
      }
    }
    storage.saveTeams(deadlines);
  }

  public void checkDeadlines() {
    int max = pluginConfig.getMaxMembers();
    if (max <= 0) {
      deadlines.clear();
      return;
    }
    long now = System.currentTimeMillis();
    Iterator<Map.Entry<UUID, Long>> it = deadlines.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<UUID, Long> entry = it.next();
      if (entry.getValue() <= now) {
        removeExtraPlayers(entry.getKey(), max);
        it.remove();
      }
    }
    storage.saveTeams(deadlines);
  }

  private void removeExtraPlayers(UUID teamId, int max) {
    Team team = storage.getTeams().get(teamId);
    if (team == null) return;
    List<String> members = new ArrayList<>(team.getMembers());
    while (members.size() > max) {
      String removed = members.remove(members.size() - 1);
      team.removeMember(removed);
      storage.getPlayerTeams().remove(removed);
      Player player = plugin.getServer().getPlayer(removed);
      if (player != null) {
        plugin
            .getServer()
            .getPluginManager()
            .callEvent(new TeamChatListener.PlayerPrefixUpdateEvent(player, null));
      }
    }
  }

  public Long getTeamDeadline(String teamName) {
    UUID id = storage.getTeamIdByName(teamName);
    return id != null ? deadlines.get(id) : null;
  }
}

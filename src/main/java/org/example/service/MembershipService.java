package org.example.service;

import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.example.config.PluginConfig;
import org.example.model.Team;
import org.example.util.TeamMessageUtils;
import org.example.util.TeamUtils;
import org.jetbrains.annotations.NotNull;

/** Contains operations that modify teams and player memberships. */
public class MembershipService {

  private final JavaPlugin plugin;
  private final PluginConfig pluginConfig;
  private final TeamStorage storage;
  private final DeadlineScheduler scheduler;

  public MembershipService(
      @NotNull JavaPlugin plugin,
      @NotNull PluginConfig pluginConfig,
      @NotNull TeamStorage storage,
      @NotNull DeadlineScheduler scheduler) {
    this.plugin = plugin;
    this.pluginConfig = pluginConfig;
    this.storage = storage;
    this.scheduler = scheduler;
  }

  public void createTeam(String teamName, String prefix, String color, @NotNull Player leader) {
    if (storage.getPlayerTeam(leader) != null || storage.getTeamByName(teamName) != null) {
      TeamMessageUtils.sendTeamMessage(leader, TeamMessageUtils.teamAlreadyExistsMessage(teamName));
      return;
    }
    NamedTextColor teamColor = NamedTextColor.NAMES.value(color.toLowerCase());
    if (teamColor == null) {
      TeamMessageUtils.sendTeamMessage(
          leader, Component.text("❌ Неверный цвет команды", NamedTextColor.RED));
      return;
    }
    if (TeamUtils.isTeamNameLengthInvalid(teamName, pluginConfig, leader)
        || TeamUtils.isPrefixLengthInvalid(prefix, pluginConfig, leader)) {
      return;
    }
    Team team = new Team(teamName, leader.getName(), prefix, color);
    storage.getTeams().put(team.getId(), team);
    storage.getPlayerTeams().put(leader.getName(), team.getId());
    storage.saveTeams(scheduler.getDeadlines());
    TeamMessageUtils.sendTeamMessage(
        leader, Component.text("✅ Команда создана", NamedTextColor.GREEN));
    scheduler.enforceTeamSizes();
  }

  public void addPlayerToTeam(String teamName, @NotNull Player player) {
    Team team = storage.getTeamByName(teamName);
    if (team == null) {
      TeamMessageUtils.sendTeamMessage(player, TeamMessageUtils.teamDoesNotExistMessage(teamName));
      return;
    }
    if (storage.getPlayerTeam(player) != null) {
      TeamMessageUtils.sendTeamMessage(
          player, Component.text("❌ Вы уже состоите в команде", NamedTextColor.RED));
      return;
    }
    int max = pluginConfig.getMaxMembers();
    if (max > 0 && team.getMembers().size() >= max) {
      TeamMessageUtils.sendTeamMessage(
          player, Component.text("❌ Команда полная", NamedTextColor.RED));
      return;
    }
    team.addMember(player.getName());
    storage.getPlayerTeams().put(player.getName(), team.getId());
    storage.saveTeams(scheduler.getDeadlines());
    TeamMessageUtils.sendTeamMessage(
        player, Component.text("✅ Вы вступили в команду", NamedTextColor.GREEN));
    scheduler.enforceTeamSizes();
  }

  public void removePlayerFromTeam(String teamName, @NotNull Player player) {
    Team team = storage.getTeamByName(teamName);
    if (team == null) return;
    if (!team.hasMember(player.getName()) && !team.isLeader(player.getName())) return;
    team.removeMember(player.getName());
    storage.getPlayerTeams().remove(player.getName());
    if (team.isLeader(player.getName())) {
      if (team.getMembers().isEmpty()) {
        storage.getTeams().remove(team.getId());
      } else {
        team.setLeader(team.getMembers().get(0));
      }
    }
    storage.saveTeams(scheduler.getDeadlines());
    scheduler.enforceTeamSizes();
  }

  public void kickPlayerFromTeam(
      String teamName, @NotNull Player leader, @NotNull String targetName) {
    Team team = storage.getTeamByName(teamName);
    if (team == null || !team.isLeader(leader.getName())) return;
    if (!team.hasMember(targetName)) return;
    team.removeMember(targetName);
    storage.getPlayerTeams().remove(targetName);
    storage.saveTeams(scheduler.getDeadlines());
    scheduler.enforceTeamSizes();
  }

  public void transferLeadership(
      String teamName, @NotNull Player leader, @NotNull Player newLeader) {
    Team team = storage.getTeamByName(teamName);
    if (team == null || !team.isLeader(leader.getName())) return;
    if (!team.hasMember(newLeader.getName())) return;
    team.setLeader(newLeader.getName());
    storage.saveTeams(scheduler.getDeadlines());
  }

  public void disbandTeam(String teamName, @NotNull Player leader) {
    Team team = storage.getTeamByName(teamName);
    if (team == null || !team.isLeader(leader.getName())) return;
    storage.getTeams().remove(team.getId());
    for (String member : team.getMembers()) {
      storage.getPlayerTeams().remove(member);
    }
    storage.saveTeams(scheduler.getDeadlines());
  }

  public void renameTeam(String oldTeamName, String newTeamName, @NotNull Player leader) {
    Team team = storage.getTeamByName(oldTeamName);
    if (team == null || !team.isLeader(leader.getName())) return;
    if (storage.getTeamByName(newTeamName) != null) return;
    team.setName(newTeamName);
    storage.saveTeams(scheduler.getDeadlines());
  }

  public void setTeamPrefix(String teamName, String newPrefix, @NotNull Player leader) {
    Team team = storage.getTeamByName(teamName);
    if (team == null || !team.isLeader(leader.getName())) return;
    if (TeamUtils.isPrefixLengthInvalid(newPrefix, pluginConfig, leader)) return;
    team.setPrefix(newPrefix);
    storage.saveTeams(scheduler.getDeadlines());
  }

  public void setTeamColor(String teamName, String newColor, @NotNull Player leader) {
    Team team = storage.getTeamByName(teamName);
    if (team == null || !team.isLeader(leader.getName())) return;
    NamedTextColor teamColor = NamedTextColor.NAMES.value(newColor.toLowerCase());
    if (teamColor == null) return;
    team.setColor(newColor);
    storage.saveTeams(scheduler.getDeadlines());
  }

  public void updatePlayerPrefixes(String teamName) {
    Team team = storage.getTeamByName(teamName);
    if (team == null) return;
    for (String member : team.getMembers()) {
      Player player = plugin.getServer().getPlayer(member);
      if (player != null) {
        plugin
            .getServer()
            .getPluginManager()
            .callEvent(
                new org.example.listener.TeamChatListener.PlayerPrefixUpdateEvent(
                    player, team.getPrefixComponent()));
      }
    }
  }
}

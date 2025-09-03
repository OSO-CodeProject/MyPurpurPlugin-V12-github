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
import org.example.config.PluginConfig;
import org.example.model.Team;
import org.jetbrains.annotations.NotNull;

/** Handles persistence of team data and keeps track of player memberships. */
public class TeamStorage {

  private final JavaPlugin plugin;
  private final PluginConfig pluginConfig;

  private final Map<UUID, Team> teams = new ConcurrentHashMap<>();
  private final Map<String, UUID> playerTeams = new ConcurrentHashMap<>();

  private FileConfiguration teamsConfig;
  private File teamsFile;

  public TeamStorage(@NotNull JavaPlugin plugin, @NotNull PluginConfig pluginConfig) {
    this.plugin = plugin;
    this.pluginConfig = pluginConfig;
  }

  /** Loads team information from teams.yml and fills provided deadlines map. */
  public void loadTeams(Map<UUID, Long> deadlines) {
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
    playerTeams.clear();
    deadlines.clear();
    var section = teamsConfig.getConfigurationSection("teams");
    if (section != null) {
      for (String teamIdStr : section.getKeys(false)) {
        UUID teamId = UUID.fromString(teamIdStr);
        String name = teamsConfig.getString("teams." + teamIdStr + ".name", "");
        String leader = teamsConfig.getString("teams." + teamIdStr + ".leader", "");
        String prefix = teamsConfig.getString("teams." + teamIdStr + ".prefix", "");
        String color = teamsConfig.getString("teams." + teamIdStr + ".color", "WHITE");
        Team team = new Team(teamId, name, leader, prefix, color);
        team.setMembers(teamsConfig.getStringList("teams." + teamIdStr + ".members"));
        long deadline = teamsConfig.getLong("teams." + teamIdStr + ".deadline", 0L);
        if (deadline > 0L) {
          deadlines.put(team.getId(), deadline);
        }
        teams.put(team.getId(), team);
        for (String member : team.getMembers()) {
          playerTeams.put(member, team.getId());
        }
      }
    }
  }

  /** Saves teams and deadlines back to teams.yml. */
  public void saveTeams(Map<UUID, Long> deadlines) {
    if (teamsConfig == null || teamsFile == null) return;
    teamsConfig.set("teams", null);
    for (Map.Entry<UUID, Team> entry : teams.entrySet()) {
      UUID teamId = entry.getKey();
      Team team = entry.getValue();
      String path = "teams." + teamId;
      teamsConfig.set(path + ".name", team.getName());
      teamsConfig.set(path + ".leader", team.getLeader());
      teamsConfig.set(path + ".members", team.getMembers());
      teamsConfig.set(path + ".prefix", team.getPrefix());
      teamsConfig.set(path + ".color", team.getColor().toString().toUpperCase());
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

  public Map<UUID, Team> getTeams() {
    return teams;
  }

  public Map<String, UUID> getPlayerTeams() {
    return playerTeams;
  }

  public Team getTeamByName(String teamName) {
    return teams.values().stream()
        .filter(t -> t.getName().equals(teamName))
        .findFirst()
        .orElse(null);
  }

  public UUID getTeamIdByName(String teamName) {
    return teams.entrySet().stream()
        .filter(e -> e.getValue().getName().equals(teamName))
        .map(Map.Entry::getKey)
        .findFirst()
        .orElse(null);
  }

  public String getPlayerTeam(@NotNull Player player) {
    UUID id = playerTeams.get(player.getName());
    Team team = id != null ? teams.get(id) : null;
    return team != null ? team.getName() : null;
  }

  @NotNull
  public List<String> getTeamMembers(String teamName) {
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

  public String getTeamLeader(String teamName) {
    Team team = getTeamByName(teamName);
    return team != null ? team.getLeader() : null;
  }
}

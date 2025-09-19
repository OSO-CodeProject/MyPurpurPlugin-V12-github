package org.example.service;

import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.example.config.PluginConfig;
import org.jetbrains.annotations.NotNull;

/** Facade over team related services. Delegates work to specialised classes. */
public class TeamManager implements TeamService {

  private final JavaPlugin plugin;
  private final PluginConfig pluginConfig;
  private final TeamStorage storage;
  private final DeadlineScheduler scheduler;
  private final MembershipService membership;
  private static final long EXECUTION_THRESHOLD_MS = 50;

  public TeamManager(@NotNull JavaPlugin plugin, @NotNull PluginConfig pluginConfig) {
    this.plugin = plugin;
    this.pluginConfig = pluginConfig;
    this.storage = new TeamStorage(plugin, pluginConfig);
    this.scheduler = new DeadlineScheduler(plugin, pluginConfig, storage);
    this.storage.loadTeams(scheduler.getDeadlines());
    this.scheduler.enforceTeamSizes();
    this.storage.startAutoSave(pluginConfig.getSaveIntervalSeconds(), scheduler.getDeadlines());
    this.scheduler.start();
    this.membership = new MembershipService(plugin, pluginConfig, storage, scheduler);
  }

  private void runWithTiming(String methodName, Runnable action) {
    long start = System.nanoTime();
    action.run();
    long durationMs = (System.nanoTime() - start) / 1_000_000;
    if (durationMs > EXECUTION_THRESHOLD_MS) {
      plugin.getLogger().warning(methodName + " took " + durationMs + " ms");
    }
  }

  @Override
  public void createTeam(String teamName, String prefix, String color, @NotNull Player leader) {
    runWithTiming("createTeam", () -> membership.createTeam(teamName, prefix, color, leader));
  }

  @Override
  public void addPlayerToTeam(String teamName, @NotNull Player player) {
    runWithTiming("addPlayerToTeam", () -> membership.addPlayerToTeam(teamName, player));
  }

  @Override
  public void removePlayerFromTeam(String teamName, @NotNull Player player) {
    runWithTiming("removePlayerFromTeam", () -> membership.removePlayerFromTeam(teamName, player));
  }

  @Override
  public void kickPlayerFromTeam(
      String teamName, @NotNull Player leader, @NotNull String targetName) {
    runWithTiming(
        "kickPlayerFromTeam", () -> membership.kickPlayerFromTeam(teamName, leader, targetName));
  }

  @Override
  public void transferLeadership(
      String teamName, @NotNull Player leader, @NotNull Player newLeader) {
    runWithTiming(
        "transferLeadership", () -> membership.transferLeadership(teamName, leader, newLeader));
  }

  @Override
  public void disbandTeam(String teamName, @NotNull Player leader) {
    runWithTiming("disbandTeam", () -> membership.disbandTeam(teamName, leader));
  }

  @Override
  public void renameTeam(String oldTeamName, String newTeamName, @NotNull Player leader) {
    runWithTiming("renameTeam", () -> membership.renameTeam(oldTeamName, newTeamName, leader));
  }

  @Override
  public void setTeamPrefix(String teamName, String newPrefix, @NotNull Player leader) {
    runWithTiming("setTeamPrefix", () -> membership.setTeamPrefix(teamName, newPrefix, leader));
  }

  @Override
  public void setTeamColor(String teamName, String newColor, @NotNull Player leader) {
    runWithTiming("setTeamColor", () -> membership.setTeamColor(teamName, newColor, leader));
  }

  @Override
  public void updatePlayerPrefixes(String teamName) {
    runWithTiming("updatePlayerPrefixes", () -> membership.updatePlayerPrefixes(teamName));
  }

  @Override
  public String getPlayerTeam(@NotNull Player player) {
    return storage.getPlayerTeam(player);
  }

  @Override
  public @NotNull List<String> getTeamMembers(String teamName) {
    return storage.getTeamMembers(teamName);
  }

  @Override
  public @NotNull List<String> getTeamNames() {
    return storage.getTeamNames();
  }

  @Override
  public String getTeamPrefix(String teamName) {
    return storage.getTeamPrefix(teamName);
  }

  @Override
  public @NotNull NamedTextColor getTeamColor(String teamName) {
    return storage.getTeamColor(teamName);
  }

  @Override
  public String getTeamLeader(String teamName) {
    return storage.getTeamLeader(teamName);
  }

  @Override
  public @NotNull JavaPlugin getPlugin() {
    return plugin;
  }

  @Override
  public @NotNull PluginConfig getPluginConfig() {
    return pluginConfig;
  }

  @Override
  public boolean isEnforceMaxMembersOnReload() {
    return pluginConfig.isEnforceMaxMembersOnReload();
  }

  @Override
  public boolean isGracePeriodEnabled() {
    return pluginConfig.isGracePeriodEnabled();
  }

  @Override
  public void reloadConfig() {
    storage.flushNow();
    storage.stopAutoSave();
    scheduler.stop();
    pluginConfig.reloadConfig();
    storage.loadTeams(scheduler.getDeadlines());
    storage.getTeams().values().forEach(membership::updateTeamMembersPrefixes);
    scheduler.resetLeaderDisplays();
    scheduler.enforceTeamSizes();
    scheduler.start();
    storage.startAutoSave(pluginConfig.getSaveIntervalSeconds(), scheduler.getDeadlines());
  }

  @Override
  public UUID getTeamIdByName(String teamName) {
    return storage.getTeamIdByName(teamName);
  }

  @Override
  public Long getTeamDeadline(String teamName) {
    return scheduler.getTeamDeadline(teamName);
  }

  @Override
  public void shutdown() {
    storage.stopAutoSave();
    scheduler.resetLeaderDisplays();
    scheduler.stop();
    storage.flushNow();
  }
}

package org.example.service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.example.config.JoinMode;
import org.example.config.PluginConfig;
import org.example.listener.TeamChatListener;
import org.example.model.PendingInvite;
import org.example.model.PendingRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    this.membership = new MembershipService(plugin, pluginConfig, storage, scheduler);
    this.storage.loadTeams(scheduler.getDeadlines(), membership);
    this.scheduler.enforceTeamSizes(true);
    this.storage.startAutoSave(pluginConfig.getSaveIntervalSeconds(), scheduler.getDeadlines());
    this.scheduler.start();
  }

  private void runWithTiming(String methodName, Runnable action) {
    runWithTiming(
        methodName,
        () -> {
          action.run();
          return null;
        });
  }

  private <T> T runWithTiming(String methodName, Supplier<T> action) {
    long start = System.nanoTime();
    T result = action.get();
    long durationMs = (System.nanoTime() - start) / 1_000_000;
    if (durationMs > EXECUTION_THRESHOLD_MS) {
      plugin.getLogger().warning(methodName + " took " + durationMs + " ms");
    }
    return result;
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
  public void removePlayerFromTeam(
      String teamName,
      @NotNull Player player,
      @NotNull MemberRemovalCause cause,
      @Nullable String initiatorName,
      @Nullable UUID initiatorId) {
    runWithTiming(
        "removePlayerFromTeam",
        () -> membership.removePlayerFromTeam(teamName, player, cause, initiatorName, initiatorId));
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
  public @NotNull RenameResult renameTeam(
      String oldTeamName, String newTeamName, @NotNull Player leader) {
    return runWithTiming(
        "renameTeam", () -> membership.renameTeam(oldTeamName, newTeamName, leader));
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
  public @NotNull List<UUID> getTeamMembers(String teamName) {
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
  public UUID getTeamLeaderId(String teamName) {
    return storage.getTeamLeaderId(teamName);
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
  public @NotNull JoinMode getJoinMode() {
    return pluginConfig.getJoinMode();
  }

  @Override
  public boolean isEnforceMaxMembersOnReload() {
    return pluginConfig.isEnforceMaxMembersOnReload();
  }

  @Override
  public void sendInvite(@NotNull Player leader, @NotNull Player target, @Nullable Duration ttl) {
    runWithTiming("sendInvite", () -> membership.sendInvite(leader, target, ttl));
  }

  @Override
  public void revokeInvite(@NotNull Player leader, @NotNull String targetName) {
    runWithTiming("revokeInvite", () -> membership.revokeInvite(leader, targetName));
  }

  @Override
  public @NotNull List<String> getRevocableInviteTargets(@NotNull Player leader) {
    return runWithTiming(
        "getRevocableInviteTargets", () -> membership.getRevocableInviteTargets(leader));
  }

  @Override
  public void acceptInvite(@NotNull Player player, @NotNull String teamName) {
    runWithTiming("acceptInvite", () -> membership.acceptInvite(player, teamName));
  }

  @Override
  public void declineInvite(@NotNull Player player, @NotNull String teamName) {
    runWithTiming("declineInvite", () -> membership.declineInvite(player, teamName));
  }

  @Override
  public @NotNull List<PendingInvite> getInvitesForPlayer(@NotNull UUID playerId) {
    return runWithTiming("getInvitesForPlayer", () -> membership.getInvitesForPlayer(playerId));
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
    storage.loadTeams(scheduler.getDeadlines(), membership);
    storage.getTeams().values().forEach(membership::updateTeamMembersPrefixes);
    scheduler.resetLeaderDisplays();
    scheduler.enforceTeamSizes(true);
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
  public void submitJoinRequest(String teamName, @NotNull Player player) {
    runWithTiming("submitJoinRequest", () -> membership.submitJoinRequest(teamName, player));
  }

  @Override
  public void cancelJoinRequest(String teamName, @NotNull Player player) {
    runWithTiming("cancelJoinRequest", () -> membership.cancelJoinRequest(teamName, player));
  }

  @Override
  public @NotNull List<PendingRequest> listJoinRequests(String teamName) {
    return runWithTiming("listJoinRequests", () -> membership.listJoinRequests(teamName));
  }

  @Override
  public @NotNull List<PendingRequest> getJoinRequestsForPlayer(@NotNull UUID playerId) {
    return runWithTiming(
        "getJoinRequestsForPlayer", () -> membership.getJoinRequestsForPlayer(playerId));
  }

  @Override
  public void approveJoinRequest(
      String teamName, @NotNull Player leader, @NotNull String targetName) {
    runWithTiming(
        "approveJoinRequest", () -> membership.approveJoinRequest(teamName, leader, targetName));
  }

  @Override
  public void denyJoinRequest(String teamName, @NotNull Player leader, @NotNull String targetName) {
    runWithTiming(
        "denyJoinRequest", () -> membership.denyJoinRequest(teamName, leader, targetName));
  }

  @Override
  public boolean hasPendingJoinRequest(String teamName, @NotNull UUID playerId) {
    return membership.hasPendingJoinRequest(teamName, playerId);
  }

  @Override
  public void shutdown() {
    plugin
        .getServer()
        .getOnlinePlayers()
        .forEach(
            player ->
                plugin
                    .getServer()
                    .getPluginManager()
                    .callEvent(new TeamChatListener.PlayerPrefixUpdateEvent(player, null)));
    storage.stopAutoSave();
    scheduler.resetLeaderDisplays();
    scheduler.stop();
    storage.flushNow();
  }
}

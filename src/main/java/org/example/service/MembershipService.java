package org.example.service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.example.config.JoinMode;
import org.example.config.PluginConfig;
import org.example.listener.TeamChatListener;
import org.example.model.PendingInvite;
import org.example.model.PendingRequest;
import org.example.model.Team;
import org.example.util.TeamMessageUtils;
import org.example.util.TeamUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Contains operations that modify teams and player memberships. */
public class MembershipService {

  private final JavaPlugin plugin;
  private final PluginConfig pluginConfig;
  private final TeamStorage storage;
  private final DeadlineScheduler scheduler;
  private final Map<UUID, Map<UUID, PendingRequest>> joinRequestsByTeam =
      new ConcurrentHashMap<>();
  private final Map<UUID, Map<UUID, PendingRequest>> joinRequestsByPlayer =
      new ConcurrentHashMap<>();
  private final Map<UUID, Map<UUID, PendingInvite>> invitesByPlayer = new ConcurrentHashMap<>();
  private final Map<UUID, Map<UUID, PendingInvite>> invitesByTeam = new ConcurrentHashMap<>();

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

  public void loadPendingInvites(@NotNull Collection<PendingInvite> invites) {
    invitesByPlayer.clear();
    invitesByTeam.clear();
    for (PendingInvite invite : invites) {
      if (invite != null && !invite.isExpired()) {
        storeInvite(invite, false);
      }
    }
  }

  public void loadPendingRequests(@NotNull Collection<PendingRequest> requests) {
    joinRequestsByTeam.clear();
    joinRequestsByPlayer.clear();
    for (PendingRequest request : requests) {
      if (request != null && !request.isExpired()) {
        storeJoinRequest(request, false);
      }
    }
  }

  public void createTeam(String teamName, String prefix, String color, @NotNull Player leader) {
    String normalizedTeamName = teamName == null ? "" : teamName.trim();
    String normalizedPrefix = prefix == null ? "" : prefix.trim();
    String normalizedColor = color == null ? "" : color.trim();
    // Убеждаемся, что имя и лидер свободны, чтобы не создавать дубликатов команд.
    if (storage.getPlayerTeam(leader) != null
        || storage.getTeamByName(normalizedTeamName) != null) {
      TeamMessageUtils.sendTeamMessage(
          leader, TeamMessageUtils.teamAlreadyExistsMessage(normalizedTeamName));
      return;
    }
    // Проверяем, что игрок выбрал поддерживаемый цвет для корректного отображения.
    NamedTextColor teamColor = NamedTextColor.NAMES.value(normalizedColor.toLowerCase(Locale.ROOT));
    if (teamColor == null) {
      TeamMessageUtils.sendTeamMessage(
          leader, Component.text("❌ Неверный цвет команды", NamedTextColor.RED));
      return;
    }
    // Валидируем длину имени и префикса согласно настройкам плагина.
    if (TeamUtils.isTeamNameLengthInvalid(normalizedTeamName, pluginConfig, leader)
        || TeamUtils.isPrefixLengthInvalid(normalizedPrefix, pluginConfig, leader)) {
      return;
    }
    // Создаём запись команды и связываем лидера с новой структурой.
    Team team =
        new Team(normalizedTeamName, leader.getUniqueId(), normalizedPrefix, normalizedColor);
    storage.addTeam(team);
    updateTeamMembersPrefixes(team);
    storage.assignPlayerToTeam(leader.getUniqueId(), team);
    TeamMessageUtils.sendTeamMessage(
        leader, Component.text("✅ Команда создана", NamedTextColor.GREEN));
    scheduler.evaluateTeam(team);
  }

  public void addPlayerToTeam(String teamName, @NotNull Player player) {
    String normalizedTeamName = teamName == null ? "" : teamName.trim();
    Team team = storage.getTeamByName(normalizedTeamName);
    // Если команда не найдена — сообщаем игроку и прекращаем обработку.
    if (team == null) {
      TeamMessageUtils.sendTeamMessage(
          player, TeamMessageUtils.teamDoesNotExistMessage(normalizedTeamName));
      return;
    }
    // Не допускаем вступления игроков, у которых уже есть команда.
    if (storage.getPlayerTeam(player) != null) {
      TeamMessageUtils.sendTeamMessage(
          player, Component.text("❌ Вы уже состоите в команде", NamedTextColor.RED));
      return;
    }
    // Учитываем ограничение на максимальное количество участников.
    int max = pluginConfig.getMaxMembers();
    if (max > 0 && team.getMembers().size() >= max) {
      TeamMessageUtils.sendTeamMessage(
          player, Component.text("❌ Команда полная", NamedTextColor.RED));
      return;
    }
    // Добавляем игрока и фиксируем изменения в хранилище.
    team.addMember(player.getUniqueId());
    storage.assignPlayerToTeam(player.getUniqueId(), team);
    clearInvitesForPlayer(player.getUniqueId());
    clearJoinRequestsForPlayer(player.getUniqueId(), team.getLeaderId());
    removeJoinRequest(
        team.getId(),
        player.getUniqueId(),
        JoinRequestRemovalCause.JOINED,
        player.getName(),
        player.getUniqueId(),
        team);
    storage.markTeamDirty(team);
    TeamMessageUtils.sendTeamMessage(
        player, Component.text("✅ Вы вступили в команду", NamedTextColor.GREEN));
    scheduler.evaluateTeam(team);
    updateTeamMembersPrefixes(team);
  }

  public void submitJoinRequest(String teamName, @NotNull Player player) {
    String normalizedTeamName = teamName == null ? "" : teamName.trim();
    Team team = storage.getTeamByName(normalizedTeamName);
    if (team == null) {
      TeamMessageUtils.sendTeamMessage(
          player, TeamMessageUtils.teamDoesNotExistMessage(normalizedTeamName));
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
          player, TeamMessageUtils.teamIsFullMessage(team.getName()));
      return;
    }
    UUID teamId = team.getId();
    UUID playerId = player.getUniqueId();
    PendingRequest existing = peekJoinRequest(teamId, playerId);
    if (existing != null && !existing.isExpired()) {
      TeamMessageUtils.sendTeamMessage(
          player, TeamMessageUtils.joinRequestAlreadySentMessage(team.getName()));
      return;
    }
    if (existing != null) {
      removeJoinRequest(teamId, playerId, JoinRequestRemovalCause.EXPIRED, null, null, team);
    }
    PendingRequest request =
        new PendingRequest(
            teamId,
            playerId,
            team.getName(),
            player.getName() != null ? player.getName() : resolvePlayerName(playerId, null),
            null);
    storeJoinRequest(request);
    TeamMessageUtils.sendTeamMessage(
        player, TeamMessageUtils.joinRequestSentPlayerMessage(team.getName()));
    Player leaderPlayer = resolveOnlineLeader(team);
    if (leaderPlayer != null) {
      TeamMessageUtils.sendTeamMessage(
          leaderPlayer,
          TeamMessageUtils.joinRequestReceivedLeaderMessage(request.getPlayerName(), team.getName()));
    }
  }

  public void cancelJoinRequest(String teamName, @NotNull Player player) {
    Team team = storage.getTeamByName(teamName);
    if (team == null) {
      TeamMessageUtils.sendTeamMessage(player, TeamMessageUtils.teamDoesNotExistMessage(teamName));
      return;
    }
    PendingRequest request = peekJoinRequest(team.getId(), player.getUniqueId());
    if (request == null) {
      TeamMessageUtils.sendTeamMessage(player, TeamMessageUtils.joinRequestNotFoundMessage(teamName));
      return;
    }
    removeJoinRequest(
        request.getTeamId(),
        request.getPlayerId(),
        JoinRequestRemovalCause.CANCELLED,
        player.getName(),
        null,
        team);
  }

  public void approveJoinRequest(
      String teamName, @NotNull Player leader, @NotNull String targetName) {
    Team team = storage.getTeamByName(teamName);
    if (team == null) {
      TeamMessageUtils.sendTeamMessage(leader, TeamMessageUtils.teamDoesNotExistMessage(teamName));
      return;
    }
    if (!team.isLeader(leader.getUniqueId())) {
      TeamMessageUtils.sendTeamMessage(leader, TeamMessageUtils.notTeamLeaderMessage());
      return;
    }
    PendingRequest request = findJoinRequestForTeam(team, targetName);
    if (request == null) {
      TeamMessageUtils.sendTeamMessage(
          leader, TeamMessageUtils.joinRequestNotFoundLeaderMessage(targetName));
      return;
    }
    int max = pluginConfig.getMaxMembers();
    if (max > 0 && team.getMembers().size() >= max) {
      TeamMessageUtils.sendTeamMessage(leader, TeamMessageUtils.teamIsFullMessage(team.getName()));
      return;
    }
    Player target = plugin.getServer().getPlayer(request.getPlayerId());
    if (target == null) {
      OfflinePlayer offline = plugin.getServer().getOfflinePlayer(request.getPlayerId());
      String targetDisplayName =
          offline != null && offline.getName() != null
              ? offline.getName()
              : request.getPlayerName();
      TeamMessageUtils.sendTeamMessage(
          leader, TeamMessageUtils.joinRequestTargetOfflineMessage(targetDisplayName));
      return;
    }
    removeJoinRequest(
        request.getTeamId(),
        request.getPlayerId(),
        JoinRequestRemovalCause.APPROVED,
        leader.getName(),
        leader.getUniqueId(),
        team);
    addPlayerToTeam(team.getName(), target);
  }

  public void denyJoinRequest(String teamName, @NotNull Player leader, @NotNull String targetName) {
    Team team = storage.getTeamByName(teamName);
    if (team == null) {
      TeamMessageUtils.sendTeamMessage(leader, TeamMessageUtils.teamDoesNotExistMessage(teamName));
      return;
    }
    if (!team.isLeader(leader.getUniqueId())) {
      TeamMessageUtils.sendTeamMessage(leader, TeamMessageUtils.notTeamLeaderMessage());
      return;
    }
    PendingRequest request = findJoinRequestForTeam(team, targetName);
    if (request == null) {
      TeamMessageUtils.sendTeamMessage(
          leader, TeamMessageUtils.joinRequestNotFoundLeaderMessage(targetName));
      return;
    }
    removeJoinRequest(
        request.getTeamId(),
        request.getPlayerId(),
        JoinRequestRemovalCause.DENIED,
        leader.getName(),
        leader.getUniqueId(),
        team);
  }

  public @NotNull List<PendingRequest> listJoinRequests(String teamName) {
    Team team = storage.getTeamByName(teamName);
    if (team == null) {
      return List.of();
    }
    Map<UUID, PendingRequest> requests = joinRequestsByTeam.get(team.getId());
    if (requests == null || requests.isEmpty()) {
      return List.of();
    }
    List<PendingRequest> active = new ArrayList<>();
    List<PendingRequest> expired = new ArrayList<>();
    for (PendingRequest request : requests.values()) {
      if (request.isExpired()) {
        expired.add(request);
      } else {
        active.add(request);
      }
    }
    for (PendingRequest expiredRequest : expired) {
      removeJoinRequest(
          expiredRequest.getTeamId(),
          expiredRequest.getPlayerId(),
          JoinRequestRemovalCause.EXPIRED,
          null,
          null,
          team);
    }
    return List.copyOf(active);
  }

  public @NotNull List<PendingRequest> getJoinRequestsForPlayer(@NotNull UUID playerId) {
    Map<UUID, PendingRequest> requests = joinRequestsByPlayer.get(playerId);
    if (requests == null || requests.isEmpty()) {
      return List.of();
    }
    List<PendingRequest> active = new ArrayList<>();
    List<PendingRequest> expired = new ArrayList<>();
    for (PendingRequest request : requests.values()) {
      if (request.isExpired()) {
        expired.add(request);
      } else {
        active.add(request);
      }
    }
    for (PendingRequest expiredRequest : expired) {
      removeJoinRequest(
          expiredRequest.getTeamId(),
          expiredRequest.getPlayerId(),
          JoinRequestRemovalCause.EXPIRED,
          null,
          null,
          storage.getTeams().get(expiredRequest.getTeamId()));
    }
    return List.copyOf(active);
  }

  public boolean hasPendingJoinRequest(String teamName, @NotNull UUID playerId) {
    Team team = storage.getTeamByName(teamName);
    if (team == null) {
      return false;
    }
    PendingRequest request = peekJoinRequest(team.getId(), playerId);
    if (request == null) {
      return false;
    }
    if (request.isExpired()) {
      removeJoinRequest(
          request.getTeamId(),
          request.getPlayerId(),
          JoinRequestRemovalCause.EXPIRED,
          null,
          null,
          team);
      return false;
    }
    return true;
  }

  public void sendInvite(@NotNull Player leader, @NotNull Player target, @Nullable Duration ttl) {
    if (pluginConfig.getJoinMode() != JoinMode.INVITE_ONLY) {
      TeamMessageUtils.sendTeamMessage(leader, TeamMessageUtils.invitesDisabledMessage());
      return;
    }
    UUID leaderId = leader.getUniqueId();
    UUID teamId = storage.getPlayerTeams().get(leaderId);
    if (teamId == null) {
      TeamMessageUtils.sendTeamMessage(leader, TeamMessageUtils.playerNotInAnyTeamMessage());
      return;
    }
    Team team = storage.getTeams().get(teamId);
    if (team == null) {
      TeamMessageUtils.sendTeamMessage(leader, TeamMessageUtils.teamDoesNotExistMessage(null));
      return;
    }
    if (!team.isLeader(leaderId)) {
      TeamMessageUtils.sendTeamMessage(leader, TeamMessageUtils.notTeamLeaderMessage());
      return;
    }
    UUID targetId = target.getUniqueId();
    if (leaderId.equals(targetId)) {
      TeamMessageUtils.sendTeamMessage(leader, TeamMessageUtils.cannotInviteSelfMessage());
      return;
    }
    String targetTeamName = storage.getPlayerTeam(target);
    if (targetTeamName != null) {
      TeamMessageUtils.sendTeamMessage(
          leader, TeamMessageUtils.targetAlreadyInTeamMessage(target.getName(), targetTeamName));
      return;
    }
    int maxMembers = pluginConfig.getMaxMembers();
    if (maxMembers > 0 && team.getMembers().size() >= maxMembers) {
      TeamMessageUtils.sendTeamMessage(leader, TeamMessageUtils.teamIsFullMessage(team.getName()));
      return;
    }
    PendingInvite existing = peekInvite(teamId, targetId);
    if (existing != null) {
      if (!existing.isExpired()) {
        TeamMessageUtils.sendTeamMessage(
            leader, TeamMessageUtils.inviteAlreadyExistsMessage(existing.getTargetName(), team.getName()));
        return;
      }
      removeInvite(teamId, targetId);
    }

    PendingInvite invite =
        new PendingInvite(
            teamId,
            targetId,
            leaderId,
            team.getName(),
            leader.getName() != null ? leader.getName() : resolvePlayerName(leaderId, null),
            target.getName(),
            ttl);
    storeInvite(invite);

    TeamMessageUtils.sendTeamMessage(
        target,
        TeamMessageUtils.inviteReceivedMessage(
            invite.getTeamName(),
            invite.getInviterName(),
            invite.getExpiresAt(),
            "/team accept " + invite.getTeamName(),
            "/team decline " + invite.getTeamName()));
    TeamMessageUtils.sendTeamMessage(
        leader, TeamMessageUtils.inviteSentLeaderMessage(invite.getTargetName(), invite.getExpiresAt()));
    sendMessageToOnlinePlayers(
        team.getMembers(),
        TeamMessageUtils.inviteSentTeamBroadcastMessage(invite.getTargetName(), invite.getInviterName()),
        leaderId);
    notifyAdmins(
        TeamMessageUtils.inviteSentAdminMessage(
            invite.getInviterName(), invite.getTargetName(), invite.getTeamName()));
  }

  public void revokeInvite(@NotNull Player leader, @NotNull String targetName) {
    if (pluginConfig.getJoinMode() != JoinMode.INVITE_ONLY) {
      TeamMessageUtils.sendTeamMessage(leader, TeamMessageUtils.invitesDisabledMessage());
      return;
    }
    UUID leaderId = leader.getUniqueId();
    UUID teamId = storage.getPlayerTeams().get(leaderId);
    if (teamId == null) {
      TeamMessageUtils.sendTeamMessage(leader, TeamMessageUtils.playerNotInAnyTeamMessage());
      return;
    }
    Team team = storage.getTeams().get(teamId);
    if (team == null || !team.isLeader(leaderId)) {
      TeamMessageUtils.sendTeamMessage(leader, TeamMessageUtils.notTeamLeaderMessage());
      return;
    }
    PendingInvite invite = findInviteForTeam(teamId, targetName);
    if (invite == null) {
      TeamMessageUtils.sendTeamMessage(
          leader, TeamMessageUtils.inviteNotFoundForLeaderMessage(targetName, team.getName()));
      return;
    }
    removeInvite(invite.getTeamId(), invite.getTargetPlayerId());
    TeamMessageUtils.sendTeamMessage(
        leader, TeamMessageUtils.inviteRevokedLeaderMessage(invite.getTargetName()));
    Player targetPlayer = plugin.getServer().getPlayer(invite.getTargetPlayerId());
    if (targetPlayer != null) {
      TeamMessageUtils.sendTeamMessage(
          targetPlayer, TeamMessageUtils.inviteRevokedTargetMessage(invite.getTeamName()));
    }
    notifyAdmins(
        TeamMessageUtils.inviteRevokedAdminMessage(
            invite.getInviterName(), invite.getTargetName(), invite.getTeamName()));
  }

  public @NotNull List<String> getRevocableInviteTargets(@NotNull Player leader) {
    if (pluginConfig.getJoinMode() != JoinMode.INVITE_ONLY) {
      return List.of();
    }
    UUID leaderId = leader.getUniqueId();
    UUID teamId = storage.getPlayerTeams().get(leaderId);
    if (teamId == null) {
      return List.of();
    }
    Team team = storage.getTeams().get(teamId);
    if (team == null || !team.isLeader(leaderId)) {
      return List.of();
    }
    Map<UUID, PendingInvite> invites = invitesByTeam.get(teamId);
    if (invites == null || invites.isEmpty()) {
      return List.of();
    }
    List<PendingInvite> expired = new ArrayList<>();
    List<String> names = new ArrayList<>();
    for (PendingInvite invite : invites.values()) {
      if (invite.isExpired()) {
        expired.add(invite);
        continue;
      }
      String storedName = invite.getTargetName();
      String resolvedName = resolvePlayerName(invite.getTargetPlayerId(), storedName);
      names.add(resolvedName != null ? resolvedName : storedName);
    }
    for (PendingInvite invite : expired) {
      removeInvite(invite.getTeamId(), invite.getTargetPlayerId());
    }
    return names;
  }

  public void acceptInvite(@NotNull Player player, @NotNull String teamName) {
    if (pluginConfig.getJoinMode() != JoinMode.INVITE_ONLY) {
      TeamMessageUtils.sendTeamMessage(player, TeamMessageUtils.invitesDisabledMessage());
      return;
    }
    Team team = storage.getTeamByName(teamName);
    if (team == null) {
      TeamMessageUtils.sendTeamMessage(player, TeamMessageUtils.teamDoesNotExistMessage(teamName));
      return;
    }
    UUID playerId = player.getUniqueId();
    PendingInvite invite = peekInvite(team.getId(), playerId);
    if (invite == null) {
      TeamMessageUtils.sendTeamMessage(
          player, TeamMessageUtils.inviteNotFoundForPlayerMessage(teamName));
      return;
    }
    if (invite.isExpired()) {
      removeInvite(invite.getTeamId(), invite.getTargetPlayerId());
      TeamMessageUtils.sendTeamMessage(player, TeamMessageUtils.inviteExpiredMessage(team.getName()));
      return;
    }
    if (storage.getPlayerTeam(player) != null) {
      TeamMessageUtils.sendTeamMessage(
          player, Component.text("❌ Вы уже состоите в команде", NamedTextColor.RED));
      removeInvite(invite.getTeamId(), invite.getTargetPlayerId());
      return;
    }
    int maxMembers = pluginConfig.getMaxMembers();
    if (maxMembers > 0 && team.getMembers().size() >= maxMembers) {
      TeamMessageUtils.sendTeamMessage(player, TeamMessageUtils.teamIsFullMessage(team.getName()));
      return;
    }

    removeInvite(invite.getTeamId(), invite.getTargetPlayerId());
    addPlayerToTeam(team.getName(), player);
    TeamMessageUtils.sendTeamMessage(
        player, TeamMessageUtils.inviteAcceptedMessage(team.getName()));
    sendMessageToOnlinePlayers(
        team.getMembers(),
        TeamMessageUtils.inviteAcceptedBroadcastMessage(player.getName()),
        playerId);
    notifyAdmins(
        TeamMessageUtils.inviteAcceptedAdminMessage(
            invite.getInviterName(), invite.getTargetName(), invite.getTeamName()));
  }

  public void declineInvite(@NotNull Player player, @NotNull String teamName) {
    if (pluginConfig.getJoinMode() != JoinMode.INVITE_ONLY) {
      TeamMessageUtils.sendTeamMessage(player, TeamMessageUtils.invitesDisabledMessage());
      return;
    }
    Team team = storage.getTeamByName(teamName);
    if (team == null) {
      TeamMessageUtils.sendTeamMessage(player, TeamMessageUtils.teamDoesNotExistMessage(teamName));
      return;
    }
    UUID playerId = player.getUniqueId();
    PendingInvite invite = peekInvite(team.getId(), playerId);
    if (invite == null) {
      TeamMessageUtils.sendTeamMessage(
          player, TeamMessageUtils.inviteNotFoundForPlayerMessage(teamName));
      return;
    }
    if (invite.isExpired()) {
      removeInvite(invite.getTeamId(), invite.getTargetPlayerId());
      TeamMessageUtils.sendTeamMessage(player, TeamMessageUtils.inviteExpiredMessage(team.getName()));
      return;
    }
    removeInvite(invite.getTeamId(), invite.getTargetPlayerId());
    TeamMessageUtils.sendTeamMessage(player, TeamMessageUtils.inviteDeclinedMessage(team.getName()));
    sendMessageToOnlinePlayers(
        team.getMembers(),
        TeamMessageUtils.inviteDeclinedBroadcastMessage(player.getName()),
        playerId);
    notifyAdmins(
        TeamMessageUtils.inviteDeclinedAdminMessage(
            invite.getInviterName(), invite.getTargetName(), invite.getTeamName()));
  }

  public @NotNull List<PendingInvite> getInvitesForPlayer(@NotNull UUID playerId) {
    Map<UUID, PendingInvite> invites = invitesByPlayer.get(playerId);
    if (invites == null || invites.isEmpty()) {
      return List.of();
    }
    List<PendingInvite> activeInvites = new ArrayList<>();
    List<PendingInvite> expiredInvites = new ArrayList<>();
    for (PendingInvite invite : invites.values()) {
      if (invite.isExpired()) {
        expiredInvites.add(invite);
      } else {
        activeInvites.add(invite);
      }
    }
    for (PendingInvite expired : expiredInvites) {
      removeInvite(expired.getTeamId(), expired.getTargetPlayerId());
    }
    return List.copyOf(activeInvites);
  }

  public void removePlayerFromTeam(String teamName, @NotNull Player player) {
    removePlayerFromTeam(teamName, player, MemberRemovalCause.LEAVE, null, player.getUniqueId());
  }

  public void removePlayerFromTeam(
      String teamName,
      @NotNull Player player,
      @NotNull MemberRemovalCause cause,
      @Nullable String initiatorName,
      @Nullable UUID initiatorId) {
    Team team = storage.getTeamByName(teamName);
    if (team == null) {
      return;
    }
    removeMember(
        team, player.getUniqueId(), player, player.getName(), cause, initiatorName, initiatorId);
  }

  private boolean removeMember(
      @NotNull Team team,
      @NotNull UUID playerId,
      @Nullable Player onlinePlayer,
      @Nullable String providedName,
      @NotNull MemberRemovalCause cause,
      @Nullable String initiatorName,
      @Nullable UUID initiatorId) {
    if (!team.hasMember(playerId) && !team.isLeader(playerId)) {
      return false;
    }
    boolean wasLeader = team.isLeader(playerId);
    UUID newLeaderId = null;
    String newLeaderName = null;
    team.removeMember(playerId);
    storage.clearPlayerTeam(playerId);
    clearInvitesForPlayer(playerId);
    boolean removedTeam = false;
    if (wasLeader) {
      if (team.getMembers().isEmpty()) {
        scheduler.cancelDeadline(team);
        storage.removeTeam(team);
        removedTeam = true;
      } else {
        UUID newLeader = team.getFirstMember();
        if (newLeader != null) {
          team.setLeader(newLeader);
          newLeaderId = newLeader;
          newLeaderName = resolvePlayerName(newLeader, null);
        }
        scheduler.handleLeaderTransfer(team);
      }
    }
    if (newLeaderId != null && !removedTeam) {
      Player newLeaderPlayer = plugin.getServer().getPlayer(newLeaderId);
      if (newLeaderPlayer != null) {
        TeamMessageUtils.sendTeamMessage(
            newLeaderPlayer, TeamMessageUtils.leadershipTransferIncomingMessage(team.getName()));
      }
      sendMessageToOnlinePlayers(
          team.getMembers(),
          TeamMessageUtils.leadershipTransferBroadcastMessage(newLeaderName),
          newLeaderId);
    }
    if (!removedTeam) {
      storage.markTeamDirty(team);
      scheduler.evaluateTeam(team);
    }
    if (removedTeam) {
      clearJoinRequests(team, JoinRequestRemovalCause.EXPIRED, null);
      clearInvitesForTeam(team.getId());
    }
    if (onlinePlayer != null) {
      notifyPrefixUpdate(onlinePlayer, null);
    } else {
      notifyPrefixUpdate(playerId, null);
    }
    if (!removedTeam) {
      updateTeamMembersPrefixes(team);
    }
    sendRemovalMessages(
        team,
        playerId,
        onlinePlayer,
        resolvePlayerName(playerId, providedName),
        cause,
        initiatorName,
        initiatorId,
        wasLeader,
        removedTeam);
    return true;
  }

  private @NotNull String resolvePlayerName(@NotNull UUID playerId, @Nullable String providedName) {
    if (providedName != null && !providedName.isBlank()) {
      return providedName;
    }
    OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(playerId);
    if (offlinePlayer != null) {
      String name = offlinePlayer.getName();
      if (name != null && !name.isBlank()) {
        return name;
      }
    }
    return playerId.toString();
  }

  private void sendRemovalMessages(
      @NotNull Team team,
      @NotNull UUID playerId,
      @Nullable Player onlinePlayer,
      @NotNull String playerName,
      @NotNull MemberRemovalCause cause,
      @Nullable String initiatorName,
      @Nullable UUID initiatorId,
      boolean wasLeader,
      boolean removedTeam) {
    if (cause == MemberRemovalCause.LEAVE) {
      if (onlinePlayer != null) {
        TeamMessageUtils.sendTeamMessage(
            onlinePlayer, TeamMessageUtils.memberLeftSelfMessage(team.getName()));
        if (removedTeam && wasLeader) {
          TeamMessageUtils.sendTeamMessage(
              onlinePlayer, TeamMessageUtils.teamDisbandedLeaderMessage(team.getName()));
        }
      }
      if (!removedTeam) {
        sendMessageToOnlinePlayers(
            team.getMembers(),
            TeamMessageUtils.memberLeftBroadcastMessage(playerName),
            mergeExcluded(playerId, initiatorId));
      }
      return;
    }

    if (onlinePlayer != null) {
      TeamMessageUtils.sendTeamMessage(
          onlinePlayer, TeamMessageUtils.memberKickedTargetMessage(team.getName(), initiatorName));
    }
    if (!removedTeam) {
      sendMessageToOnlinePlayers(
          team.getMembers(),
          TeamMessageUtils.memberKickedBroadcastMessage(playerName),
          mergeExcluded(playerId, initiatorId));
    } else if (onlinePlayer != null && wasLeader) {
      TeamMessageUtils.sendTeamMessage(
          onlinePlayer, TeamMessageUtils.teamDisbandedLeaderMessage(team.getName()));
    }
  }

  private UUID[] mergeExcluded(@NotNull UUID playerId, @Nullable UUID initiatorId) {
    if (initiatorId == null || initiatorId.equals(playerId)) {
      return new UUID[] {playerId};
    }
    return new UUID[] {playerId, initiatorId};
  }

  public void kickPlayerFromTeam(
      String teamName, @NotNull Player leader, @NotNull String targetName) {
    Team team = storage.getTeamByName(teamName);
    // Проверяем право лидера на это действие и наличие цели в команде.

    if (team == null || !team.isLeader(leader.getUniqueId())) return;
    UUID targetId = findMemberIdByName(team, targetName);
    if (targetId == null) {
      return;
    }
    if (team.isLeader(targetId)) {
      removePlayerFromTeam(teamName, leader, MemberRemovalCause.LEAVE, null, leader.getUniqueId());
      return;
    }
    // Удаляем участника и фиксируем изменения.

    String actualTargetName = targetName;
    String leaderName = leader.getName();
    OfflinePlayer offlineTarget = plugin.getServer().getOfflinePlayer(targetId);
    if (offlineTarget != null && offlineTarget.getName() != null) {
      actualTargetName = offlineTarget.getName();
    }
    Player kickedPlayer = plugin.getServer().getPlayer(targetId);
    boolean removed =
        removeMember(
            team,
            targetId,
            kickedPlayer,
            actualTargetName,
            MemberRemovalCause.KICK,
            leaderName,
            leader.getUniqueId());
    if (!removed) {
      return;
    }
    TeamMessageUtils.sendTeamMessage(
        leader, TeamMessageUtils.memberKickedLeaderMessage(actualTargetName));
  }

  public void transferLeadership(
      String teamName, @NotNull Player leader, @NotNull Player newLeader) {
    Team team = storage.getTeamByName(teamName);
    // Убеждаемся, что изменение лидерства инициирует действующий лидер.
    if (team == null || !team.isLeader(leader.getUniqueId())) return;
    // Назначать лидером можно только участника команды.
    if (!team.hasMember(newLeader.getUniqueId())) return;
    UUID previousLeader = leader.getUniqueId();
    team.setLeader(newLeader.getUniqueId());
    storage.markTeamDirty(team);
    scheduler.handleLeaderTransfer(team);
    scheduler.evaluateTeam(team);
    String newLeaderName = newLeader.getName();
    TeamMessageUtils.sendTeamMessage(
        leader, TeamMessageUtils.leadershipTransferOutgoingMessage(newLeaderName));
    TeamMessageUtils.sendTeamMessage(
        newLeader, TeamMessageUtils.leadershipTransferIncomingMessage(team.getName()));
    sendMessageToOnlinePlayers(
        team.getMembers(),
        TeamMessageUtils.leadershipTransferBroadcastMessage(newLeaderName),
        previousLeader,
        newLeader.getUniqueId());
  }

  public void disbandTeam(String teamName, @NotNull Player leader) {
    Team team = storage.getTeamByName(teamName);
    // Разрешаем роспуск только лидеру существующей команды.
    if (team == null || !team.isLeader(leader.getUniqueId())) return;
    // Сохраняем список участников до удаления, чтобы очистить их отображаемые префиксы.
    List<UUID> members = team.getMembers();
    String leaderName = leader.getName();
    scheduler.cancelDeadline(team);
    // Удаляем команду и уведомляем игроков о сбросе префикса.
    storage.removeTeam(team);
    clearJoinRequests(team, JoinRequestRemovalCause.EXPIRED, leader.getName());
    clearInvitesForTeam(team.getId());
    TeamMessageUtils.sendTeamMessage(
        leader, TeamMessageUtils.teamDisbandedLeaderMessage(team.getName()));
    sendMessageToOnlinePlayers(
        members,
        TeamMessageUtils.teamDisbandedMemberMessage(team.getName(), leaderName),
        leader.getUniqueId());
    for (UUID memberId : members) {
      notifyPrefixUpdate(memberId, null);
    }
    // После уведомления очищаем соответствия игроков и команд.
    for (UUID memberId : members) {
      storage.clearPlayerTeam(memberId);
    }
  }

  public @NotNull RenameResult renameTeam(
      String oldTeamName, String newTeamName, @NotNull Player leader) {
    Team team = storage.getTeamByName(oldTeamName);
    // Проверяем полномочия и уникальность нового имени.
    if (team == null) {
      return RenameResult.TEAM_NOT_FOUND;
    }
    if (!team.isLeader(leader.getUniqueId())) {
      return RenameResult.NOT_LEADER;
    }
    String normalizedNewName = newTeamName == null ? "" : newTeamName.trim();
    Team existingTeam = storage.getTeamByName(normalizedNewName);
    if (existingTeam != null && !existingTeam.getId().equals(team.getId())) {
      return RenameResult.NAME_TAKEN;
    }

    String previousName = team.getName();
    storage.updateTeamName(team, normalizedNewName);
    if (!Objects.equals(previousName, team.getName())) {
      sendMessageToOnlinePlayers(
          team.getMembers(),
          TeamMessageUtils.teamRenamedMemberMessage(previousName, team.getName()),
          leader.getUniqueId());
      refreshPendingInvitesForRenamedTeam(team);
      refreshPendingRequestsForRenamedTeam(team);
    }
    return RenameResult.SUCCESS;
  }

  public void setTeamPrefix(String teamName, String newPrefix, @NotNull Player leader) {
    Team team = storage.getTeamByName(teamName);
    // Изменение префикса доступно только лидеру, при этом валидируем значение.
    if (team == null || !team.isLeader(leader.getUniqueId())) return;
    String normalizedPrefix = newPrefix == null ? "" : newPrefix.trim();
    if (TeamUtils.isPrefixLengthInvalid(normalizedPrefix, pluginConfig, leader)) return;
    team.setPrefix(normalizedPrefix);
    storage.markTeamDirty(team);
    updateTeamMembersPrefixes(team);
    TeamMessageUtils.sendTeamMessage(
        leader, TeamMessageUtils.teamPrefixUpdatedLeaderMessage(normalizedPrefix));
    sendMessageToOnlinePlayers(
        team.getMembers(),
        TeamMessageUtils.teamPrefixUpdatedMemberMessage(normalizedPrefix),
        leader.getUniqueId());
  }

  public void setTeamColor(String teamName, String newColor, @NotNull Player leader) {
    Team team = storage.getTeamByName(teamName);
    // Проверяем права и корректность цвета перед сохранением.
    if (team == null || !team.isLeader(leader.getUniqueId())) return;
    String normalizedColor = newColor == null ? "" : newColor.trim();
    NamedTextColor teamColor = NamedTextColor.NAMES.value(normalizedColor.toLowerCase(Locale.ROOT));
    if (teamColor == null) {
      TeamMessageUtils.sendTeamMessage(
          leader, Component.text("❌ Неверный цвет команды", NamedTextColor.RED));
      return;
    }
    team.setColor(normalizedColor);
    storage.markTeamDirty(team);
    updateTeamMembersPrefixes(team);
    TeamMessageUtils.sendTeamMessage(
        leader, TeamMessageUtils.teamColorUpdatedLeaderMessage(normalizedColor));
    sendMessageToOnlinePlayers(
        team.getMembers(),
        TeamMessageUtils.teamColorUpdatedMemberMessage(normalizedColor),
        leader.getUniqueId());
  }

  public void updatePlayerPrefixes(String teamName) {
    Team team = storage.getTeamByName(teamName);
    // Выполняем синхронизацию префиксов только для существующей команды.
    if (team == null) return;
    updateTeamMembersPrefixes(team);
  }

  public void updateTeamMembersPrefixes(@NotNull Team team) {
    Component prefixComponent = team.getPrefixComponent();
    for (UUID member : team.getMembers()) {
      notifyPrefixUpdate(member, prefixComponent);
    }
  }

  private void notifyPrefixUpdate(@NotNull Player player, @Nullable Component prefix) {
    plugin
        .getServer()
        .getPluginManager()
        .callEvent(new TeamChatListener.PlayerPrefixUpdateEvent(player, prefix));
  }

  private void storeJoinRequest(@NotNull PendingRequest request) {
    storeJoinRequest(request, true);
  }

  private void storeJoinRequest(@NotNull PendingRequest request, boolean persist) {
    joinRequestsByTeam
        .computeIfAbsent(request.getTeamId(), id -> new ConcurrentHashMap<>())
        .put(request.getPlayerId(), request);
    joinRequestsByPlayer
        .computeIfAbsent(request.getPlayerId(), id -> new ConcurrentHashMap<>())
        .put(request.getTeamId(), request);
    if (persist) {
      persistJoinRequests();
    }
  }

  private void persistJoinRequests() {
    storage.saveRequests(snapshotJoinRequests());
  }

  private Collection<PendingRequest> snapshotJoinRequests() {
    if (joinRequestsByTeam.isEmpty()) {
      return List.of();
    }
    List<PendingRequest> requests = new ArrayList<>();
    for (Map<UUID, PendingRequest> teamRequests : joinRequestsByTeam.values()) {
      for (PendingRequest request : teamRequests.values()) {
        if (request != null && !request.isExpired()) {
          requests.add(request);
        }
      }
    }
    return requests.isEmpty() ? List.of() : List.copyOf(requests);
  }

  private @Nullable PendingRequest peekJoinRequest(@NotNull UUID teamId, @NotNull UUID playerId) {
    Map<UUID, PendingRequest> requests = joinRequestsByTeam.get(teamId);
    if (requests == null) {
      return null;
    }
    return requests.get(playerId);
  }

  private @Nullable PendingRequest findJoinRequestForTeam(
      @NotNull Team team, @NotNull String targetName) {
    Map<UUID, PendingRequest> requests = joinRequestsByTeam.get(team.getId());
    if (requests == null) {
      return null;
    }
    List<PendingRequest> expired = new ArrayList<>();
    PendingRequest matched = null;
    for (PendingRequest request : requests.values()) {
      if (request.isExpired()) {
        expired.add(request);
        continue;
      }
      String storedName = request.getPlayerName();
      String resolvedName = resolvePlayerName(request.getPlayerId(), storedName);
      if (storedName.equalsIgnoreCase(targetName) || resolvedName.equalsIgnoreCase(targetName)) {
        matched = request;
        break;
      }
    }
    for (PendingRequest expiredRequest : expired) {
      removeJoinRequest(
          expiredRequest.getTeamId(),
          expiredRequest.getPlayerId(),
          JoinRequestRemovalCause.EXPIRED,
          null,
          null,
          team);
    }
    return matched;
  }

  private @Nullable PendingRequest removeJoinRequest(
      @NotNull UUID teamId,
      @NotNull UUID playerId,
      @NotNull JoinRequestRemovalCause cause,
      @Nullable String actorName,
      @Nullable UUID actorId,
      @Nullable Team providedTeam) {
    Map<UUID, PendingRequest> byTeam = joinRequestsByTeam.get(teamId);
    if (byTeam == null) {
      return null;
    }
    PendingRequest request = byTeam.remove(playerId);
    if (request == null) {
      return null;
    }
    if (byTeam.isEmpty()) {
      joinRequestsByTeam.remove(teamId);
    }
    joinRequestsByPlayer.computeIfPresent(
        playerId,
        (id, requests) -> {
          requests.remove(teamId);
          return requests.isEmpty() ? null : requests;
        });
    Team team = providedTeam;
    if (team == null) {
      team = storage.getTeams().get(teamId);
    }
    notifyJoinRequestRemoval(request, cause, actorName, actorId, team);
    persistJoinRequests();
    return request;
  }

  private void clearJoinRequestsForPlayer(@NotNull UUID playerId, @Nullable UUID actorId) {
    Map<UUID, PendingRequest> requests = joinRequestsByPlayer.remove(playerId);
    if (requests == null || requests.isEmpty()) {
      return;
    }
    for (PendingRequest request : requests.values()) {
      Map<UUID, PendingRequest> byTeam = joinRequestsByTeam.get(request.getTeamId());
      if (byTeam != null) {
        byTeam.remove(playerId);
        if (byTeam.isEmpty()) {
          joinRequestsByTeam.remove(request.getTeamId());
        }
      }
      Team requestTeam = storage.getTeams().get(request.getTeamId());
      notifyJoinRequestRemoval(
          request, JoinRequestRemovalCause.JOINED, null, actorId, requestTeam);
    }
    persistJoinRequests();
  }

  private void clearJoinRequests(
      @NotNull Team team,
      @NotNull JoinRequestRemovalCause cause,
      @Nullable String actorName) {
    Map<UUID, PendingRequest> requests = joinRequestsByTeam.remove(team.getId());
    if (requests == null || requests.isEmpty()) {
      return;
    }
    for (PendingRequest request : requests.values()) {
      joinRequestsByPlayer.computeIfPresent(
          request.getPlayerId(),
          (id, playerRequests) -> {
            playerRequests.remove(request.getTeamId());
            return playerRequests.isEmpty() ? null : playerRequests;
          });
      notifyJoinRequestRemoval(request, cause, actorName, team.getLeaderId(), team);
    }
    persistJoinRequests();
  }

  private void notifyJoinRequestRemoval(
      @NotNull PendingRequest request,
      @NotNull JoinRequestRemovalCause cause,
      @Nullable String actorName,
      @Nullable UUID actorId,
      @Nullable Team team) {
    Player applicant = plugin.getServer().getPlayer(request.getPlayerId());
    Player leader = null;
    UUID leaderId = team != null ? team.getLeaderId() : null;
    if (leaderId != null) {
      leader = plugin.getServer().getPlayer(leaderId);
    }

    switch (cause) {
      case APPROVED -> {
        if (applicant != null) {
          TeamMessageUtils.sendTeamMessage(
              applicant, TeamMessageUtils.joinRequestApprovedPlayerMessage(request.getTeamName()));
        }
        if (leader != null) {
          TeamMessageUtils.sendTeamMessage(
              leader,
              TeamMessageUtils.joinRequestApprovedLeaderMessage(
                  request.getPlayerName(), request.getTeamName()));
        }
      }
      case DENIED -> {
        if (applicant != null) {
          TeamMessageUtils.sendTeamMessage(
              applicant, TeamMessageUtils.joinRequestDeniedPlayerMessage(request.getTeamName()));
        }
        if (leader != null) {
          TeamMessageUtils.sendTeamMessage(
              leader,
              TeamMessageUtils.joinRequestDeniedLeaderMessage(
                  request.getPlayerName(), request.getTeamName(), actorName));
        }
      }
      case CANCELLED -> {
        if (applicant != null) {
          TeamMessageUtils.sendTeamMessage(
              applicant, TeamMessageUtils.joinRequestCancelledPlayerMessage(request.getTeamName()));
        }
        if (leader != null) {
          TeamMessageUtils.sendTeamMessage(
              leader,
              TeamMessageUtils.joinRequestCancelledLeaderMessage(request.getPlayerName(), actorName));
        }
      }
      case EXPIRED -> {
        if (applicant != null) {
          TeamMessageUtils.sendTeamMessage(
              applicant, TeamMessageUtils.joinRequestExpiredPlayerMessage(request.getTeamName()));
        }
        if (leader != null) {
          TeamMessageUtils.sendTeamMessage(
              leader,
              TeamMessageUtils.joinRequestExpiredLeaderMessage(request.getPlayerName(), request.getTeamName()));
        }
      }
      case JOINED -> {
        if (leader != null && actorId != null && !leader.getUniqueId().equals(actorId)) {
          TeamMessageUtils.sendTeamMessage(
              leader,
              TeamMessageUtils.joinRequestAutoClearedLeaderMessage(
                  request.getPlayerName(), request.getTeamName()));
        }
      }
    }
  }

  private Player resolveOnlineLeader(@NotNull Team team) {
    UUID leaderId = team.getLeaderId();
    if (leaderId == null) {
      return null;
    }
    return plugin.getServer().getPlayer(leaderId);
  }

  private enum JoinRequestRemovalCause {
    APPROVED,
    DENIED,
    CANCELLED,
    EXPIRED,
    JOINED
  }

  private @Nullable PendingInvite getActiveInvite(@NotNull UUID teamId, @NotNull UUID playerId) {
    PendingInvite invite = peekInvite(teamId, playerId);
    if (invite == null) {
      return null;
    }
    if (invite.isExpired()) {
      removeInvite(teamId, playerId);
      return null;
    }
    return invite;
  }

  private @Nullable PendingInvite peekInvite(@NotNull UUID teamId, @NotNull UUID playerId) {
    Map<UUID, PendingInvite> invites = invitesByPlayer.get(playerId);
    if (invites == null) {
      return null;
    }
    return invites.get(teamId);
  }

  private @Nullable PendingInvite findInviteForTeam(@NotNull UUID teamId, @NotNull String targetName) {
    Map<UUID, PendingInvite> invites = invitesByTeam.get(teamId);
    if (invites == null) {
      return null;
    }
    List<PendingInvite> expired = new ArrayList<>();
    PendingInvite matched = null;
    for (PendingInvite invite : invites.values()) {
      if (invite.isExpired()) {
        expired.add(invite);
        continue;
      }
      String storedName = invite.getTargetName();
      String resolvedName = resolvePlayerName(invite.getTargetPlayerId(), storedName);
      if (storedName.equalsIgnoreCase(targetName) || resolvedName.equalsIgnoreCase(targetName)) {
        matched = invite;
        break;
      }
    }
    for (PendingInvite invite : expired) {
      removeInvite(invite.getTeamId(), invite.getTargetPlayerId());
    }
    return matched;
  }

  private void storeInvite(@NotNull PendingInvite invite) {
    storeInvite(invite, true);
  }

  private void storeInvite(@NotNull PendingInvite invite, boolean persist) {
    invitesByPlayer
        .computeIfAbsent(invite.getTargetPlayerId(), id -> new ConcurrentHashMap<>())
        .put(invite.getTeamId(), invite);
    invitesByTeam
        .computeIfAbsent(invite.getTeamId(), id -> new ConcurrentHashMap<>())
        .put(invite.getTargetPlayerId(), invite);
    if (persist) {
      persistInvites();
    }
  }

  private void persistInvites() {
    storage.saveInvites(snapshotInvites());
  }

  private Collection<PendingInvite> snapshotInvites() {
    if (invitesByTeam.isEmpty()) {
      return List.of();
    }
    List<PendingInvite> invites = new ArrayList<>();
    for (Map<UUID, PendingInvite> teamInvites : invitesByTeam.values()) {
      for (PendingInvite invite : teamInvites.values()) {
        if (invite != null && !invite.isExpired()) {
          invites.add(invite);
        }
      }
    }
    return invites.isEmpty() ? List.of() : List.copyOf(invites);
  }

  private boolean removeInvite(@NotNull UUID teamId, @NotNull UUID playerId) {
    boolean changed = false;
    Map<UUID, PendingInvite> byPlayer = invitesByPlayer.get(playerId);
    if (byPlayer != null) {
      changed = byPlayer.remove(teamId) != null || changed;
      if (byPlayer.isEmpty()) {
        invitesByPlayer.remove(playerId);
      }
    }
    Map<UUID, PendingInvite> byTeam = invitesByTeam.get(teamId);
    if (byTeam != null) {
      changed = byTeam.remove(playerId) != null || changed;
      if (byTeam.isEmpty()) {
        invitesByTeam.remove(teamId);
      }
    }
    if (changed) {
      persistInvites();
    }
    return changed;
  }

  private void clearInvitesForPlayer(@NotNull UUID playerId) {
    Map<UUID, PendingInvite> invites = invitesByPlayer.remove(playerId);
    if (invites == null) {
      return;
    }
    for (PendingInvite invite : invites.values()) {
      Map<UUID, PendingInvite> teamInvites = invitesByTeam.get(invite.getTeamId());
      if (teamInvites != null) {
        teamInvites.remove(playerId);
        if (teamInvites.isEmpty()) {
          invitesByTeam.remove(invite.getTeamId());
        }
      }
    }
    persistInvites();
  }

  private void clearInvitesForTeam(@NotNull UUID teamId) {
    Map<UUID, PendingInvite> invites = invitesByTeam.remove(teamId);
    if (invites == null) {
      return;
    }
    for (PendingInvite invite : invites.values()) {
      Map<UUID, PendingInvite> playerInvites = invitesByPlayer.get(invite.getTargetPlayerId());
      if (playerInvites != null) {
        playerInvites.remove(teamId);
        if (playerInvites.isEmpty()) {
          invitesByPlayer.remove(invite.getTargetPlayerId());
        }
      }
    }
    persistInvites();
  }

  private void refreshPendingInvitesForRenamedTeam(@NotNull Team team) {
    Map<UUID, PendingInvite> invites = invitesByTeam.get(team.getId());
    if (invites == null || invites.isEmpty()) {
      return;
    }

    List<PendingInvite> existingInvites = new ArrayList<>(invites.values());
    invitesByTeam.put(team.getId(), new ConcurrentHashMap<>());
    for (PendingInvite invite : existingInvites) {
      invitesByPlayer.computeIfPresent(
          invite.getTargetPlayerId(),
          (playerId, playerInvites) -> {
            playerInvites.remove(team.getId());
            return playerInvites.isEmpty() ? null : playerInvites;
          });
    }

    for (PendingInvite invite : existingInvites) {
      PendingInvite updatedInvite = invite.withUpdatedNames(team.getName(), invite.getTargetName());
      storeInvite(updatedInvite, false);
      Player target = plugin.getServer().getPlayer(updatedInvite.getTargetPlayerId());
      if (target != null) {
        String acceptCommand = "/team accept " + updatedInvite.getTeamName();
        String declineCommand = "/team decline " + updatedInvite.getTeamName();
        TeamMessageUtils.sendTeamMessage(
            target,
            TeamMessageUtils.inviteReceivedMessage(
                updatedInvite.getTeamName(),
                updatedInvite.getInviterName(),
                updatedInvite.getExpiresAt(),
                acceptCommand,
                declineCommand));
        TeamMessageUtils.sendTeamMessage(
            target,
            TeamMessageUtils.inviteListEntry(updatedInvite, acceptCommand, declineCommand));
      }
    }
    if (!existingInvites.isEmpty()) {
      persistInvites();
    }
  }

  private void refreshPendingRequestsForRenamedTeam(@NotNull Team team) {
    Map<UUID, PendingRequest> requests = joinRequestsByTeam.get(team.getId());
    if (requests == null || requests.isEmpty()) {
      return;
    }

    List<PendingRequest> existingRequests = new ArrayList<>(requests.values());
    joinRequestsByTeam.put(team.getId(), new ConcurrentHashMap<>());
    for (PendingRequest request : existingRequests) {
      joinRequestsByPlayer.computeIfPresent(
          request.getPlayerId(),
          (playerId, playerRequests) -> {
            playerRequests.remove(team.getId());
            return playerRequests.isEmpty() ? null : playerRequests;
          });
    }

    for (PendingRequest request : existingRequests) {
      storeJoinRequest(request.withTeamName(team.getName()), false);
    }
    if (!existingRequests.isEmpty()) {
      persistJoinRequests();
    }
  }

  private void notifyAdmins(@Nullable Component message) {
    if (message == null || !pluginConfig.shouldNotifyAdmins()) {
      return;
    }
    plugin.getServer().getOnlinePlayers().stream()
        .filter(player -> player.hasPermission("mypurpurplugin.admin"))
        .forEach(player -> TeamMessageUtils.sendTeamMessage(player, message));
  }

  private void notifyPrefixUpdate(@NotNull UUID playerId, @Nullable Component prefix) {
    Player onlinePlayer = plugin.getServer().getPlayer(playerId);
    if (onlinePlayer != null) {
      notifyPrefixUpdate(onlinePlayer, prefix);
    }
  }

  private void sendMessageToOnlinePlayers(
      @NotNull Collection<UUID> recipients, @NotNull Component message, UUID... excludedPlayers) {
    Set<UUID> excluded = Collections.emptySet();
    if (excludedPlayers != null && excludedPlayers.length > 0) {
      excluded = new HashSet<>(Arrays.asList(excludedPlayers));
    }
    for (UUID memberId : recipients) {
      if (excluded.contains(memberId)) {
        continue;
      }
      Player member = plugin.getServer().getPlayer(memberId);
      if (member != null) {
        TeamMessageUtils.sendTeamMessage(member, message);
      }
    }
  }

  private UUID findMemberIdByName(@NotNull Team team, @NotNull String targetName) {
    for (UUID memberId : team.getMembers()) {
      OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(memberId);
      if (offlinePlayer != null) {
        String currentName = offlinePlayer.getName();
        if (currentName != null && currentName.equalsIgnoreCase(targetName)) {
          return memberId;
        }
      }
    }
    return null;
  }
}

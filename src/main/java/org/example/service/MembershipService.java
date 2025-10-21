package org.example.service;

import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.example.config.PluginConfig;
import org.example.listener.TeamChatListener;
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
    storage.markTeamDirty(team);
    TeamMessageUtils.sendTeamMessage(
        player, Component.text("✅ Вы вступили в команду", NamedTextColor.GREEN));
    scheduler.evaluateTeam(team);
    updateTeamMembersPrefixes(team);
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

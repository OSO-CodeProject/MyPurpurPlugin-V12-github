package org.example.service;

import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
    // Убеждаемся, что имя и лидер свободны, чтобы не создавать дубликатов команд.
    if (storage.getPlayerTeam(leader) != null || storage.getTeamByName(teamName) != null) {
      TeamMessageUtils.sendTeamMessage(leader, TeamMessageUtils.teamAlreadyExistsMessage(teamName));
      return;
    }
    // Проверяем, что игрок выбрал поддерживаемый цвет для корректного отображения.
    NamedTextColor teamColor = NamedTextColor.NAMES.value(color.toLowerCase());
    if (teamColor == null) {
      TeamMessageUtils.sendTeamMessage(
          leader, Component.text("❌ Неверный цвет команды", NamedTextColor.RED));
      return;
    }
    // Валидируем длину имени и префикса согласно настройкам плагина.
    if (TeamUtils.isTeamNameLengthInvalid(teamName, pluginConfig, leader)
        || TeamUtils.isPrefixLengthInvalid(prefix, pluginConfig, leader)) {
      return;
    }
    // Создаём запись команды и связываем лидера с новой структурой.
    Team team = new Team(teamName, leader.getName(), prefix, color);
    storage.addTeam(team);
    storage.getPlayerTeams().put(leader.getName(), team.getId());
    TeamMessageUtils.sendTeamMessage(
        leader, Component.text("✅ Команда создана", NamedTextColor.GREEN));
    scheduler.enforceTeamSizes();
  }

  public void addPlayerToTeam(String teamName, @NotNull Player player) {
    Team team = storage.getTeamByName(teamName);
    // Если команда не найдена — сообщаем игроку и прекращаем обработку.
    if (team == null) {
      TeamMessageUtils.sendTeamMessage(player, TeamMessageUtils.teamDoesNotExistMessage(teamName));
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
    team.addMember(player.getName());
    storage.getPlayerTeams().put(player.getName(), team.getId());
    storage.markTeamDirty(team);
    TeamMessageUtils.sendTeamMessage(
        player, Component.text("✅ Вы вступили в команду", NamedTextColor.GREEN));
    scheduler.enforceTeamSizes();
    updateTeamMembersPrefixes(team);
  }

  public void removePlayerFromTeam(String teamName, @NotNull Player player) {
    Team team = storage.getTeamByName(teamName);
    // Проводим проверки существования команды и членства игрока.
    if (team == null) return;
    if (!team.hasMember(player.getName()) && !team.isLeader(player.getName())) return;
    // Удаляем игрока и очищаем обратные ссылки.
    team.removeMember(player.getName());
    storage.getPlayerTeams().remove(player.getName());
    boolean removedTeam = false;
    // Если ушедший игрок был лидером, либо закрываем команду, либо назначаем нового.
    if (team.isLeader(player.getName())) {
      if (team.getMembers().isEmpty()) {
        storage.removeTeam(team);
        removedTeam = true;
      } else {
        team.setLeader(team.getMembers().get(0));
      }
    }
    // Отмечаем изменившуюся команду и пересчитываем дедлайны.
    if (!removedTeam) {
      storage.markTeamDirty(team);
    }
    scheduler.enforceTeamSizes();
    notifyPrefixUpdate(player, null);
    if (!removedTeam) {
      updateTeamMembersPrefixes(team);
    }
  }

  public void kickPlayerFromTeam(
      String teamName, @NotNull Player leader, @NotNull String targetName) {
    Team team = storage.getTeamByName(teamName);
    // Проверяем право лидера на это действие и наличие цели в команде.
    if (team == null || !team.isLeader(leader.getName())) return;
    if (!team.hasMember(targetName)) return;
    // Удаляем участника и фиксируем изменения.
    team.removeMember(targetName);
    storage.getPlayerTeams().remove(targetName);
    storage.markTeamDirty(team);
    scheduler.enforceTeamSizes();
    notifyPrefixUpdate(targetName, null);
    updateTeamMembersPrefixes(team);
  }

  public void transferLeadership(
      String teamName, @NotNull Player leader, @NotNull Player newLeader) {
    Team team = storage.getTeamByName(teamName);
    // Убеждаемся, что изменение лидерства инициирует действующий лидер.
    if (team == null || !team.isLeader(leader.getName())) return;
    // Назначать лидером можно только участника команды.
    if (!team.hasMember(newLeader.getName())) return;
    team.setLeader(newLeader.getName());
    storage.markTeamDirty(team);
  }

  public void disbandTeam(String teamName, @NotNull Player leader) {
    Team team = storage.getTeamByName(teamName);
    // Разрешаем роспуск только лидеру существующей команды.
    if (team == null || !team.isLeader(leader.getName())) return;
    // Сохраняем список участников до удаления, чтобы очистить их отображаемые префиксы.
    List<String> members = team.getMembers();
    // Удаляем команду и уведомляем игроков о сбросе префикса.
    storage.removeTeam(team);
    for (String memberName : members) {
      notifyPrefixUpdate(memberName, null);
    }
    // После уведомления очищаем соответствия игроков и команд.
    for (String memberName : members) {
      storage.getPlayerTeams().remove(memberName);
    }
    scheduler.enforceTeamSizes();
  }

  public void renameTeam(String oldTeamName, String newTeamName, @NotNull Player leader) {
    Team team = storage.getTeamByName(oldTeamName);
    // Проверяем полномочия и уникальность нового имени.
    if (team == null || !team.isLeader(leader.getName())) return;
    if (storage.getTeamByName(newTeamName) != null) return;
    storage.updateTeamName(team, newTeamName);
  }

  public void setTeamPrefix(String teamName, String newPrefix, @NotNull Player leader) {
    Team team = storage.getTeamByName(teamName);
    // Изменение префикса доступно только лидеру, при этом валидируем значение.
    if (team == null || !team.isLeader(leader.getName())) return;
    if (TeamUtils.isPrefixLengthInvalid(newPrefix, pluginConfig, leader)) return;
    team.setPrefix(newPrefix);
    storage.markTeamDirty(team);
    updateTeamMembersPrefixes(team);
  }

  public void setTeamColor(String teamName, String newColor, @NotNull Player leader) {
    Team team = storage.getTeamByName(teamName);
    // Проверяем права и корректность цвета перед сохранением.
    if (team == null || !team.isLeader(leader.getName())) return;
    NamedTextColor teamColor = NamedTextColor.NAMES.value(newColor.toLowerCase());
    if (teamColor == null) {
      TeamMessageUtils.sendTeamMessage(
          leader, Component.text("❌ Неверный цвет команды", NamedTextColor.RED));
      return;
    }
    team.setColor(newColor);
    storage.markTeamDirty(team);
    updateTeamMembersPrefixes(team);
  }

  public void updatePlayerPrefixes(String teamName) {
    Team team = storage.getTeamByName(teamName);
    // Выполняем синхронизацию префиксов только для существующей команды.
    if (team == null) return;
    updateTeamMembersPrefixes(team);
  }

  private void updateTeamMembersPrefixes(@NotNull Team team) {
    Component prefixComponent = team.getPrefixComponent();
    for (String member : team.getMembers()) {
      notifyPrefixUpdate(member, prefixComponent);
    }
  }

  private void notifyPrefixUpdate(@NotNull Player player, @Nullable Component prefix) {
    plugin
        .getServer()
        .getPluginManager()
        .callEvent(new TeamChatListener.PlayerPrefixUpdateEvent(player, prefix));
  }

  private void notifyPrefixUpdate(@NotNull String playerName, @Nullable Component prefix) {
    Player onlinePlayer = plugin.getServer().getPlayer(playerName);
    if (onlinePlayer != null) {
      notifyPrefixUpdate(onlinePlayer, prefix);
    }
  }
}

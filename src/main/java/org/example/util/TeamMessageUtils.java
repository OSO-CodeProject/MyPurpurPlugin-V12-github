package org.example.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/** Утилитарный класс для работы с сообщениями команд. */
public class TeamMessageUtils {

  /**
   * Отправляет сообщение игроку.
   *
   * @param player Игрок, которому отправляется сообщение
   * @param message Сообщение для отправки
   */
  public static void sendTeamMessage(Player player, Component message) {
    if (player != null) {
      player.sendMessage(message);
    }
  }

  /**
   * Формирует сообщение об ошибке, если команда уже существует.
   *
   * @param teamName Название команды
   * @return Форматированное сообщение об ошибке
   */
  public static Component teamAlreadyExistsMessage(String teamName) {
    return Component.text("❌ Команда ", NamedTextColor.RED)
        .append(Component.text(teamName, NamedTextColor.WHITE))
        .append(Component.text(" уже существует ", NamedTextColor.RED))
        .append(Component.text("!", NamedTextColor.RED));
  }

  /**
   * Формирует сообщение об ошибке, если команда не существует.
   *
   * @param teamName Название команды
   * @return Форматированное сообщение об ошибке
   */
  public static Component teamDoesNotExistMessage(String teamName) {
    String name = (teamName != null) ? teamName : "неизвестная команда";
    return Component.text("❌ Команда ", NamedTextColor.RED)
        .append(Component.text(name, NamedTextColor.WHITE))
        .append(Component.text(" не существует!", NamedTextColor.RED));
  }

  /**
   * Формирует сообщение о том, что игрок не состоит ни в одной команде.
   *
   * @return сообщение об ошибке
   */
  public static Component playerNotInAnyTeamMessage() {
    return Component.text("❌ Вы не состоите в команде!", NamedTextColor.RED);
  }

  /**
   * Формирует сообщение о том, что действие доступно только лидеру команды.
   *
   * @return сообщение об ошибке
   */
  public static Component notTeamLeaderMessage() {
    return Component.text("❌ Это действие доступно только лидеру команды!", NamedTextColor.RED);
  }

  /**
   * Формирует сообщение об ошибке, если запрошенный игрок не найден.
   *
   * @param playerName имя игрока
   * @return сообщение об ошибке
   */
  public static Component playerNotFoundMessage(String playerName) {
    return Component.text("❌ Игрок ", NamedTextColor.RED)
        .append(Component.text(playerName, NamedTextColor.WHITE))
        .append(Component.text(" не найден!", NamedTextColor.RED));
  }

  /**
   * Сообщает, что команда принимает игроков только по приглашению.
   *
   * @return информационное сообщение
   */
  public static Component joinInviteOnlyMessage() {
    return Component.text(
        "ℹ️ Эта команда принимает новых участников только по приглашению лидера.",
        NamedTextColor.YELLOW);
  }

  /**
   * Сообщает игроку, что заявка на вступление отправлена лидеру.
   *
   * @param teamName название команды
   * @return информационное сообщение
   */
  public static Component joinRequestSentMessage(String teamName) {
    String name = (teamName == null || teamName.isBlank()) ? "команду" : teamName;
    return Component.text("ℹ️ Заявка на вступление в команду ", NamedTextColor.YELLOW)
        .append(Component.text(name, NamedTextColor.WHITE))
        .append(Component.text(" отправлена лидеру.", NamedTextColor.YELLOW));
  }

  /**
   * Сообщает игроку, что заявка на вступление уже существует.
   *
   * @param teamName название команды
   * @return информационное сообщение
   */
  public static Component joinRequestAlreadySentMessage(String teamName) {
    String name = (teamName == null || teamName.isBlank()) ? "команду" : teamName;
    return Component.text("ℹ️ Вы уже отправили заявку в команду ", NamedTextColor.YELLOW)
        .append(Component.text(name, NamedTextColor.WHITE))
        .append(Component.text(".", NamedTextColor.YELLOW));
  }

  /**
   * Уведомляет лидера о новой заявке на вступление.
   *
   * @param playerName имя игрока, отправившего заявку
   * @param teamName название команды
   * @return информационное сообщение
   */
  public static Component joinRequestReceivedLeaderMessage(String playerName, String teamName) {
    String candidate = (playerName == null || playerName.isBlank()) ? "Неизвестный игрок" : playerName;
    String name = (teamName == null || teamName.isBlank()) ? "вашу команду" : teamName;
    return Component.text("ℹ️ Игрок ", NamedTextColor.YELLOW)
        .append(Component.text(candidate, NamedTextColor.WHITE))
        .append(Component.text(" хочет вступить в команду ", NamedTextColor.YELLOW))
        .append(Component.text(name, NamedTextColor.WHITE))
        .append(Component.text(".", NamedTextColor.YELLOW));
  }

  /**
   * Формирует сообщение об ошибке, если игрок уже состоит в команде.
   *
   * @param teamName Название команды
   * @param prefixComponent Префикс команды
   * @return Форматированное сообщение об ошибке
   */
  public static Component playerAlreadyInTeamMessage(String teamName, Component prefixComponent) {
    return Component.text("❌ Вы уже состоите в команде ", NamedTextColor.RED)
        .append(prefixComponent)
        .append(Component.text(teamName, NamedTextColor.WHITE))
        .append(Component.text(" !", NamedTextColor.RED));
  }

  /**
   * Формирует сообщение об ошибке, если игрок не состоит в команде.
   *
   * @param teamName Название команды
   * @param prefixComponent Префикс команды
   * @return Форматированное сообщение об ошибке
   */
  public static Component playerNotInTeamMessage(String teamName, Component prefixComponent) {
    return Component.text("❌ Вы не состоите в команде ", NamedTextColor.RED)
        .append(prefixComponent)
        .append(Component.text(teamName, NamedTextColor.WHITE))
        .append(Component.text(" !", NamedTextColor.RED));
  }

  /**
   * Формирует предупреждение о необходимости сократить команду.
   *
   * @param max новый лимит участников
   * @param minutes время на сокращение
   * @param excess количество лишних участников
   * @return сообщение-предупреждение
   */
  public static Component deadlineWarningMessage(int max, int minutes, int excess) {
    return Component.text("Максимум игроков уменьшен до ", NamedTextColor.YELLOW)
        .append(Component.text(max, NamedTextColor.WHITE))
        .append(Component.text(". У вас ", NamedTextColor.YELLOW))
        .append(Component.text(minutes + " мин", NamedTextColor.WHITE))
        .append(Component.text(", чтобы исключить ", NamedTextColor.YELLOW))
        .append(Component.text(excess + " участника(ов)", NamedTextColor.WHITE))
        .append(Component.text(".", NamedTextColor.YELLOW));
  }

  /**
   * Сообщение после принудительного удаления участников.
   *
   * @param removed количество удалённых участников
   * @return сообщение для лидера
   */
  public static Component forcedRemovalMessage(int removed) {
    return Component.text("Из вашей команды удалено ", NamedTextColor.RED)
        .append(Component.text(removed + " участника(ов)", NamedTextColor.WHITE))
        .append(Component.text(" из-за превышения лимита.", NamedTextColor.RED));
  }

  /**
   * Сообщение с оставшимся временем для удаления лишних участников.
   *
   * @param minutes оставшиеся минуты
   * @param seconds оставшиеся секунды
   * @param excess количество лишних участников
   * @return сообщение для лидера
   */
  public static Component deadlineRemainingMessage(long minutes, long seconds, int excess) {
    return Component.text("Осталось ", NamedTextColor.YELLOW)
        .append(Component.text(String.format("%d:%02d", minutes, seconds), NamedTextColor.WHITE))
        .append(Component.text(" чтобы исключить ", NamedTextColor.YELLOW))
        .append(Component.text(excess + " участника(ов)", NamedTextColor.WHITE))
        .append(Component.text(".", NamedTextColor.YELLOW));
  }

  public static Component teamDisbandedLeaderMessage(String teamName) {
    return Component.text("✅ Команда ", NamedTextColor.GREEN)
        .append(Component.text(teamName, NamedTextColor.WHITE))
        .append(Component.text(" распущена.", NamedTextColor.GREEN));
  }

  public static Component teamDisbandedMemberMessage(String teamName, String leaderName) {
    Component base =
        Component.text("ℹ️ Команда ", NamedTextColor.YELLOW)
            .append(Component.text(teamName, NamedTextColor.WHITE))
            .append(Component.text(" была распущена", NamedTextColor.YELLOW));
    if (leaderName != null && !leaderName.isBlank()) {
      base =
          base.append(Component.text(" лидером ", NamedTextColor.YELLOW))
              .append(Component.text(leaderName, NamedTextColor.WHITE));
    }
    return base.append(Component.text(".", NamedTextColor.YELLOW));
  }

  public static Component leadershipTransferOutgoingMessage(String newLeaderName) {
    return Component.text("✅ Вы передали лидерство игроку ", NamedTextColor.GREEN)
        .append(Component.text(newLeaderName, NamedTextColor.WHITE))
        .append(Component.text(".", NamedTextColor.GREEN));
  }

  public static Component leadershipTransferIncomingMessage(String teamName) {
    return Component.text("ℹ️ Вы стали лидером команды ", NamedTextColor.YELLOW)
        .append(Component.text(teamName, NamedTextColor.WHITE))
        .append(Component.text(".", NamedTextColor.YELLOW));
  }

  public static Component leadershipTransferBroadcastMessage(String newLeaderName) {
    return Component.text("ℹ️ Новый лидер команды — ", NamedTextColor.YELLOW)
        .append(Component.text(newLeaderName, NamedTextColor.WHITE))
        .append(Component.text(".", NamedTextColor.YELLOW));
  }

  public static Component memberKickedLeaderMessage(String targetName) {
    return Component.text("✅ Игрок ", NamedTextColor.GREEN)
        .append(Component.text(targetName, NamedTextColor.WHITE))
        .append(Component.text(" исключён из команды.", NamedTextColor.GREEN));
  }

  public static Component memberKickedTargetMessage(String teamName, String leaderName) {
    Component base =
        Component.text("❌ Вас исключили из команды ", NamedTextColor.RED)
            .append(Component.text(teamName, NamedTextColor.WHITE));
    if (leaderName != null && !leaderName.isBlank()) {
      base =
          base.append(Component.text(" лидером ", NamedTextColor.RED))
              .append(Component.text(leaderName, NamedTextColor.WHITE));
    }
    return base.append(Component.text(".", NamedTextColor.RED));
  }

  public static Component memberKickedBroadcastMessage(String targetName) {
    return Component.text("ℹ️ Игрок ", NamedTextColor.YELLOW)
        .append(Component.text(targetName, NamedTextColor.WHITE))
        .append(Component.text(" был исключён из команды.", NamedTextColor.YELLOW));
  }

  public static Component memberLeftSelfMessage(String teamName) {
    return Component.text("ℹ️ Вы покинули команду ", NamedTextColor.YELLOW)
        .append(Component.text(teamName, NamedTextColor.WHITE))
        .append(Component.text(".", NamedTextColor.YELLOW));
  }

  public static Component memberLeftBroadcastMessage(String playerName) {
    return Component.text("ℹ️ Игрок ", NamedTextColor.YELLOW)
        .append(Component.text(playerName, NamedTextColor.WHITE))
        .append(Component.text(" покинул команду.", NamedTextColor.YELLOW));
  }

  public static Component teamPrefixUpdatedLeaderMessage(String prefix) {
    return Component.text("✅ Префикс команды обновлён", NamedTextColor.GREEN)
        .append(Component.text(prefixDisplay(prefix), NamedTextColor.WHITE));
  }

  public static Component teamPrefixUpdatedMemberMessage(String prefix) {
    return Component.text("ℹ️ Префикс команды обновлён", NamedTextColor.YELLOW)
        .append(Component.text(prefixDisplay(prefix), NamedTextColor.WHITE));
  }

  public static Component teamRenamedLeaderMessage(String oldName, String newName) {
    return Component.text("✅ Команда ", NamedTextColor.GREEN)
        .append(nameComponent(oldName))
        .append(Component.text(" переименована в ", NamedTextColor.GREEN))
        .append(nameComponent(newName))
        .append(Component.text(".", NamedTextColor.GREEN));
  }

  public static Component teamRenamedMemberMessage(String oldName, String newName) {
    return Component.text("ℹ️ Команда ", NamedTextColor.YELLOW)
        .append(nameComponent(oldName))
        .append(Component.text(" переименована в ", NamedTextColor.YELLOW))
        .append(nameComponent(newName))
        .append(Component.text(".", NamedTextColor.YELLOW));
  }

  public static Component teamColorUpdatedLeaderMessage(String color) {
    return Component.text("✅ Цвет команды обновлён", NamedTextColor.GREEN)
        .append(Component.text(valueDisplay(color), NamedTextColor.WHITE));
  }

  public static Component teamColorUpdatedMemberMessage(String color) {
    return Component.text("ℹ️ Цвет команды обновлён", NamedTextColor.YELLOW)
        .append(Component.text(valueDisplay(color), NamedTextColor.WHITE));
  }

  private static String prefixDisplay(String prefix) {
    if (prefix == null || prefix.isEmpty()) {
      return " (пустой)";
    }
    return " — \"" + prefix + "\"";
  }

  private static String valueDisplay(String value) {
    if (value == null || value.isEmpty()) {
      return " (пустое значение)";
    }
    return " — \"" + value + "\"";
  }

  private static Component nameComponent(String name) {
    String value = (name == null || name.isEmpty()) ? "(без названия)" : name;
    return Component.text(value, NamedTextColor.WHITE);
  }
}

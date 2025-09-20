package org.example.util;

import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.example.config.PluginConfig;
import org.example.service.TeamService;

/** Утилитарный класс для работы с командами и их визуальными элементами. */
public class TeamUtils {

  /**
   * Создаёт компонент префикса команды с указанным текстом и цветом.
   *
   * @param prefix Префикс команды
   * @param color Цвет префикса
   * @return Компонент префикса
   */
  public static Component createPrefixComponent(String prefix, NamedTextColor color) {
    return Component.text("[" + prefix + "] ", color);
  }

  /**
   * Уведомляет всех членов команды о сообщении, исключая указанных игроков.
   *
   * @param teamName Название команды
   * @param teamManager Менеджер команд
   * @param message Сообщение для отправки
   * @param excludedPlayers Игроки, которых нужно исключить из уведомления (может быть null)
   */
  public static void notifyTeamMembers(
      String teamName, TeamService teamManager, Component message, Set<UUID> excludedPlayers) {
    for (UUID memberId : teamManager.getTeamMembers(teamName)) {
      if (excludedPlayers != null && excludedPlayers.contains(memberId)) {
        continue; // Пропускаем исключённых игроков
      }
      Player member = teamManager.getPlugin().getServer().getPlayer(memberId);
      if (member != null) {
        TeamMessageUtils.sendTeamMessage(member, message);
      }
    }
  }

  /**
   * Проверяет, является ли длина префикса команды недопустимой.
   *
   * @param prefix Префикс для проверки
   * @param pluginConfig Конфигурация плагина
   * @param player Игрок, которому отправляется сообщение об ошибке
   * @return true, если длина недопустима, иначе false
   */
  public static boolean isPrefixLengthInvalid(
      String prefix, PluginConfig pluginConfig, Player player) {
    int minPrefixLength = pluginConfig.getMinPrefixLength();
    int maxPrefixLength = pluginConfig.getMaxPrefixLength();
    if (prefix.length() < minPrefixLength) {
      TeamMessageUtils.sendTeamMessage(
          player,
          Component.text(
              "❌ Префикс слишком короткий!\nМинимальная длина — " + minPrefixLength + " символов.",
              NamedTextColor.RED));
      return true;
    }
    if (prefix.length() > maxPrefixLength) {
      TeamMessageUtils.sendTeamMessage(
          player,
          Component.text(
              "❌ Префикс слишком длинный!\nМаксимальная длина — " + maxPrefixLength + " символов.",
              NamedTextColor.RED));
      return true;
    }
    return false;
  }

  /**
   * Проверяет, является ли длина названия команды недопустимой.
   *
   * @param teamName Название команды для проверки
   * @param pluginConfig Конфигурация плагина
   * @param player Игрок, которому отправляется сообщение об ошибке
   * @return true, если длина недопустима, иначе false
   */
  public static boolean isTeamNameLengthInvalid(
      String teamName, PluginConfig pluginConfig, Player player) {
    int minTeamNameLength = pluginConfig.getMinTeamNameLength();
    int maxTeamNameLength = pluginConfig.getMaxTeamNameLength();
    if (teamName.length() < minTeamNameLength) {
      TeamMessageUtils.sendTeamMessage(
          player,
          Component.text(
              "❌ Название команды слишком короткое!\nМинимальная длина — "
                  + minTeamNameLength
                  + " символов.",
              NamedTextColor.RED));
      return true;
    }
    if (teamName.length() > maxTeamNameLength) {
      TeamMessageUtils.sendTeamMessage(
          player,
          Component.text(
              "❌ Название команды слишком длинное!\nМаксимальная длина — "
                  + maxTeamNameLength
                  + " символов.",
              NamedTextColor.RED));
      return true;
    }
    return false;
  }
}

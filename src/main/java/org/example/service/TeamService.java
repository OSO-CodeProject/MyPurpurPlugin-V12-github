package org.example.service;

import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.example.config.PluginConfig;
import org.jetbrains.annotations.NotNull;

/** Сервис для управления командами. */
public interface TeamService {

  /**
   * Создаёт новую команду с указанным названием, префиксом и цветом.
   *
   * @param teamName Название команды
   * @param prefix Префикс команды
   * @param color Цвет команды
   * @param leader Лидер команды
   */
  void createTeam(String teamName, String prefix, String color, @NotNull Player leader);

  /**
   * Добавляет игрока в указанную команду.
   *
   * @param teamName Название команды
   * @param player Игрок, которого нужно добавить
   */
  void addPlayerToTeam(String teamName, @NotNull Player player);

  /**
   * Удаляет игрока из указанной команды.
   *
   * @param teamName Название команды
   * @param player Игрок, которого нужно удалить
   */
  void removePlayerFromTeam(String teamName, @NotNull Player player);

  /**
   * Исключает игрока из команды по приказу лидера.
   *
   * @param teamName Название команды
   * @param leader Лидер команды, выполняющий исключение
   * @param targetName Имя игрока, исключаемого из команды
   */
  void kickPlayerFromTeam(String teamName, @NotNull Player leader, @NotNull String targetName);

  /**
   * Передаёт лидерство в команде от одного игрока другому.
   *
   * @param teamName Название команды
   * @param leader Текущий лидер команды
   * @param newLeader Новый лидер команды
   */
  void transferLeadership(String teamName, @NotNull Player leader, @NotNull Player newLeader);

  /**
   * Распускает указанную команду.
   *
   * @param teamName Название команды
   * @param leader Лидер команды, выполняющий роспуск
   */
  void disbandTeam(String teamName, @NotNull Player leader);

  /**
   * Переименовывает команду.
   *
   * @param oldTeamName Старое название команды
   * @param newTeamName Новое название команды
   * @param leader Лидер команды, выполняющий переименование
   */
  void renameTeam(String oldTeamName, String newTeamName, @NotNull Player leader);

  /**
   * Устанавливает новый префикс для команды.
   *
   * @param teamName Название команды
   * @param newPrefix Новый префикс команды
   * @param leader Лидер команды, выполняющий изменение
   */
  void setTeamPrefix(String teamName, String newPrefix, @NotNull Player leader);

  /**
   * Устанавливает новый цвет для команды.
   *
   * @param teamName Название команды
   * @param newColor Новый цвет команды
   * @param leader Лидер команды, выполняющий изменение
   */
  void setTeamColor(String teamName, String newColor, @NotNull Player leader);

  /**
   * Обновляет префиксы игроков в указанной команде.
   *
   * @param teamName Название команды
   */
  void updatePlayerPrefixes(String teamName);

  /**
   * Возвращает название команды, в которой состоит игрок.
   *
   * @param player Игрок
   * @return Название команды или null, если игрок не состоит в команде
   */
  String getPlayerTeam(@NotNull Player player);

  /**
   * Получает список участников указанной команды.
   *
   * @param teamName Название команды
   * @return Список участников команды
   */
  @NotNull
  List<String> getTeamMembers(String teamName);

  /**
   * Получает список названий всех команд.
   *
   * @return Список названий команд
   */
  @NotNull
  List<String> getTeamNames();

  /**
   * Получает префикс указанной команды.
   *
   * @param teamName Название команды
   * @return Префикс команды
   */
  String getTeamPrefix(String teamName);

  /**
   * Получает цвет указанной команды.
   *
   * @param teamName Название команды
   * @return Цвет команды
   */
  @NotNull
  NamedTextColor getTeamColor(String teamName);

  /**
   * Получает имя лидера указанной команды.
   *
   * @param teamName Название команды
   * @return Имя лидера команды или null, если команда не существует
   */
  String getTeamLeader(String teamName);

  /**
   * Получает экземпляр плагина.
   *
   * @return Экземпляр плагина
   */
  @NotNull
  JavaPlugin getPlugin();

  /**
   * Возвращает конфигурацию плагина.
   *
   * @return конфигурация плагина
   */
  @NotNull
  PluginConfig getPluginConfig();

  /**
   * Определяет, применяется ли ограничение по количеству участников при перезагрузке.
   *
   * @return true, если ограничение активно
   */
  boolean isEnforceMaxMembersOnReload();

  /**
   * Проверяет, активен ли льготный период перед удалением лишних участников.
   *
   * @return true, если льготный период включён
   */
  boolean isGracePeriodEnabled();

  /** Перезагружает конфигурацию плагина и применяет изменения. */
  void reloadConfig();

  /**
   * Получает UUID команды по её названию.
   *
   * @param teamName Название команды
   * @return UUID команды или null, если команда не существует
   */
  UUID getTeamIdByName(String teamName);

  /**
   * Возвращает дедлайн команды в миллисекундах или null, если он не установлен.
   *
   * @param teamName название команды
   * @return значение дедлайна или null
   */
  Long getTeamDeadline(String teamName);

  /** Выполняет очистку ресурсов сервиса команд. */
  default void shutdown() {}
}

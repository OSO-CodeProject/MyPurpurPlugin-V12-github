package org.example.service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.example.config.JoinMode;
import org.example.config.PluginConfig;
import org.example.model.PendingInvite;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  default void removePlayerFromTeam(String teamName, @NotNull Player player) {
    removePlayerFromTeam(teamName, player, MemberRemovalCause.LEAVE, null, null);
  }

  /**
   * Удаляет игрока из команды с указанием причины.
   *
   * @param teamName Название команды
   * @param player Игрок, которого нужно удалить
   * @param cause Причина удаления
   * @param initiatorName Имя инициатора (может быть null)
   * @param initiatorId UUID инициатора (может быть null)
   */
  void removePlayerFromTeam(
      String teamName,
      @NotNull Player player,
      @NotNull MemberRemovalCause cause,
      @Nullable String initiatorName,
      @Nullable UUID initiatorId);

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
  @NotNull
  RenameResult renameTeam(String oldTeamName, String newTeamName, @NotNull Player leader);

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
  List<UUID> getTeamMembers(String teamName);

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
  UUID getTeamLeaderId(String teamName);

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
   * Возвращает текущий режим вступления в команды.
   *
   * @return режим вступления
   */
  @NotNull
  JoinMode getJoinMode();

  /**
   * Определяет, применяется ли ограничение по количеству участников при перезагрузке.
   *
   * @return true, если ограничение активно
   */
  boolean isEnforceMaxMembersOnReload();

  /**
   * Отправляет приглашение игроку присоединиться к команде лидера.
   *
   * @param leader лидер команды, инициирующий приглашение
   * @param target целевой игрок
   * @param ttl время жизни приглашения (null — без ограничения)
   */
  void sendInvite(@NotNull Player leader, @NotNull Player target, @Nullable Duration ttl);

  /**
   * Отменяет ранее отправленное приглашение.
   *
   * @param leader лидер команды
   * @param targetName имя игрока, приглашение которого нужно отозвать
   */
  void revokeInvite(@NotNull Player leader, @NotNull String targetName);

  /**
   * Принимает приглашение в указанную команду.
   *
   * @param player игрок, принимающий приглашение
   * @param teamName команда, в которую осуществляется вступление
   */
  void acceptInvite(@NotNull Player player, @NotNull String teamName);

  /**
   * Отклоняет приглашение в указанную команду.
   *
   * @param player игрок, отклоняющий приглашение
   * @param teamName команда, приглашение от которой отклоняется
   */
  void declineInvite(@NotNull Player player, @NotNull String teamName);

  /**
   * Возвращает список активных приглашений игрока.
   *
   * @param playerId идентификатор игрока
   * @return список приглашений
   */
  @NotNull
  List<PendingInvite> getInvitesForPlayer(@NotNull UUID playerId);

  /**
   * Отправляет заявку на вступление игрока в команду.
   *
   * @param teamName Название команды
   * @param player Игрок, отправляющий заявку
   */
  void requestToJoinTeam(String teamName, @NotNull Player player);

  /**
   * Проверяет, отправлял ли игрок заявку в указанную команду.
   *
   * @param teamName название команды
   * @param playerId идентификатор игрока
   * @return true, если заявка уже существует
   */
  boolean hasPendingJoinRequest(String teamName, @NotNull UUID playerId);

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

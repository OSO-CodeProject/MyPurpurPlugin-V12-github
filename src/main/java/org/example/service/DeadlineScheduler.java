package org.example.service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.example.config.PluginConfig;
import org.example.config.RemovalPolicy;
import org.example.listener.TeamChatListener;
import org.example.model.Team;
import org.jetbrains.annotations.NotNull;

/**
 * Планировщик, который следит за размерами команд и временем, отведённым на их
 * уменьшение до допустимого количества участников.
 */
public class DeadlineScheduler {

  private final JavaPlugin plugin;
  private final PluginConfig pluginConfig;
  private final TeamStorage storage;
  private final Map<UUID, Long> deadlines = new ConcurrentHashMap<>();
  private BukkitTask task;

  /**
   * Создаёт планировщик, работающий поверх Bukkit, и связывает его с источниками
   * конфигурации и хранилищем команд.
   *
   * @param plugin плагин, в котором выполняется расписание
   * @param pluginConfig настройки, задающие ограничения и периоды проверок
   * @param storage хранилище, содержащее данные по командам
   */
  public DeadlineScheduler(
      @NotNull JavaPlugin plugin,
      @NotNull PluginConfig pluginConfig,
      @NotNull TeamStorage storage) {
    this.plugin = plugin;
    this.pluginConfig = pluginConfig;
    this.storage = storage;
  }

  /**
   * Возвращает отображение команд в момент времени, когда истекает их льготный
   * период.
   *
   * @return изменяемая карта дедлайнов по идентификатору команды
   */
  public Map<UUID, Long> getDeadlines() {
    return deadlines;
  }

  /**
   * Запускает периодическую проверку дедлайнов и автоматически перезапускает
   * задачу, если она уже была активна.
   */
  public synchronized void start() {
    long seconds = pluginConfig.getDeadlineNotifyPeriodSeconds();
    long period = 20L * Math.max(1, seconds);
    if (task != null) {
      task.cancel();
    }
    task =
        plugin
            .getServer()
            .getScheduler()
            .runTaskTimer(plugin, this::checkDeadlines, period, period);
  }

  /** Останавливает проверку дедлайнов, отменяя запланированную задачу. */
  public synchronized void stop() {
    if (task != null) {
      task.cancel();
      task = null;
    }
  }

  /**
   * Фиксирует команды, превышающие лимит участников, и назначает им дедлайны на
   * сокращение, очищая записи для команд с допустимым размером.
   */
  public void enforceTeamSizes() {
    int max = pluginConfig.getMaxMembers();
    boolean changed = false;
    if (max <= 0) {
      if (!deadlines.isEmpty()) {
        deadlines.clear();
        changed = true;
      }
      if (changed) {
        storage.markDeadlinesDirty();
      }
      return;
    }
    for (Map.Entry<UUID, Team> entry : storage.getTeams().entrySet()) {
      Team team = entry.getValue();
      int size = team.getMembers().size();
      if (size > max) {
        Long previous =
            deadlines.putIfAbsent(
                entry.getKey(),
                System.currentTimeMillis() + pluginConfig.getGracePeriodMinutes() * 60L * 1000L);
        if (previous == null) {
          changed = true;
        }
      } else {
        if (deadlines.remove(entry.getKey()) != null) {
          changed = true;
        }
      }
    }
    if (changed) {
      storage.markDeadlinesDirty();
    }
  }

  /**
   * Проверяет, истекли ли дедлайны команд, и при необходимости удаляет
   * лишних игроков, синхронизируя изменения с хранилищем.
   */
  public void checkDeadlines() {
    int max = pluginConfig.getMaxMembers();
    boolean changed = false;
    if (max <= 0) {
      if (!deadlines.isEmpty()) {
        deadlines.clear();
        changed = true;
      }
      if (changed) {
        storage.markDeadlinesDirty();
      }
      return;
    }
    long now = System.currentTimeMillis();
    Iterator<Map.Entry<UUID, Long>> it = deadlines.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<UUID, Long> entry = it.next();
      if (entry.getValue() <= now) {
        removeExtraPlayers(entry.getKey(), max);
        it.remove();
        changed = true;
      }
    }
    if (changed) {
      storage.markDeadlinesDirty();
    }
  }

  private void removeExtraPlayers(UUID teamId, int max) {
    Team team = storage.getTeams().get(teamId);
    if (team == null) return;
    boolean changed = false;
    RemovalPolicy policy = pluginConfig.getExcessPlayerRemovalPolicy();
    while (team.getMembers().size() > max) {
      String removed = selectMemberToRemove(team, policy);
      if (removed == null) {
        break;
      }
      team.removeMember(removed);
      storage.getPlayerTeams().remove(removed);
      changed = true;
      Player player = plugin.getServer().getPlayer(removed);
      if (player != null) {
        plugin
            .getServer()
            .getPluginManager()
            .callEvent(new TeamChatListener.PlayerPrefixUpdateEvent(player, null));
      }
    }
    if (changed) {
      storage.markTeamDirty(team);
    }
  }

  private String selectMemberToRemove(@NotNull Team team, @NotNull RemovalPolicy policy) {
    List<String> members = team.getMembers();
    if (members.isEmpty()) {
      return null;
    }
    String leader = team.getLeader();
    if (policy == RemovalPolicy.OFFLINE_FIRST) {
      String offlineCandidate = findOfflineCandidate(members, leader);
      if (offlineCandidate != null) {
        return offlineCandidate;
      }
    }
    return findLastJoinedCandidate(members, leader);
  }

  private String findOfflineCandidate(@NotNull List<String> members, String leader) {
    for (int i = members.size() - 1; i >= 0; i--) {
      String candidate = members.get(i);
      if (isLeader(candidate, leader)) {
        continue;
      }
      Player player = plugin.getServer().getPlayer(candidate);
      if (player == null || !player.isOnline()) {
        return candidate;
      }
    }
    return null;
  }

  private String findLastJoinedCandidate(@NotNull List<String> members, String leader) {
    for (int i = members.size() - 1; i >= 0; i--) {
      String candidate = members.get(i);
      if (!isLeader(candidate, leader)) {
        return candidate;
      }
    }
    return members.get(members.size() - 1);
  }

  private boolean isLeader(String candidate, String leader) {
    return leader != null && leader.equalsIgnoreCase(candidate);
  }

  /**
   * Возвращает зарегистрированный дедлайн конкретной команды по имени, если он
   * существует.
   *
   * @param teamName имя команды, для которой нужен дедлайн
   * @return отметка времени истечения льготного периода или {@code null}, если
   *     команда в норме
   */
  public Long getTeamDeadline(String teamName) {
    UUID id = storage.getTeamIdByName(teamName);
    return id != null ? deadlines.get(id) : null;
  }
}

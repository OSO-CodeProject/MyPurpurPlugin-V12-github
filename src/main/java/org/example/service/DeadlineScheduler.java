package org.example.service;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.RenderType;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.example.config.PluginConfig;
import org.example.config.RemovalPolicy;
import org.example.listener.TeamChatListener;
import org.example.model.Team;
import org.example.util.TeamMessageUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Планировщик, который следит за размерами команд и временем, отведённым на их уменьшение до
 * допустимого количества участников.
 */
public class DeadlineScheduler {

  private final JavaPlugin plugin;
  private final PluginConfig pluginConfig;
  private final TeamStorage storage;
  private final Map<UUID, Long> deadlines = new ConcurrentHashMap<>();
  private final Map<UUID, Scoreboard> leaderOriginalScoreboards = new ConcurrentHashMap<>();
  private final Map<UUID, UUID> teamLeaderIds = new ConcurrentHashMap<>();
  private BukkitTask task;

  private static final String SCOREBOARD_OBJECTIVE = "deadlineWarn";
  private static final int SCOREBOARD_LINE_LENGTH = 30;
  private static final String SCOREBOARD_ENTRY_PREFIX = "deadline-line-";
  private static final NamedTextColor[] SCOREBOARD_COLORS = {
    NamedTextColor.BLUE,
    NamedTextColor.GREEN,
    NamedTextColor.AQUA,
    NamedTextColor.GOLD,
    NamedTextColor.RED,
    NamedTextColor.LIGHT_PURPLE,
    NamedTextColor.YELLOW,
    NamedTextColor.WHITE,
    NamedTextColor.DARK_BLUE,
    NamedTextColor.DARK_GREEN,
    NamedTextColor.DARK_AQUA,
    NamedTextColor.DARK_RED,
    NamedTextColor.DARK_PURPLE,
    NamedTextColor.DARK_GRAY,
    NamedTextColor.GRAY
  };
  private static final Method OBJECTIVE_GET_SCORE_COMPONENT = resolveComponentScoreMethod();

  private enum DeadlineDisplayMode {
    CHAT,
    ACTION_BAR,
    SCOREBOARD;

    static DeadlineDisplayMode fromConfig(String raw) {
      if (raw == null) {
        return CHAT;
      }
      try {
        return DeadlineDisplayMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ex) {
        return CHAT;
      }
    }
  }

  /**
   * Создаёт планировщик, работающий поверх Bukkit, и связывает его с источниками конфигурации и
   * хранилищем команд.
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
   * Возвращает отображение команд в момент времени, когда истекает их льготный период.
   *
   * @return изменяемая карта дедлайнов по идентификатору команды
   */
  public Map<UUID, Long> getDeadlines() {
    return deadlines;
  }

  /**
   * Принудительно завершает льготный период указанной команды, очищая связанные уведомления лидера.
   */
  public void cancelDeadline(@NotNull Team team) {
    if (team == null) {
      return;
    }
    UUID teamId = team.getId();
    boolean removed = deadlines.remove(teamId) != null;
    UUID leaderId = teamLeaderIds.remove(teamId);
    if (leaderId != null) {
      clearLeaderDisplay(leaderId);
    } else {
      clearLeaderDisplay(team);
    }
    if (removed) {
      storage.markDeadlinesDirty();
    }
  }

  /**
   * Запускает периодическую проверку дедлайнов и автоматически перезапускает задачу, если она уже
   * была активна.
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
   * Фиксирует команды, превышающие лимит участников, и назначает им дедлайны на сокращение, очищая
   * записи для команд с допустимым размером.
   */
  public void enforceTeamSizes() {
    enforceTeamSizes(false);
  }

  /**
   * Фиксирует команды, превышающие лимит участников, и назначает им дедлайны на сокращение, очищая
   * записи для команд с допустимым размером.
   *
   * @param triggeredByReload {@code true}, если проверка вызвана перезагрузкой плагина или сервера
   */
  public void enforceTeamSizes(boolean triggeredByReload) {
    int max = pluginConfig.getMaxMembers();
    boolean changed = false;
    boolean enforcementEnabled = !triggeredByReload || pluginConfig.isEnforceMaxMembersOnReload();
    boolean graceEnabled =
        pluginConfig.isGracePeriodEnabled() && pluginConfig.getGracePeriodMinutes() > 0;

    if (max <= 0) {
      if (!deadlines.isEmpty()) {
        deadlines.clear();
        changed = true;
        resetAllLeaderDisplays();
      }
      if (changed) {
        storage.markDeadlinesDirty();
      }
      return;
    }

    if (!enforcementEnabled && !deadlines.isEmpty()) {
      deadlines.clear();
      changed = true;
      resetAllLeaderDisplays();
    }

    long now = System.currentTimeMillis();
    for (Team team : storage.getTeams().values()) {
      changed |= evaluateTeamState(team, max, enforcementEnabled, graceEnabled, now);
    }
    if (changed) {
      storage.markDeadlinesDirty();
    }
  }

  /**
   * Переоценивает состояние конкретной команды и при необходимости обновляет дедлайн или проводит
   * удаление лишних участников без полного обхода всех команд.
   */
  public void evaluateTeam(@Nullable Team team) {
    evaluateTeam(team, false);
  }

  /**
   * Переоценивает состояние конкретной команды с учётом источника вызова.
   *
   * @param team команда, для которой следует обновить дедлайн
   * @param triggeredByReload {@code true}, если проверка вызвана перезагрузкой плагина или сервера
   */
  public void evaluateTeam(@Nullable Team team, boolean triggeredByReload) {
    if (team == null) {
      return;
    }
    int max = pluginConfig.getMaxMembers();
    boolean enforcementEnabled = !triggeredByReload || pluginConfig.isEnforceMaxMembersOnReload();
    boolean graceEnabled =
        pluginConfig.isGracePeriodEnabled() && pluginConfig.getGracePeriodMinutes() > 0;
    long now = System.currentTimeMillis();
    long startedAt = System.nanoTime();
    boolean changed = evaluateTeamState(team, max, enforcementEnabled, graceEnabled, now);
    if (changed) {
      storage.markDeadlinesDirty();
    }
    if (pluginConfig.isDebugModeEnabled()) {
      long elapsedMicros = TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - startedAt);
      plugin
          .getLogger()
          .info(
              "Пересчёт команды "
                  + team.getName()
                  + " занял "
                  + elapsedMicros
                  + " мкс (reload="
                  + triggeredByReload
                  + ")");
    }
  }

  private boolean evaluateTeamState(
      @NotNull Team team, int max, boolean enforcementEnabled, boolean graceEnabled, long now) {
    boolean changed = false;
    UUID teamId = team.getId();

    if (max <= 0) {
      if (deadlines.remove(teamId) != null) {
        changed = true;
      }
      clearLeaderDisplay(team);
      return changed;
    }

    int size = team.getMembers().size();
    if (size <= max) {
      if (deadlines.remove(teamId) != null) {
        changed = true;
      }
      clearLeaderDisplay(team);
      return changed;
    }

    int excess = size - max;
    plugin
        .getLogger()
        .warning("Команда " + team.getName() + " превышает лимит: " + size + " из " + max + ".");

    if (!enforcementEnabled) {
      Component leaderMessage = buildEnforcementDisabledMessage(max, excess);
      DeadlineDisplayMode mode = getDisplayMode();
      if (mode == DeadlineDisplayMode.SCOREBOARD) {
        notifyLeaderWithoutScoreboard(team, leaderMessage);
        clearLeaderDisplay(team);
      } else {
        notifyLeader(team, leaderMessage);
      }
      notifyAdmins(
          adminBaseMessage(team, size, max)
              .append(Component.space())
              .append(Component.text("Автоматическое сокращение отключено.", NamedTextColor.GOLD)));
      if (deadlines.remove(teamId) != null) {
        changed = true;
      }
      return changed;
    }

    if (!graceEnabled) {
      if (deadlines.remove(teamId) != null) {
        changed = true;
      }
      clearLeaderDisplay(team);
      int removed = removeExtraPlayers(team, max);
      if (removed > 0) {
        Component leaderMessage = TeamMessageUtils.forcedRemovalMessage(removed);
        notifyLeader(team, leaderMessage);
        notifyAdmins(
            adminBaseMessage(team, size, max)
                .append(Component.space())
                .append(
                    Component.text(
                        "Удалено " + removed + " участника(ов) без льготного периода.",
                        NamedTextColor.RED)));
        plugin
            .getLogger()
            .info(
                "Команда "
                    + team.getName()
                    + ": удалено "
                    + removed
                    + " участника(ов) без льготного периода.");
      }
      return changed;
    }

    Long existingDeadline = deadlines.get(teamId);
    if (existingDeadline != null) {
      if (existingDeadline <= now) {
        changed = true;
        deadlines.remove(teamId);
        clearLeaderDisplay(team);
        int removed = removeExtraPlayers(team, max);
        if (removed > 0) {
          Component leaderMessage = TeamMessageUtils.forcedRemovalMessage(removed);
          notifyLeader(team, leaderMessage);
          notifyAdmins(
              adminBaseMessage(team, size, max)
                  .append(Component.space())
                  .append(
                      Component.text(
                          "Льготный период истёк, удалено " + removed + " участника(ов).",
                          NamedTextColor.RED)));
          plugin
              .getLogger()
              .info(
                  "Льготный период команды "
                      + team.getName()
                      + " истёк, удалено "
                      + removed
                      + " игрок(ов).");
        }
        return changed;
      }
      return changed;
    }

    long deadlineAt = now + pluginConfig.getGracePeriodMinutes() * 60L * 1000L;
    Long previous = deadlines.put(teamId, deadlineAt);
    boolean deadlineUpdated = !Objects.equals(previous, deadlineAt);
    if (deadlineUpdated) {
      changed = true;
      Component leaderMessage =
          TeamMessageUtils.deadlineWarningMessage(
              max, pluginConfig.getGracePeriodMinutes(), excess);
      notifyLeader(team, leaderMessage);
      notifyAdmins(
          adminBaseMessage(team, size, max)
              .append(Component.space())
              .append(
                  Component.text(
                      "Лидеру дано "
                          + pluginConfig.getGracePeriodMinutes()
                          + " мин. на сокращение.",
                      NamedTextColor.GOLD)));
    }
    return changed;
  }

  public void handleLeaderTransfer(@NotNull Team team) {
    if (team == null) {
      return;
    }
    UUID teamId = team.getId();
    UUID currentLeader = team.getLeaderId();
    UUID previousLeader = teamLeaderIds.get(teamId);
    if (previousLeader != null
        && (currentLeader == null || !previousLeader.equals(currentLeader))) {
      teamLeaderIds.remove(teamId, previousLeader);
      clearLeaderDisplay(previousLeader);
    }
    Long deadlineAt = deadlines.get(teamId);
    if (deadlineAt == null) {
      return;
    }
    int max = pluginConfig.getMaxMembers();
    if (max <= 0) {
      return;
    }
    int excess = Math.max(0, team.getMembers().size() - max);
    if (excess <= 0) {
      return;
    }
    long remainingMillis = Math.max(0L, deadlineAt - System.currentTimeMillis());
    long minutesLeft = TimeUnit.MILLISECONDS.toMinutes(remainingMillis);
    long secondsLeft =
        TimeUnit.MILLISECONDS.toSeconds(remainingMillis) - TimeUnit.MINUTES.toSeconds(minutesLeft);
    Component message = TeamMessageUtils.deadlineRemainingMessage(minutesLeft, secondsLeft, excess);
    notifyLeader(team, message);
    if (getDisplayMode() == DeadlineDisplayMode.SCOREBOARD) {
      UUID leaderId = team.getLeaderId();
      if (leaderId != null) {
        Player leader = plugin.getServer().getPlayer(leaderId);
        if (leader != null) {
          TeamMessageUtils.sendTeamMessage(leader, message);
        }
      }
    }
  }

  /**
   * Проверяет, истекли ли дедлайны команд, и при необходимости удаляет лишних игроков,
   * синхронизируя изменения с хранилищем.
   */
  public void checkDeadlines() {
    int max = pluginConfig.getMaxMembers();
    boolean changed = false;
    if (max <= 0) {
      if (!deadlines.isEmpty()) {
        deadlines.clear();
        changed = true;
        resetAllLeaderDisplays();
      }
      if (changed) {
        storage.markDeadlinesDirty();
      }
      return;
    }
    long now = System.currentTimeMillis();
    Iterator<Map.Entry<UUID, Long>> it = deadlines.entrySet().iterator();
    List<UUID> deadlinesToRemove = new ArrayList<>();
    while (it.hasNext()) {
      Map.Entry<UUID, Long> entry = it.next();
      UUID teamId = entry.getKey();
      long deadlineAt = entry.getValue();
      Team team = storage.getTeams().get(teamId);
      if (team == null) {
        UUID leaderId = teamLeaderIds.remove(teamId);
        if (leaderId != null) {
          clearLeaderDisplay(leaderId);
        }
        deadlinesToRemove.add(teamId);
        changed = true;
        continue;
      }

      int sizeBefore = team.getMembers().size();
      int excess = sizeBefore - max;
      if (excess <= 0) {
        deadlinesToRemove.add(teamId);
        clearLeaderDisplay(team);
        changed = true;
        continue;
      }

      if (deadlineAt <= now) {
        int removed = removeExtraPlayers(team, max);
        if (removed > 0) {
          Component leaderMessage = TeamMessageUtils.forcedRemovalMessage(removed);
          notifyLeader(team, leaderMessage);
          notifyAdmins(
              adminBaseMessage(team, sizeBefore, max)
                  .append(Component.space())
                  .append(
                      Component.text(
                          "Льготный период истёк, удалено " + removed + " участника(ов).",
                          NamedTextColor.RED)));
          plugin
              .getLogger()
              .info(
                  "Льготный период команды "
                      + team.getName()
                      + " истёк, удалено "
                      + removed
                      + " игрок(ов).");
        }
        deadlinesToRemove.add(teamId);
        clearLeaderDisplay(team);
        changed = true;
        continue;
      }

      long remainingMillis = Math.max(0L, deadlineAt - now);
      long minutesLeft = TimeUnit.MILLISECONDS.toMinutes(remainingMillis);
      long secondsLeft =
          TimeUnit.MILLISECONDS.toSeconds(remainingMillis)
              - TimeUnit.MINUTES.toSeconds(minutesLeft);
      Component leaderMessage =
          TeamMessageUtils.deadlineRemainingMessage(minutesLeft, secondsLeft, excess);
      notifyLeader(team, leaderMessage);
    }
    if (!deadlinesToRemove.isEmpty()) {
      deadlines.keySet().removeAll(deadlinesToRemove);
      deadlinesToRemove.stream()
          .map(teamLeaderIds::remove)
          .filter(Objects::nonNull)
          .forEach(this::clearLeaderDisplay);
    }
    if (changed) {
      storage.markDeadlinesDirty();
    }
  }

  private int removeExtraPlayers(@NotNull Team team, int max) {
    boolean changed = false;
    int removedCount = 0;
    RemovalPolicy policy = pluginConfig.getExcessPlayerRemovalPolicy();
    while (team.getMembers().size() > max) {
      UUID removed = selectMemberToRemove(team, policy);
      if (removed == null) {
        break;
      }
      team.removeMember(removed);
      storage.getPlayerTeams().remove(removed);
      removedCount++;
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
    return removedCount;
  }

  private UUID selectMemberToRemove(@NotNull Team team, @NotNull RemovalPolicy policy) {
    List<UUID> members = team.getMembers();
    if (members.isEmpty()) {
      return null;
    }
    UUID leader = team.getLeaderId();
    if (policy == RemovalPolicy.OFFLINE_FIRST) {
      UUID offlineCandidate = findOfflineCandidate(members, leader);
      if (offlineCandidate != null) {
        return offlineCandidate;
      }
    }
    return findLastJoinedCandidate(members, leader);
  }

  private UUID findOfflineCandidate(@NotNull List<UUID> members, UUID leader) {
    for (int i = members.size() - 1; i >= 0; i--) {
      UUID candidate = members.get(i);
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

  private UUID findLastJoinedCandidate(@NotNull List<UUID> members, UUID leader) {
    for (int i = members.size() - 1; i >= 0; i--) {
      UUID candidate = members.get(i);
      if (!isLeader(candidate, leader)) {
        return candidate;
      }
    }
    return members.get(members.size() - 1);
  }

  private boolean isLeader(UUID candidate, UUID leader) {
    return leader != null && leader.equals(candidate);
  }

  /**
   * Возвращает зарегистрированный дедлайн конкретной команды по имени, если он существует.
   *
   * @param teamName имя команды, для которой нужен дедлайн
   * @return отметка времени истечения льготного периода или {@code null}, если команда в норме
   */
  public Long getTeamDeadline(String teamName) {
    UUID id = storage.getTeamIdByName(teamName);
    return id != null ? deadlines.get(id) : null;
  }

  private Component buildEnforcementDisabledMessage(int max, int excess) {
    return Component.text("Максимальный размер команды установлен на ", NamedTextColor.YELLOW)
        .append(Component.text(max, NamedTextColor.WHITE))
        .append(Component.text(". Удалите ", NamedTextColor.YELLOW))
        .append(Component.text(excess + " участника(ов)", NamedTextColor.WHITE))
        .append(
            Component.text(
                ", но автоматическое сокращение сейчас отключено.", NamedTextColor.YELLOW));
  }

  private Component adminBaseMessage(@NotNull Team team, int size, int max) {
    return Component.text("Команда ", NamedTextColor.GOLD)
        .append(Component.text(team.getName(), NamedTextColor.WHITE))
        .append(Component.text(" превышает лимит (", NamedTextColor.GOLD))
        .append(Component.text(size + "/" + max, NamedTextColor.WHITE))
        .append(Component.text(")", NamedTextColor.GOLD));
  }

  private DeadlineDisplayMode getDisplayMode() {
    return DeadlineDisplayMode.fromConfig(pluginConfig.getDeadlineDisplayMode());
  }

  public void resetLeaderDisplays() {
    resetAllLeaderDisplays();
  }

  private void resetAllLeaderDisplays() {
    storage.getTeams().values().forEach(this::clearLeaderDisplay);
    // Полностью очищаем кеш, чтобы при перезапуске/отключении не осталось привязок к
    // временным табло лидеров и их именам.
    leaderOriginalScoreboards.clear();
    teamLeaderIds.clear();
  }

  private void clearLeaderDisplay(@NotNull Team team) {
    UUID notifiedLeader = teamLeaderIds.remove(team.getId());
    if (notifiedLeader != null) {
      clearLeaderDisplay(notifiedLeader);
      return;
    }
    UUID leaderId = team.getLeaderId();
    if (leaderId == null) {
      return;
    }
    DeadlineDisplayMode mode = getDisplayMode();
    if (mode == DeadlineDisplayMode.SCOREBOARD) {
      if (leaderOriginalScoreboards.containsKey(leaderId)) {
        clearLeaderDisplay(leaderId);
      }
      return;
    }
    if (mode == DeadlineDisplayMode.ACTION_BAR) {
      clearLeaderDisplay(leaderId);
    }
  }

  private void clearLeaderDisplay(UUID leaderId) {
    if (leaderId == null) {
      return;
    }
    boolean hadOriginalScoreboard = leaderOriginalScoreboards.containsKey(leaderId);
    Scoreboard original = hadOriginalScoreboard ? leaderOriginalScoreboards.remove(leaderId) : null;
    Player leader = plugin.getServer().getPlayer(leaderId);
    DeadlineDisplayMode mode = getDisplayMode();
    if (leader != null) {
      if (hadOriginalScoreboard) {
        if (original != null) {
          leader.setScoreboard(original);
        } else if (mode == DeadlineDisplayMode.SCOREBOARD) {
          ScoreboardManager manager = plugin.getServer().getScoreboardManager();
          if (manager != null) {
            leader.setScoreboard(manager.getMainScoreboard());
          }
        }
      }
      if (mode == DeadlineDisplayMode.ACTION_BAR) {
        leader.sendActionBar(Component.empty());
      }
    }
  }

  private void notifyLeader(@NotNull Team team, @NotNull Component message) {
    UUID leaderId = team.getLeaderId();
    if (leaderId == null) {
      return;
    }
    Player leader = plugin.getServer().getPlayer(leaderId);
    if (leader == null) {
      return;
    }
    DeadlineDisplayMode mode = getDisplayMode();
    if (mode == DeadlineDisplayMode.SCOREBOARD) {
      UUID previousLeader = teamLeaderIds.put(team.getId(), leaderId);
      if (previousLeader != null && !previousLeader.equals(leaderId)) {
        clearLeaderDisplay(previousLeader);
      }
    } else {
      UUID previousLeader = teamLeaderIds.remove(team.getId());
      if (previousLeader != null) {
        clearLeaderDisplay(previousLeader);
      }
    }
    switch (mode) {
      case ACTION_BAR -> leader.sendActionBar(message);
      case SCOREBOARD -> sendScoreboardMessage(leader, message);
      case CHAT -> TeamMessageUtils.sendTeamMessage(leader, message);
    }
  }

  private void notifyLeaderWithoutScoreboard(@NotNull Team team, @NotNull Component message) {
    UUID leaderId = team.getLeaderId();
    if (leaderId == null) {
      return;
    }
    Player leader = plugin.getServer().getPlayer(leaderId);
    if (leader == null) {
      return;
    }
    TeamMessageUtils.sendTeamMessage(leader, message);
  }

  private void notifyAdmins(Component message) {
    if (message == null || !pluginConfig.shouldNotifyAdmins()) {
      return;
    }
    plugin.getServer().getOnlinePlayers().stream()
        .filter(player -> player.isOp() || player.hasPermission("mypurpurplugin.admin"))
        .forEach(player -> TeamMessageUtils.sendTeamMessage(player, message));
  }

  private void sendScoreboardMessage(@NotNull Player player, @NotNull Component message) {
    ScoreboardManager manager = plugin.getServer().getScoreboardManager();
    if (manager == null) {
      TeamMessageUtils.sendTeamMessage(player, message);
      return;
    }
    UUID playerId = player.getUniqueId();
    leaderOriginalScoreboards.putIfAbsent(playerId, player.getScoreboard());
    Scoreboard scoreboard = manager.getNewScoreboard();
    Objective objective =
        scoreboard.registerNewObjective(
            SCOREBOARD_OBJECTIVE,
            Criteria.DUMMY,
            Component.text("⚠ Лимит команды", NamedTextColor.GOLD),
            RenderType.INTEGER);
    objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    List<Component> lines = wrapForScoreboard(message);
    int score = lines.size();
    for (int index = 0; index < lines.size(); index++) {
      Component line = lines.get(index);
      Score scoreboardLine = getScoreForLine(objective, line, index);
      scoreboardLine.setScore(score--);
    }
    player.setScoreboard(scoreboard);
  }

  private Score getScoreForLine(Objective objective, Component line, int index) {
    if (OBJECTIVE_GET_SCORE_COMPONENT != null) {
      try {
        return (Score) OBJECTIVE_GET_SCORE_COMPONENT.invoke(objective, line);
      } catch (IllegalAccessException | InvocationTargetException ex) {
        plugin
            .getLogger()
            .warning(
                "Failed to use component-based scoreboard entry, falling back to string entries: "
                    + ex.getMessage());
      }
    }
    Score score = objective.getScore(SCOREBOARD_ENTRY_PREFIX + index);
    score.customName(line);
    return score;
  }

  private List<Component> wrapForScoreboard(@NotNull Component message) {
    String plain = PlainTextComponentSerializer.plainText().serialize(message);
    if (plain.isBlank()) {
      plain = " ";
    }
    List<Component> lines = new ArrayList<>();
    int index = 0;
    int colorIndex = 0;
    while (index < plain.length() && lines.size() < SCOREBOARD_COLORS.length) {
      int end = Math.min(index + SCOREBOARD_LINE_LENGTH, plain.length());
      String part = plain.substring(index, end);
      lines.add(Component.text(part, SCOREBOARD_COLORS[colorIndex % SCOREBOARD_COLORS.length]));
      colorIndex++;
      index = end;
    }
    if (lines.isEmpty()) {
      lines.add(Component.text(plain, SCOREBOARD_COLORS[0]));
    }
    return lines;
  }

  private static Method resolveComponentScoreMethod() {
    try {
      return Objective.class.getMethod("getScore", Component.class);
    } catch (NoSuchMethodException ex) {
      return null;
    }
  }
}

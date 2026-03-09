package org.example.config;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

/** Класс для управления конфигурацией плагина. */
public class PluginConfig {

  /** Пути до ключей конфигурации. */
  public static final class Keys {
    private Keys() {}

    public static final String DEBUG_MODE = "debug.mode";
    public static final String CHAT_FORCE_WHITE = "chat.force-white";

    public static final class Team {
      private Team() {}

      public static final class Commands {
        private Commands() {}

        public static final String REQUIRES_OP = "team.commands.requires-op";
      }

      public static final class Notifications {
        private Notifications() {}

        public static final String NOTIFY_ADMINS = "team.notifications.notify-admins";
      }

      public static final class Naming {
        private Naming() {}

        public static final class Prefix {
          private Prefix() {}

          public static final String MIN_LENGTH = "team.naming.prefix.min-length";
          public static final String MAX_LENGTH = "team.naming.prefix.max-length";
        }

        public static final class TeamName {
          private TeamName() {}

          public static final String MIN_LENGTH = "team.naming.team.min-length";
          public static final String MAX_LENGTH = "team.naming.team.max-length";
        }
      }

      public static final class Membership {
        private Membership() {}

        public static final String JOIN_MODE = "team.membership.join-mode";
        public static final String MAX_MEMBERS = "team.membership.max-members";
        public static final String ENFORCE_MAX_ON_RELOAD =
            "team.membership.enforce-max-members-on-reload";

        public static final class GracePeriod {
          private GracePeriod() {}

          public static final String ENABLED = "team.membership.grace-period.enabled";
          public static final String MINUTES = "team.membership.grace-period.minutes";
        }
      }

      public static final class Deadlines {
        private Deadlines() {}

        public static final String NOTIFY_PERIOD_SECONDS = "team.deadlines.notify-period-seconds";
        public static final String DISPLAY_MODE = "team.deadlines.display-mode";
        public static final String REMOVAL_POLICY = "team.deadlines.removal-policy";
      }

      public static final class Storage {
        private Storage() {}

        public static final String SAVE_INTERVAL_SECONDS = "team.storage.save-interval-seconds";
      }
    }

    public static final class Menu {
      private Menu() {}

      public static final class Sound {
        private Sound() {}

        public static final String OPEN = "menu.sound.open";
        public static final String VOLUME = "menu.sound.volume";
        public static final String PITCH = "menu.sound.pitch";
      }

      public static final String PARTICLE_EFFECT = "menu.particle-effect";
    }

    public static final class Chat {
      private Chat() {}

      public static final class Local {
        private Local() {}

        public static final String ENABLED = "chat.local.enabled";
        public static final String OBFUSCATE_FALLOFF = "chat.local.obfuscate-falloff";
        public static final String OBFUSCATION_CHARS = "chat.local.obfuscation-chars";

        public static final String WHISPER_PREFIX = "chat.local.volumes.whisper.prefix";
        public static final String WHISPER_RADIUS = "chat.local.volumes.whisper.radius";
        public static final String WHISPER_FALLOFF = "chat.local.volumes.whisper.falloff";
        public static final String WHISPER_FORMAT = "chat.local.volumes.whisper.format";

        public static final String TALK_PREFIX = "chat.local.volumes.talk.prefix";
        public static final String TALK_RADIUS = "chat.local.volumes.talk.radius";
        public static final String TALK_FALLOFF = "chat.local.volumes.talk.falloff";
        public static final String TALK_FORMAT = "chat.local.volumes.talk.format";

        public static final String YELL_PREFIX = "chat.local.volumes.yell.prefix";
        public static final String YELL_RADIUS = "chat.local.volumes.yell.radius";
        public static final String YELL_FALLOFF = "chat.local.volumes.yell.falloff";
        public static final String YELL_FORMAT = "chat.local.volumes.yell.format";

        public static final String SCREAM_PREFIX = "chat.local.volumes.scream.prefix";
        public static final String SCREAM_RADIUS = "chat.local.volumes.scream.radius";
        public static final String SCREAM_FALLOFF = "chat.local.volumes.scream.falloff";
        public static final String SCREAM_FORMAT = "chat.local.volumes.scream.format";
      }
    }
  }

  private final JavaPlugin plugin;
  private FileConfiguration config;
  private File configFile;
  private final Object saveLock = new Object();
  private volatile boolean dirty;
  private BukkitTask autoSaveTask;

  public PluginConfig(@NotNull JavaPlugin plugin) {
    this.plugin = plugin;
    loadConfig();
  }

  /** Загружает или создаёт файл конфигурации. */
  private void loadConfig() {
    configFile = new File(plugin.getDataFolder(), "config.yml");

    // Создаём папку плагина, если она не существует
    if (!plugin.getDataFolder().exists()) {
      if (!plugin.getDataFolder().mkdirs()) {
        plugin
            .getLogger()
            .severe(
                "Не удалось создать папку плагина: " + plugin.getDataFolder().getAbsolutePath());
      }
    }

    // Создаём файл конфигурации, если он не существует
    if (!configFile.exists()) {
      plugin.saveResource("config.yml", false);
    }

    config = YamlConfiguration.loadConfiguration(configFile);
    dirty = false;
    setDefaults(true);
  }

  /** Устанавливает значения по умолчанию для конфигурации. */
  private void setDefaults(boolean persistChanges) {
    boolean changed = false;

    // Миграция старых ключей
    changed |= migrateLegacyKey("debug-mode", Keys.DEBUG_MODE);
    changed |= migrateLegacyKey("force-white-chat", Keys.CHAT_FORCE_WHITE);
    changed |= migrateLegacyKey("team.requires-op", Keys.Team.Commands.REQUIRES_OP);
    changed |= migrateLegacyKey("team.notify-admins", Keys.Team.Notifications.NOTIFY_ADMINS);
    changed |= migrateLegacyKey("team.max-members", Keys.Team.Membership.MAX_MEMBERS);
    changed |=
        migrateLegacyKey(
            "team.enforce-max-members-on-reload", Keys.Team.Membership.ENFORCE_MAX_ON_RELOAD);
    changed |=
        migrateLegacyKey("team.grace-period-enabled", Keys.Team.Membership.GracePeriod.ENABLED);
    changed |=
        migrateLegacyKey("team.grace-period-minutes", Keys.Team.Membership.GracePeriod.MINUTES);
    changed |= migrateLegacyKey("team.min-prefix-length", Keys.Team.Naming.Prefix.MIN_LENGTH);
    changed |= migrateLegacyKey("team.max-prefix-length", Keys.Team.Naming.Prefix.MAX_LENGTH);
    changed |= migrateLegacyKey("team.min-team-name-length", Keys.Team.Naming.TeamName.MIN_LENGTH);
    changed |= migrateLegacyKey("team.max-team-name-length", Keys.Team.Naming.TeamName.MAX_LENGTH);
    changed |=
        migrateLegacyKey(
            "team.deadline-notify-period-seconds", Keys.Team.Deadlines.NOTIFY_PERIOD_SECONDS);
    changed |= migrateLegacyKey("team.deadline-display-mode", Keys.Team.Deadlines.DISPLAY_MODE);
    changed |= migrateLegacyKey("team.deadline-removal-policy", Keys.Team.Deadlines.REMOVAL_POLICY);
    changed |=
        migrateLegacyKey("team.save-interval-seconds", Keys.Team.Storage.SAVE_INTERVAL_SECONDS);
    changed |= migrateLegacyKey("menu.open-sound", Keys.Menu.Sound.OPEN);
    changed |= migrateLegacyKey("menu.sound-volume", Keys.Menu.Sound.VOLUME);
    changed |= migrateLegacyKey("menu.sound-pitch", Keys.Menu.Sound.PITCH);
    changed |= migrateLegacyJoinModeSetting();

    // Глобальные настройки
    changed |= addDefaultIfMissing(Keys.DEBUG_MODE, false);
    changed |= addDefaultIfMissing(Keys.CHAT_FORCE_WHITE, false);

    // Настройки локального чата
    changed |= addDefaultIfMissing(Keys.Chat.Local.ENABLED, true);
    changed |= addDefaultIfMissing(Keys.Chat.Local.OBFUSCATE_FALLOFF, true);
    changed |=
        addDefaultIfMissing(
            Keys.Chat.Local.OBFUSCATION_CHARS, java.util.Arrays.asList("#", "@", "%", "*", "?"));

    changed |= addDefaultIfMissing(Keys.Chat.Local.WHISPER_PREFIX, "-");
    changed |= addDefaultIfMissing(Keys.Chat.Local.WHISPER_RADIUS, 2.5);
    changed |= addDefaultIfMissing(Keys.Chat.Local.WHISPER_FALLOFF, 1.5);
    changed |= addDefaultIfMissing(Keys.Chat.Local.WHISPER_FORMAT, "< Шепотом >");

    changed |= addDefaultIfMissing(Keys.Chat.Local.TALK_PREFIX, "");
    changed |= addDefaultIfMissing(Keys.Chat.Local.TALK_RADIUS, 16.0);
    changed |= addDefaultIfMissing(Keys.Chat.Local.TALK_FALLOFF, 4.0);
    changed |= addDefaultIfMissing(Keys.Chat.Local.TALK_FORMAT, "");

    changed |= addDefaultIfMissing(Keys.Chat.Local.YELL_PREFIX, "+");
    changed |= addDefaultIfMissing(Keys.Chat.Local.YELL_RADIUS, 32.0);
    changed |= addDefaultIfMissing(Keys.Chat.Local.YELL_FALLOFF, 6.0);
    changed |= addDefaultIfMissing(Keys.Chat.Local.YELL_FORMAT, "< Громко >");

    changed |= addDefaultIfMissing(Keys.Chat.Local.SCREAM_PREFIX, "++");
    changed |= addDefaultIfMissing(Keys.Chat.Local.SCREAM_RADIUS, 48.0);
    changed |= addDefaultIfMissing(Keys.Chat.Local.SCREAM_FALLOFF, 8.0);
    changed |= addDefaultIfMissing(Keys.Chat.Local.SCREAM_FORMAT, "< Крик >");

    // Основные настройки
    changed |= addDefaultIfMissing(Keys.Team.Commands.REQUIRES_OP, false);
    changed |= addDefaultIfMissing(Keys.Team.Notifications.NOTIFY_ADMINS, true);
    changed |= addDefaultIfMissing(Keys.Team.Membership.MAX_MEMBERS, 0);
    changed |= addDefaultIfMissing(Keys.Team.Membership.JOIN_MODE, JoinMode.OPEN.name());
    changed |= addDefaultIfMissing(Keys.Team.Naming.Prefix.MIN_LENGTH, 1);
    changed |= addDefaultIfMissing(Keys.Team.Naming.Prefix.MAX_LENGTH, 16);
    changed |= addDefaultIfMissing(Keys.Team.Naming.TeamName.MIN_LENGTH, 3);
    changed |= addDefaultIfMissing(Keys.Team.Naming.TeamName.MAX_LENGTH, 32);
    changed |= addDefaultIfMissing(Keys.Team.Membership.ENFORCE_MAX_ON_RELOAD, true);
    changed |= addDefaultIfMissing(Keys.Team.Membership.GracePeriod.ENABLED, true);
    changed |= addDefaultIfMissing(Keys.Team.Membership.GracePeriod.MINUTES, 10);
    changed |= addDefaultIfMissing(Keys.Team.Deadlines.NOTIFY_PERIOD_SECONDS, 300L);
    changed |= addDefaultIfMissing(Keys.Team.Storage.SAVE_INTERVAL_SECONDS, 60L);
    changed |= addDefaultIfMissing(Keys.Team.Deadlines.DISPLAY_MODE, "CHAT");
    changed |= addDefaultIfMissing(Keys.Team.Deadlines.REMOVAL_POLICY, "last-joined");

    // Настройки меню
    changed |= addDefaultIfMissing(Keys.Menu.Sound.OPEN, "BLOCK_NOTE_BLOCK_PLING");
    changed |= addDefaultIfMissing(Keys.Menu.PARTICLE_EFFECT, "FIREWORK");
    changed |= addDefaultIfMissing(Keys.Menu.Sound.VOLUME, 1.0);
    changed |= addDefaultIfMissing(Keys.Menu.Sound.PITCH, 1.0);

    config.options().copyDefaults(true);

    if (persistChanges && changed) {
      markDirty();
    }
  }

  private boolean addDefaultIfMissing(@NotNull String path, Object value) {
    boolean missing = !config.contains(path);
    config.addDefault(path, value);
    return missing;
  }

  private boolean migrateLegacyKey(@NotNull String legacyPath, @NotNull String newPath) {
    if (config.contains(legacyPath) && !config.contains(newPath)) {
      Object value = config.get(legacyPath);
      config.set(newPath, value);
      config.set(legacyPath, null);
      return true;
    }
    return false;
  }

  private boolean migrateLegacyJoinModeSetting() {
    if (config.contains(Keys.Team.Membership.JOIN_MODE)) {
      return false;
    }
    String legacyInviteOnly = "team.membership.invite-only";
    if (config.contains(legacyInviteOnly)) {
      boolean inviteOnly = config.getBoolean(legacyInviteOnly);
      config.set(
          Keys.Team.Membership.JOIN_MODE,
          inviteOnly ? JoinMode.INVITE_ONLY.name() : JoinMode.OPEN.name());
      config.set(legacyInviteOnly, null);
      return true;
    }
    String legacyRequestKey = "team.membership.request-to-join";
    if (config.contains(legacyRequestKey)) {
      boolean requestToJoin = config.getBoolean(legacyRequestKey);
      config.set(
          Keys.Team.Membership.JOIN_MODE,
          requestToJoin ? JoinMode.REQUEST_TO_JOIN.name() : JoinMode.OPEN.name());
      config.set(legacyRequestKey, null);
      return true;
    }
    String legacyAutoAccept = "team.membership.auto-accept";
    if (config.contains(legacyAutoAccept)) {
      boolean autoAccept = config.getBoolean(legacyAutoAccept);
      config.set(
          Keys.Team.Membership.JOIN_MODE,
          autoAccept ? JoinMode.OPEN.name() : JoinMode.INVITE_ONLY.name());
      config.set(legacyAutoAccept, null);
      return true;
    }
    return false;
  }

  private boolean getBooleanWithLegacyFallback(
      @NotNull String path, boolean defaultValue, String... legacyPaths) {
    if (config.contains(path)) {
      return config.getBoolean(path, defaultValue);
    }
    for (String legacyPath : legacyPaths) {
      if (config.contains(legacyPath)) {
        return config.getBoolean(legacyPath, defaultValue);
      }
    }
    return defaultValue;
  }

  private int getIntWithLegacyFallback(
      @NotNull String path, int defaultValue, String... legacyPaths) {
    if (config.contains(path)) {
      return config.getInt(path, defaultValue);
    }
    for (String legacyPath : legacyPaths) {
      if (config.contains(legacyPath)) {
        return config.getInt(legacyPath, defaultValue);
      }
    }
    return defaultValue;
  }

  private long getLongWithLegacyFallback(
      @NotNull String path, long defaultValue, String... legacyPaths) {
    if (config.contains(path)) {
      return config.getLong(path, defaultValue);
    }
    for (String legacyPath : legacyPaths) {
      if (config.contains(legacyPath)) {
        return config.getLong(legacyPath, defaultValue);
      }
    }
    return defaultValue;
  }

  private double getDoubleWithLegacyFallback(
      @NotNull String path, double defaultValue, String... legacyPaths) {
    if (config.contains(path)) {
      return config.getDouble(path, defaultValue);
    }
    for (String legacyPath : legacyPaths) {
      if (config.contains(legacyPath)) {
        return config.getDouble(legacyPath, defaultValue);
      }
    }
    return defaultValue;
  }

  private String getStringWithLegacyFallback(
      @NotNull String path, String defaultValue, String... legacyPaths) {
    if (config.contains(path)) {
      return config.getString(path, defaultValue);
    }
    for (String legacyPath : legacyPaths) {
      if (config.contains(legacyPath)) {
        String value = config.getString(legacyPath, defaultValue);
        return value != null ? value : defaultValue;
      }
    }
    return defaultValue;
  }

  /** Сохраняет файл конфигурации. */
  private void saveConfig() {
    synchronized (saveLock) {
      try {
        config.save(configFile);
      } catch (IOException e) {
        plugin.getLogger().severe("Ошибка при сохранении config.yml: " + e.getMessage());
      }
    }
  }

  /** Перезагружает конфигурацию из файла. */
  public void reloadConfig() {
    flushIfDirty();
    config = YamlConfiguration.loadConfiguration(configFile);
    dirty = false;
    setDefaults(true);
  }

  /** Обновляет состояние флага debug.mode и помечает конфигурацию как изменённую. */
  public void updateDebugMode(boolean enabled) {
    boolean current = config.getBoolean(Keys.DEBUG_MODE, false);
    if (current == enabled) {
      return;
    }
    config.set(Keys.DEBUG_MODE, enabled);
    markDirty();
  }

  private void markDirty() {
    dirty = true;
  }

  /** Запускает периодическое сохранение конфигурации. */
  public void startAutoSave(long intervalSeconds) {
    stopAutoSave();
    if (intervalSeconds <= 0) {
      return;
    }
    long ticks = Math.max(1L, intervalSeconds) * 20L;
    autoSaveTask =
        plugin
            .getServer()
            .getScheduler()
            .runTaskTimerAsynchronously(plugin, this::flushIfDirty, ticks, ticks);
  }

  /** Останавливает периодическое сохранение конфигурации. */
  public void stopAutoSave() {
    if (autoSaveTask != null) {
      autoSaveTask.cancel();
      autoSaveTask = null;
    }
  }

  /** Сохраняет конфигурацию, если с момента последнего сохранения были изменения. */
  public void flushIfDirty() {
    if (!dirty) {
      return;
    }
    synchronized (saveLock) {
      if (!dirty) {
        return;
      }
      saveConfig();
      dirty = false;
    }
  }

  /** Останавливает автосохранение и гарантирует запись несохранённых изменений на диск. */
  public void shutdown() {
    stopAutoSave();
    flushIfDirty();
  }

  /**
   * Проверяет, включён ли режим отладки.
   *
   * @return true, если режим отладки активен, иначе false
   */
  public boolean isDebugModeEnabled() {
    return getBooleanWithLegacyFallback(Keys.DEBUG_MODE, false, "debug-mode");
  }

  /**
   * Определяет, следует ли принудительно перекрашивать сообщения чата в белый цвет.
   *
   * @return true, если сообщения должны быть перекрашены в белый цвет
   */
  public boolean isForceWhiteChat() {
    return getBooleanWithLegacyFallback(Keys.CHAT_FORCE_WHITE, false, "force-white-chat");
  }

  /**
   * Проверяет, требуется ли OP для выполнения команд /team.
   *
   * @return true, если требуется OP, иначе false
   */
  public boolean isTeamCommandRequiresOp() {
    return getBooleanWithLegacyFallback(Keys.Team.Commands.REQUIRES_OP, false, "team.requires-op");
  }

  /**
   * Проверяет, должны ли администраторы получать уведомления о действиях с командами.
   *
   * @return true, если уведомления включены, иначе false
   */
  public boolean shouldNotifyAdmins() {
    return getBooleanWithLegacyFallback(
        Keys.Team.Notifications.NOTIFY_ADMINS, true, "team.notify-admins");
  }

  /**
   * Получает максимальное количество участников в команде.
   *
   * @return Максимальное количество участников (0 — без ограничений)
   */
  public int getMaxMembers() {
    return getIntWithLegacyFallback(Keys.Team.Membership.MAX_MEMBERS, 0, "team.max-members");
  }

  /**
   * Возвращает политику вступления в команды.
   *
   * @return выбранный режим вступления
   */
  public @NotNull JoinMode getJoinMode() {
    String rawValue =
        getStringWithLegacyFallback(
            Keys.Team.Membership.JOIN_MODE, JoinMode.OPEN.name(), "team.join-mode");
    if (rawValue == null) {
      return JoinMode.OPEN;
    }
    String normalized =
        rawValue.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
    try {
      return JoinMode.valueOf(normalized);
    } catch (IllegalArgumentException ex) {
      plugin
          .getLogger()
          .warning(
              "Некорректное значение "
                  + Keys.Team.Membership.JOIN_MODE
                  + ": "
                  + rawValue
                  + ". Используем OPEN.");
      return JoinMode.OPEN;
    }
  }

  /**
   * Проверяет, следует ли применять ограничение по количеству участников при перезагрузке.
   *
   * @return true, если ограничение должно применяться
   */
  public boolean isEnforceMaxMembersOnReload() {
    return getBooleanWithLegacyFallback(
        Keys.Team.Membership.ENFORCE_MAX_ON_RELOAD, true, "team.enforce-max-members-on-reload");
  }

  /**
   * Проверяет, включён ли льготный период перед удалением лишних участников.
   *
   * @return true, если льготный период активен
   */
  public boolean isGracePeriodEnabled() {
    return getBooleanWithLegacyFallback(
        Keys.Team.Membership.GracePeriod.ENABLED, true, "team.grace-period-enabled");
  }

  /**
   * Получает длительность льготного периода в минутах.
   *
   * @return длительность периода в минутах
   */
  public int getGracePeriodMinutes() {
    return getIntWithLegacyFallback(
        Keys.Team.Membership.GracePeriod.MINUTES, 10, "team.grace-period-minutes");
  }

  /**
   * Получает период проверки дедлайнов в секундах.
   *
   * @return период проверки в секундах
   */
  public long getDeadlineNotifyPeriodSeconds() {
    return getLongWithLegacyFallback(
        Keys.Team.Deadlines.NOTIFY_PERIOD_SECONDS, 300L, "team.deadline-notify-period-seconds");
  }

  /**
   * Получает интервал автосохранения команд в секундах.
   *
   * @return интервал автосохранения
   */
  public long getSaveIntervalSeconds() {
    return getLongWithLegacyFallback(
        Keys.Team.Storage.SAVE_INTERVAL_SECONDS, 60L, "team.save-interval-seconds");
  }

  /**
   * Получает способ отображения уведомлений о дедлайне.
   *
   * @return режим отображения
   */
  public String getDeadlineDisplayMode() {
    return getStringWithLegacyFallback(
        Keys.Team.Deadlines.DISPLAY_MODE, "CHAT", "team.deadline-display-mode");
  }

  /**
   * Возвращает стратегию удаления лишних игроков в команде.
   *
   * @return стратегия удаления игроков
   */
  public @NotNull RemovalPolicy getExcessPlayerRemovalPolicy() {
    String rawValue =
        getStringWithLegacyFallback(
            Keys.Team.Deadlines.REMOVAL_POLICY, "last-joined", "team.deadline-removal-policy");
    String normalized = rawValue.trim().replace('-', '_').toUpperCase(Locale.ROOT);
    try {
      return RemovalPolicy.valueOf(normalized);
    } catch (IllegalArgumentException ex) {
      plugin
          .getLogger()
          .warning(
              "Некорректное значение "
                  + Keys.Team.Deadlines.REMOVAL_POLICY
                  + ": "
                  + rawValue
                  + ". Используем LAST_JOINED.");
      return RemovalPolicy.LAST_JOINED;
    }
  }

  /**
   * Получает минимальную длину префикса команды.
   *
   * @return Минимальная длина префикса
   */
  public int getMinPrefixLength() {
    return getIntWithLegacyFallback(
        Keys.Team.Naming.Prefix.MIN_LENGTH, 1, "team.min-prefix-length");
  }

  /**
   * Получает максимальную длину префикса команды.
   *
   * @return Максимальная длина префикса
   */
  public int getMaxPrefixLength() {
    return getIntWithLegacyFallback(
        Keys.Team.Naming.Prefix.MAX_LENGTH, 16, "team.max-prefix-length");
  }

  /**
   * Получает минимальную длину названия команды.
   *
   * @return Минимальная длина названия команды
   */
  public int getMinTeamNameLength() {
    return getIntWithLegacyFallback(
        Keys.Team.Naming.TeamName.MIN_LENGTH, 3, "team.min-team-name-length");
  }

  /**
   * Получает максимальную длину названия команды.
   *
   * @return Максимальная длина названия команды
   */
  public int getMaxTeamNameLength() {
    return getIntWithLegacyFallback(
        Keys.Team.Naming.TeamName.MAX_LENGTH, 32, "team.max-team-name-length");
  }

  /**
   * Получает звук, воспроизводимый при открытии меню.
   *
   * @return Название звука
   */
  public String getMenuOpenSound() {
    return getStringWithLegacyFallback(
        Keys.Menu.Sound.OPEN, "BLOCK_NOTE_BLOCK_PLING", "menu.open-sound");
  }

  /**
   * Получает эффект частиц, отображаемый при открытии меню.
   *
   * @return Название эффекта частиц
   */
  public String getMenuParticleEffect() {
    return getStringWithLegacyFallback(
        Keys.Menu.PARTICLE_EFFECT, "FIREWORK", "menu.particle-effect");
  }

  /**
   * Получает громкость звука при открытии меню.
   *
   * @return Громкость звука
   */
  public double getMenuSoundVolume() {
    return getDoubleWithLegacyFallback(Keys.Menu.Sound.VOLUME, 1.0, "menu.sound-volume");
  }

  /**
   * Получает высоту звука при открытии меню.
   *
   * @return Высота звука
   */
  public double getMenuSoundPitch() {
    return getDoubleWithLegacyFallback(Keys.Menu.Sound.PITCH, 1.0, "menu.sound-pitch");
  }

  // --- Настройки локального чата ---

  public boolean isLocalChatEnabled() {
    return config.getBoolean(Keys.Chat.Local.ENABLED, true);
  }

  public boolean isLocalChatObfuscationEnabled() {
    return config.getBoolean(Keys.Chat.Local.OBFUSCATE_FALLOFF, true);
  }

  public java.util.List<String> getLocalChatObfuscationChars() {
    return config.getStringList(Keys.Chat.Local.OBFUSCATION_CHARS);
  }

  public String getWhisperPrefix() {
    return config.getString(Keys.Chat.Local.WHISPER_PREFIX, "-");
  }

  public double getWhisperRadius() {
    return config.getDouble(Keys.Chat.Local.WHISPER_RADIUS, 2.5);
  }

  public double getWhisperFalloff() {
    return config.getDouble(Keys.Chat.Local.WHISPER_FALLOFF, 1.5);
  }

  public String getWhisperFormat() {
    return config.getString(Keys.Chat.Local.WHISPER_FORMAT, "< Шепотом >");
  }

  public String getTalkPrefix() {
    return config.getString(Keys.Chat.Local.TALK_PREFIX, "");
  }

  public double getTalkRadius() {
    return config.getDouble(Keys.Chat.Local.TALK_RADIUS, 16.0);
  }

  public double getTalkFalloff() {
    return config.getDouble(Keys.Chat.Local.TALK_FALLOFF, 4.0);
  }

  public String getTalkFormat() {
    return config.getString(Keys.Chat.Local.TALK_FORMAT, "");
  }

  public String getYellPrefix() {
    return config.getString(Keys.Chat.Local.YELL_PREFIX, "+");
  }

  public double getYellRadius() {
    return config.getDouble(Keys.Chat.Local.YELL_RADIUS, 32.0);
  }

  public double getYellFalloff() {
    return config.getDouble(Keys.Chat.Local.YELL_FALLOFF, 6.0);
  }

  public String getYellFormat() {
    return config.getString(Keys.Chat.Local.YELL_FORMAT, "< Громко >");
  }

  public String getScreamPrefix() {
    return config.getString(Keys.Chat.Local.SCREAM_PREFIX, "++");
  }

  public double getScreamRadius() {
    return config.getDouble(Keys.Chat.Local.SCREAM_RADIUS, 48.0);
  }

  public double getScreamFalloff() {
    return config.getDouble(Keys.Chat.Local.SCREAM_FALLOFF, 8.0);
  }

  public String getScreamFormat() {
    return config.getString(Keys.Chat.Local.SCREAM_FORMAT, "< Крик >");
  }
}

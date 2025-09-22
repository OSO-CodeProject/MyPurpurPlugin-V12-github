package org.example.config;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
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
  }

  private final JavaPlugin plugin;
  private FileConfiguration config;
  private File configFile;

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
    setDefaults(true);
  }

  /** Устанавливает значения по умолчанию для конфигурации. */
  private void setDefaults(boolean persistChanges) {
    boolean newKeysAdded = false;

    // Миграция старых ключей
    newKeysAdded |= migrateLegacyKey("debug-mode", Keys.DEBUG_MODE);
    newKeysAdded |= migrateLegacyKey("force-white-chat", Keys.CHAT_FORCE_WHITE);
    newKeysAdded |= migrateLegacyKey("team.requires-op", Keys.Team.Commands.REQUIRES_OP);
    newKeysAdded |= migrateLegacyKey("team.notify-admins", Keys.Team.Notifications.NOTIFY_ADMINS);
    newKeysAdded |= migrateLegacyKey("team.max-members", Keys.Team.Membership.MAX_MEMBERS);
    newKeysAdded |=
        migrateLegacyKey(
            "team.enforce-max-members-on-reload", Keys.Team.Membership.ENFORCE_MAX_ON_RELOAD);
    newKeysAdded |=
        migrateLegacyKey("team.grace-period-enabled", Keys.Team.Membership.GracePeriod.ENABLED);
    newKeysAdded |=
        migrateLegacyKey("team.grace-period-minutes", Keys.Team.Membership.GracePeriod.MINUTES);
    newKeysAdded |= migrateLegacyKey("team.min-prefix-length", Keys.Team.Naming.Prefix.MIN_LENGTH);
    newKeysAdded |= migrateLegacyKey("team.max-prefix-length", Keys.Team.Naming.Prefix.MAX_LENGTH);
    newKeysAdded |=
        migrateLegacyKey("team.min-team-name-length", Keys.Team.Naming.TeamName.MIN_LENGTH);
    newKeysAdded |=
        migrateLegacyKey("team.max-team-name-length", Keys.Team.Naming.TeamName.MAX_LENGTH);
    newKeysAdded |=
        migrateLegacyKey(
            "team.deadline-notify-period-seconds", Keys.Team.Deadlines.NOTIFY_PERIOD_SECONDS);
    newKeysAdded |=
        migrateLegacyKey("team.deadline-display-mode", Keys.Team.Deadlines.DISPLAY_MODE);
    newKeysAdded |=
        migrateLegacyKey("team.deadline-removal-policy", Keys.Team.Deadlines.REMOVAL_POLICY);
    newKeysAdded |=
        migrateLegacyKey("team.save-interval-seconds", Keys.Team.Storage.SAVE_INTERVAL_SECONDS);
    newKeysAdded |= migrateLegacyKey("menu.open-sound", Keys.Menu.Sound.OPEN);
    newKeysAdded |= migrateLegacyKey("menu.sound-volume", Keys.Menu.Sound.VOLUME);
    newKeysAdded |= migrateLegacyKey("menu.sound-pitch", Keys.Menu.Sound.PITCH);

    // Глобальные настройки
    newKeysAdded |= addDefaultIfMissing(Keys.DEBUG_MODE, true);
    newKeysAdded |= addDefaultIfMissing(Keys.CHAT_FORCE_WHITE, false);

    // Основные настройки
    newKeysAdded |= addDefaultIfMissing(Keys.Team.Commands.REQUIRES_OP, false);
    newKeysAdded |= addDefaultIfMissing(Keys.Team.Notifications.NOTIFY_ADMINS, true);
    newKeysAdded |= addDefaultIfMissing(Keys.Team.Membership.MAX_MEMBERS, 0);
    newKeysAdded |= addDefaultIfMissing(Keys.Team.Naming.Prefix.MIN_LENGTH, 1);
    newKeysAdded |= addDefaultIfMissing(Keys.Team.Naming.Prefix.MAX_LENGTH, 16);
    newKeysAdded |= addDefaultIfMissing(Keys.Team.Naming.TeamName.MIN_LENGTH, 3);
    newKeysAdded |= addDefaultIfMissing(Keys.Team.Naming.TeamName.MAX_LENGTH, 32);
    newKeysAdded |= addDefaultIfMissing(Keys.Team.Membership.ENFORCE_MAX_ON_RELOAD, true);
    newKeysAdded |= addDefaultIfMissing(Keys.Team.Membership.GracePeriod.ENABLED, true);
    newKeysAdded |= addDefaultIfMissing(Keys.Team.Membership.GracePeriod.MINUTES, 10);
    newKeysAdded |= addDefaultIfMissing(Keys.Team.Deadlines.NOTIFY_PERIOD_SECONDS, 300L);
    newKeysAdded |= addDefaultIfMissing(Keys.Team.Storage.SAVE_INTERVAL_SECONDS, 60L);
    newKeysAdded |= addDefaultIfMissing(Keys.Team.Deadlines.DISPLAY_MODE, "CHAT");
    newKeysAdded |= addDefaultIfMissing(Keys.Team.Deadlines.REMOVAL_POLICY, "last-joined");

    // Настройки меню
    newKeysAdded |= addDefaultIfMissing(Keys.Menu.Sound.OPEN, "BLOCK_NOTE_BLOCK_PLING");
    newKeysAdded |= addDefaultIfMissing(Keys.Menu.PARTICLE_EFFECT, "FIREWORK");
    newKeysAdded |= addDefaultIfMissing(Keys.Menu.Sound.VOLUME, 1.0);
    newKeysAdded |= addDefaultIfMissing(Keys.Menu.Sound.PITCH, 1.0);

    config.options().copyDefaults(true);

    if (persistChanges && newKeysAdded) {
      saveConfig();
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
    try {
      config.save(configFile);
    } catch (IOException e) {
      plugin.getLogger().severe("Ошибка при сохранении config.yml: " + e.getMessage());
    }
  }

  /** Перезагружает конфигурацию из файла. */
  public void reloadConfig() {
    config = YamlConfiguration.loadConfiguration(configFile);
    setDefaults(false);
  }

  /**
   * Проверяет, включён ли режим отладки.
   *
   * @return true, если режим отладки активен, иначе false
   */
  public boolean isDebugModeEnabled() {
    return getBooleanWithLegacyFallback(Keys.DEBUG_MODE, true, "debug-mode");
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
}

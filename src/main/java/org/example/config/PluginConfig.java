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

    // Глобальные настройки
    newKeysAdded |= addDefaultIfMissing("debug-mode", true);
    newKeysAdded |= addDefaultIfMissing("force-white-chat", false);

    // Основные настройки
    newKeysAdded |= addDefaultIfMissing("team.requires-op", false);
    newKeysAdded |= addDefaultIfMissing("team.notify-admins", true);
    newKeysAdded |= addDefaultIfMissing("team.max-members", 0);
    newKeysAdded |= addDefaultIfMissing("team.min-prefix-length", 1);
    newKeysAdded |= addDefaultIfMissing("team.max-prefix-length", 16);
    newKeysAdded |= addDefaultIfMissing("team.min-team-name-length", 3);
    newKeysAdded |= addDefaultIfMissing("team.max-team-name-length", 32);
    newKeysAdded |= addDefaultIfMissing("team.enforce-max-members-on-reload", true);
    newKeysAdded |= addDefaultIfMissing("team.grace-period-enabled", true);
    newKeysAdded |= addDefaultIfMissing("team.grace-period-minutes", 10);
    newKeysAdded |= addDefaultIfMissing("team.deadline-notify-period-seconds", 300L);
    newKeysAdded |= addDefaultIfMissing("team.save-interval-seconds", 60L);
    newKeysAdded |= addDefaultIfMissing("team.deadline-display-mode", "CHAT");
    newKeysAdded |= addDefaultIfMissing("team.deadline-removal-policy", "last-joined");

    // Настройки меню
    newKeysAdded |= addDefaultIfMissing("menu.open-sound", "BLOCK_NOTE_BLOCK_PLING");
    newKeysAdded |= addDefaultIfMissing("menu.particle-effect", "FIREWORK");
    newKeysAdded |= addDefaultIfMissing("menu.sound-volume", 1.0);
    newKeysAdded |= addDefaultIfMissing("menu.sound-pitch", 1.0);

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
    return config.getBoolean("debug-mode", true);
  }

  /**
   * Определяет, следует ли принудительно перекрашивать сообщения чата в белый цвет.
   *
   * @return true, если сообщения должны быть перекрашены в белый цвет
   */
  public boolean isForceWhiteChat() {
    return config.getBoolean("force-white-chat", false);
  }

  /**
   * Проверяет, требуется ли OP для выполнения команд /team.
   *
   * @return true, если требуется OP, иначе false
   */
  public boolean isTeamCommandRequiresOp() {
    return config.getBoolean("team.requires-op", false);
  }

  /**
   * Проверяет, должны ли администраторы получать уведомления о действиях с командами.
   *
   * @return true, если уведомления включены, иначе false
   */
  public boolean shouldNotifyAdmins() {
    return config.getBoolean("team.notify-admins", true);
  }

  /**
   * Получает максимальное количество участников в команде.
   *
   * @return Максимальное количество участников (0 — без ограничений)
   */
  public int getMaxMembers() {
    return config.getInt("team.max-members", 0);
  }

  /**
   * Проверяет, следует ли применять ограничение по количеству участников при перезагрузке.
   *
   * @return true, если ограничение должно применяться
   */
  public boolean isEnforceMaxMembersOnReload() {
    return config.getBoolean("team.enforce-max-members-on-reload", true);
  }

  /**
   * Проверяет, включён ли льготный период перед удалением лишних участников.
   *
   * @return true, если льготный период активен
   */
  public boolean isGracePeriodEnabled() {
    return config.getBoolean("team.grace-period-enabled", true);
  }

  /**
   * Получает длительность льготного периода в минутах.
   *
   * @return длительность периода в минутах
   */
  public int getGracePeriodMinutes() {
    return config.getInt("team.grace-period-minutes", 10);
  }

  /**
   * Получает период проверки дедлайнов в секундах.
   *
   * @return период проверки в секундах
   */
  public long getDeadlineNotifyPeriodSeconds() {
    return config.getLong("team.deadline-notify-period-seconds", 300L);
  }

  /**
   * Получает интервал автосохранения команд в секундах.
   *
   * @return интервал автосохранения
   */
  public long getSaveIntervalSeconds() {
    return config.getLong("team.save-interval-seconds", 60L);
  }

  /**
   * Получает способ отображения уведомлений о дедлайне.
   *
   * @return режим отображения
   */
  public String getDeadlineDisplayMode() {
    return config.getString("team.deadline-display-mode", "CHAT");
  }

  /**
   * Возвращает стратегию удаления лишних игроков в команде.
   *
   * @return стратегия удаления игроков
   */
  public @NotNull RemovalPolicy getExcessPlayerRemovalPolicy() {
    String rawValue = config.getString("team.deadline-removal-policy", "last-joined");
    String normalized = rawValue.trim().replace('-', '_').toUpperCase(Locale.ROOT);
    try {
      return RemovalPolicy.valueOf(normalized);
    } catch (IllegalArgumentException ex) {
      plugin
          .getLogger()
          .warning(
              "Некорректное значение team.deadline-removal-policy: "
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
    return config.getInt("team.min-prefix-length", 1);
  }

  /**
   * Получает максимальную длину префикса команды.
   *
   * @return Максимальная длина префикса
   */
  public int getMaxPrefixLength() {
    return config.getInt("team.max-prefix-length", 16);
  }

  /**
   * Получает минимальную длину названия команды.
   *
   * @return Минимальная длина названия команды
   */
  public int getMinTeamNameLength() {
    return config.getInt("team.min-team-name-length", 3);
  }

  /**
   * Получает максимальную длину названия команды.
   *
   * @return Максимальная длина названия команды
   */
  public int getMaxTeamNameLength() {
    return config.getInt("team.max-team-name-length", 32);
  }

  /**
   * Получает звук, воспроизводимый при открытии меню.
   *
   * @return Название звука
   */
  public String getMenuOpenSound() {
    return config.getString("menu.open-sound", "BLOCK_NOTE_BLOCK_PLING");
  }

  /**
   * Получает эффект частиц, отображаемый при открытии меню.
   *
   * @return Название эффекта частиц
   */
  public String getMenuParticleEffect() {
    return config.getString("menu.particle-effect", "FIREWORK");
  }

  /**
   * Получает громкость звука при открытии меню.
   *
   * @return Громкость звука
   */
  public double getMenuSoundVolume() {
    return config.getDouble("menu.sound-volume", 1.0);
  }

  /**
   * Получает высоту звука при открытии меню.
   *
   * @return Высота звука
   */
  public double getMenuSoundPitch() {
    return config.getDouble("menu.sound-pitch", 1.0);
  }
}

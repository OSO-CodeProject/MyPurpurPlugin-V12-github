package org.example;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

/**
 * Класс для управления конфигурацией плагина.
 */
public class PluginConfig {

    private final JavaPlugin plugin;
    private FileConfiguration config;
    private File configFile;

    public PluginConfig(@NotNull JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    /**
     * Загружает или создаёт файл конфигурации.
     */
    private void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "config.yml");

        // Создаём папку плагина, если она не существует
        if (!plugin.getDataFolder().exists()) {
            if (!plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().severe("Не удалось создать папку плагина: " + plugin.getDataFolder().getAbsolutePath());
            }
        }

        // Создаём файл конфигурации, если он не существует
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);
        setDefaults();
    }

    /**
     * Устанавливает значения по умолчанию для конфигурации.
     */
    private void setDefaults() {
        // Основные настройки
        config.addDefault("team-command-requires-op", false);
        config.addDefault("notify-admins", true);
        config.addDefault("max-members", 5);
        config.addDefault("min-prefix-length", 2);
        config.addDefault("max-prefix-length", 5);
        config.addDefault("min-team-name-length", 3);
        config.addDefault("max-team-name-length", 16);
        config.addDefault("team.enforce-max-members-on-reload", true);
        config.addDefault("team.grace-period-enabled", true);
        config.addDefault("team.grace-period-minutes", 10);
        config.addDefault("team.deadline-notify-period-seconds", 300L);
        config.addDefault("team.deadline-display-mode", "CHAT");

        // Настройки меню
        config.addDefault("menu.sound", "BLOCK_NOTE_BLOCK_PLING");
        config.addDefault("menu.particle", "FIREWORK");
        config.addDefault("menu.sound-volume", 1.0);
        config.addDefault("menu.sound-pitch", 1.0);

        config.options().copyDefaults(true);
        saveConfig();
    }

    /**
     * Сохраняет файл конфигурации.
     */
    private void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Ошибка при сохранении config.yml: " + e.getMessage());
        }
    }

    /**
     * Перезагружает конфигурацию из файла.
     */
    public void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    /**
     * Проверяет, требуется ли OP для выполнения команд /team.
     *
     * @return true, если требуется OP, иначе false
     */
    public boolean isTeamCommandRequiresOp() {
        return config.getBoolean("team-command-requires-op", false);
    }

    /**
     * Проверяет, должны ли администраторы получать уведомления о действиях с командами.
     *
     * @return true, если уведомления включены, иначе false
     */
    public boolean shouldNotifyAdmins() {
        return config.getBoolean("notify-admins", true);
    }

    /**
     * Получает максимальное количество участников в команде.
     *
     * @return Максимальное количество участников (0 — без ограничений)
     */
    public int getMaxMembers() {
        return config.getInt("max-members", 5);
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
     * Проверяет, включён ли льготный период для удаления лишних участников.
     *
     * @return true, если льготный период включён
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
     * Получает способ отображения уведомлений о дедлайне.
     *
     * @return режим отображения
     */
    public String getDeadlineDisplayMode() {
        return config.getString("team.deadline-display-mode", "CHAT");
    }

    /**
     * Получает минимальную длину префикса команды.
     *
     * @return Минимальная длина префикса
     */
    public int getMinPrefixLength() {
        return config.getInt("min-prefix-length", 2);
    }

    /**
     * Получает максимальную длину префикса команды.
     *
     * @return Максимальная длина префикса
     */
    public int getMaxPrefixLength() {
        return config.getInt("max-prefix-length", 5);
    }

    /**
     * Получает минимальную длину названия команды.
     *
     * @return Минимальная длина названия команды
     */
    public int getMinTeamNameLength() {
        return config.getInt("min-team-name-length", 3);
    }

    /**
     * Получает максимальную длину названия команды.
     *
     * @return Максимальная длина названия команды
     */
    public int getMaxTeamNameLength() {
        return config.getInt("max-team-name-length", 16);
    }

    /**
     * Получает звук, воспроизводимый при открытии меню.
     *
     * @return Название звука
     */
    public String getMenuOpenSound() {
        return config.getString("menu.sound", "BLOCK_NOTE_BLOCK_PLING");
    }

    /**
     * Получает эффект частиц, отображаемый при открытии меню.
     *
     * @return Название эффекта частиц
     */
    public String getMenuParticleEffect() {
        return config.getString("menu.particle", "FIREWORK");
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
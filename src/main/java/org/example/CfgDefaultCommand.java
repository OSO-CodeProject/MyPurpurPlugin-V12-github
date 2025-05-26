package org.example;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Обработчик команды /cfgDefault для сброса конфигурации плагина до дефолтных настроек.
 */
public class CfgDefaultCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final PluginConfig pluginConfig;
    private final TeamService teamManager;

    public CfgDefaultCommand(@NotNull JavaPlugin plugin, @NotNull PluginConfig pluginConfig, @NotNull TeamService teamManager) {
        this.plugin = plugin;
        this.pluginConfig = pluginConfig;
        this.teamManager = teamManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage(Component.text("❌ Эта команда доступна только из консоли!", NamedTextColor.RED));
            return true;
        }

        // Сбрасываем config.yml до дефолтного
        plugin.saveResource("config.yml", true);
        // Перезагружаем конфигурацию
        pluginConfig.reloadConfig();
        teamManager.reloadConfig();
        sender.sendMessage(Component.text("✅ Конфигурация плагина успешно сброшена до дефолтных настроек!", NamedTextColor.GREEN));
        return true;
    }
}
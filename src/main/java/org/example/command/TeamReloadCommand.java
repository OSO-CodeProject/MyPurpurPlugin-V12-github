package org.example.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.example.MyPurpurPlugin;
import org.example.config.PluginConfig;
import org.example.service.TeamService;
import org.jetbrains.annotations.NotNull;

/** Обработчик команды /teamreload для перезагрузки конфигурации плагина. */
public class TeamReloadCommand implements CommandExecutor {

  private final MyPurpurPlugin plugin;
  private final TeamService teamManager;
  private final PluginConfig pluginConfig;

  public TeamReloadCommand(
      @NotNull MyPurpurPlugin plugin,
      @NotNull TeamService teamManager,
      @NotNull PluginConfig pluginConfig) {
    this.plugin = plugin;
    this.teamManager = teamManager;
    this.pluginConfig = pluginConfig;
  }

  @Override
  public boolean onCommand(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String label,
      @NotNull String[] args) {
    if (!(sender instanceof ConsoleCommandSender)) {
      sender.sendMessage(
          Component.text("❌ Эта команда доступна только из консоли!", NamedTextColor.RED));
      return true;
    }

    // Перезагружаем конфигурацию
    pluginConfig.reloadConfig();
    // Перезагружаем состояние TeamManager
    teamManager.reloadConfig();
    plugin.applyDebugModeFromConfig();
    sender.sendMessage(
        Component.text("✅ Конфигурация плагина успешно перезагружена!", NamedTextColor.GREEN));
    return true;
  }
}

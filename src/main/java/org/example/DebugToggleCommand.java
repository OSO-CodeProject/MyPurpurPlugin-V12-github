package org.example;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.jetbrains.annotations.NotNull;

/**
 * Обработчик команды /debugtoggle для переключения режима отладки.
 */
public class DebugToggleCommand implements CommandExecutor {

    private final MyPurpurPlugin plugin;

    public DebugToggleCommand(@NotNull MyPurpurPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage(Component.text("❌ Эта команда доступна только из консоли!", NamedTextColor.RED));
            return true;
        }

        // Переключаем режим отладки
        plugin.toggleDebugMode();
        return true;
    }
}
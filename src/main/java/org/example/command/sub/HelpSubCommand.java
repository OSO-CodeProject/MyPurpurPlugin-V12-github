package org.example.command.sub;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** Подкоманда /team help. */
public class HelpSubCommand implements SubCommand {

  @Override
  public boolean execute(@NotNull Player player, @NotNull String[] args) {
    player.sendMessage(Component.text(""));
    player.sendMessage(Component.text("ℹ Использование /team:", NamedTextColor.AQUA));
    player.sendMessage(Component.text(""));
    player.sendMessage(
        Component.text(
            "/team create <название> <префикс> <цвет> — создать команду (цвет: RED, BLUE, GREEN и т.д.)",
            NamedTextColor.AQUA));
    player.sendMessage(
        Component.text("/team join <название> — вступить в команду", NamedTextColor.AQUA));
    player.sendMessage(Component.text("/team leave — покинуть команду", NamedTextColor.AQUA));
    player.sendMessage(
        Component.text("/team list — показать список всех команд", NamedTextColor.AQUA));
    player.sendMessage(
        Component.text("/team members — показать участников вашей команды", NamedTextColor.AQUA));
    player.sendMessage(Component.text("/team help — показать эту справку", NamedTextColor.AQUA));
    player.sendMessage(Component.text(""));
    return true;
  }
}

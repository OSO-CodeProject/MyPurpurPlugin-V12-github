package org.example.command.sub;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** Подкоманда /team help. */
public class HelpSubCommand implements SubCommand {

  @Override
  public boolean execute(@NotNull Player player, @NotNull String[] args) {
    Component helpMessage =
        joinLines(
            heading("ℹ Использование /team:"),
            Component.empty(),
            sectionHeading("Основные команды"),
            bullet(
                "/team create <название> <префикс> <цвет>",
                "создать команду (цвет: RED, BLUE, GREEN и т.д.)"),
            bullet("/team join <название>", "вступить в команду"),
            bullet("/team leave", "покинуть команду"),
            bullet("/team help", "показать эту справку"),
            Component.empty(),
            sectionHeading("Управление командой"),
            bullet("/team list", "показать список всех команд"),
            bullet("/team members", "показать участников вашей команды"),
            bullet("/team uninvite <игрок>", "отозвать активное приглашение"));

    player.sendMessage(helpMessage);
    return true;
  }

  private static Component heading(String text) {
    return Component.text(text, NamedTextColor.AQUA).decorate(TextDecoration.BOLD);
  }

  private static Component sectionHeading(String text) {
    return Component.text(text, NamedTextColor.AQUA).decorate(TextDecoration.BOLD);
  }

  private static Component bullet(String command, String description) {
    return Component.text("• ", NamedTextColor.DARK_GRAY)
        .append(Component.text(command, NamedTextColor.GOLD))
        .append(Component.text(" — " + description, NamedTextColor.WHITE));
  }

  private static Component joinLines(Component... components) {
    return Component.join(JoinConfiguration.newlines(), components);
  }
}

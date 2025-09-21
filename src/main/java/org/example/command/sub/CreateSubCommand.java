package org.example.command.sub;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.example.service.TeamService;
import org.jetbrains.annotations.NotNull;

/** Подкоманда /team create. */
public class CreateSubCommand implements SubCommand {

  private final TeamService teamService;

  public CreateSubCommand(@NotNull TeamService teamService) {
    this.teamService = teamService;
  }

  @Override
  public boolean execute(@NotNull Player player, @NotNull String[] args) {
    if (args.length < 4) {
      player.sendMessage(
          Component.text(
              "❌ Использование: /team create <название> <префикс> <цвет> (цвет: RED, BLUE, GREEN и т.д.)",
              NamedTextColor.RED));
      return true;
    }
    String teamName = args[1].trim();
    String prefix = args[2].trim();
    String color = args[3].trim();
    teamService.createTeam(teamName, prefix, color, player);
    return true;
  }

  @Override
  public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
    List<String> suggestions = new ArrayList<>();
    if (args.length == 2) {
      suggestions.add("<название>");
    } else if (args.length == 3) {
      suggestions.add("<префикс>");
    } else if (args.length == 4) {
      String partial = args[3].toLowerCase(Locale.ROOT);
      for (NamedTextColor color : NamedTextColor.NAMES.values()) {
        String key = NamedTextColor.NAMES.key(color);
        if (key == null) {
          continue;
        }
        String keyLower = key.toLowerCase(Locale.ROOT);
        if (keyLower.startsWith(partial)) {
          suggestions.add(key.toUpperCase(Locale.ROOT));
        }
      }
      suggestions.sort(String::compareTo);
    }
    return suggestions;
  }
}

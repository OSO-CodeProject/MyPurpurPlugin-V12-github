package org.example.command.sub;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.example.service.TeamService;
import org.jetbrains.annotations.NotNull;

/** Подкоманда /team join. */
public class JoinSubCommand implements SubCommand {

  private final TeamService teamService;

  public JoinSubCommand(@NotNull TeamService teamService) {
    this.teamService = teamService;
  }

  @Override
  public boolean execute(@NotNull Player player, @NotNull String[] args) {
    if (args.length < 2) {
      player.sendMessage(
          Component.text("❌ Использование: /team join <название>", NamedTextColor.RED));
      return true;
    }
    teamService.addPlayerToTeam(args[1], player);
    return true;
  }

  @Override
  public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
    List<String> suggestions = new ArrayList<>();
    if (!(sender instanceof Player player)) {
      return suggestions;
    }
    if (args.length == 2) {
      String playerTeam = teamService.getPlayerTeam(player);
      if (playerTeam == null) {
        String partial = args[1].toLowerCase(Locale.ROOT);
        for (String teamName : teamService.getTeamNames()) {
          UUID leaderId = teamService.getTeamLeaderId(teamName);
          if (leaderId == null || !leaderId.equals(player.getUniqueId())) {
            if (teamName.toLowerCase(Locale.ROOT).startsWith(partial)) {
              suggestions.add(teamName);
            }
          }
        }
      }
    }
    return suggestions;
  }
}

package org.example.command.sub;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.example.model.PendingRequest;
import org.example.service.TeamService;
import org.jetbrains.annotations.NotNull;

/** Подкоманда /team cancelrequest. */
public class CancelRequestSubCommand implements SubCommand {

  private final TeamService teamService;

  public CancelRequestSubCommand(@NotNull TeamService teamService) {
    this.teamService = teamService;
  }

  @Override
  public boolean execute(@NotNull Player player, @NotNull String[] args) {
    if (args.length < 2) {
      player.sendMessage(
          Component.text("❌ Использование: /team cancelrequest <название>", NamedTextColor.RED));
      return true;
    }
    teamService.cancelJoinRequest(args[1].trim(), player);
    return true;
  }

  @Override
  public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
    List<String> suggestions = new ArrayList<>();
    if (!(sender instanceof Player player)) {
      return suggestions;
    }
    if (args.length == 2) {
      String partial = args[1].toLowerCase(Locale.ROOT);
      for (PendingRequest request : teamService.getJoinRequestsForPlayer(player.getUniqueId())) {
        String teamName = request.getTeamName();
        if (teamName.toLowerCase(Locale.ROOT).startsWith(partial)) {
          suggestions.add(teamName);
        }
      }
    }
    return suggestions;
  }
}

package org.example.command.sub;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.example.config.JoinMode;
import org.example.service.TeamService;
import org.example.util.TeamMessageUtils;
import org.jetbrains.annotations.NotNull;

/** Подкоманда /team uninvite. */
public class UninviteSubCommand implements SubCommand {

  private final TeamService teamService;

  public UninviteSubCommand(@NotNull TeamService teamService) {
    this.teamService = teamService;
  }

  @Override
  public boolean execute(@NotNull Player player, @NotNull String[] args) {
    if (teamService.getJoinMode() != JoinMode.INVITE_ONLY) {
      TeamMessageUtils.sendTeamMessage(player, TeamMessageUtils.invitesDisabledMessage());
      return true;
    }
    if (args.length < 2) {
      player.sendMessage(
          Component.text("❌ Использование: /team uninvite <игрок>", NamedTextColor.RED));
      return true;
    }
    String targetName = args[1];
    teamService.revokeInvite(player, targetName);
    return true;
  }

  @Override
  public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
    List<String> suggestions = new ArrayList<>();
    if (!(sender instanceof Player player)) {
      return suggestions;
    }
    if (teamService.getJoinMode() != JoinMode.INVITE_ONLY) {
      return suggestions;
    }
    if (args.length == 2) {
      String partial = args[1].toLowerCase(Locale.ROOT);
      Set<String> invited =
          teamService.getRevocableInviteTargets(player).stream()
              .filter(name -> name != null && !name.isBlank())
              .map(name -> name.toLowerCase(Locale.ROOT))
              .collect(Collectors.toCollection(LinkedHashSet::new));
      if (invited.isEmpty()) {
        return suggestions;
      }
      teamService
          .getPlugin()
          .getServer()
          .getOnlinePlayers()
          .stream()
          .filter(online -> !online.getUniqueId().equals(player.getUniqueId()))
          .forEach(
              online -> {
                String name = online.getName();
                if (name == null) {
                  return;
                }
                String lower = name.toLowerCase(Locale.ROOT);
                if (lower.startsWith(partial) && invited.contains(lower)) {
                  suggestions.add(name);
                }
              });
    }
    return suggestions;
  }
}

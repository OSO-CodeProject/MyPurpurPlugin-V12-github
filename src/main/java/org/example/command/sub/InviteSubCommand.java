package org.example.command.sub;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.example.config.JoinMode;
import org.example.service.TeamService;
import org.example.util.TeamMessageUtils;
import org.jetbrains.annotations.NotNull;

/** Подкоманда /team invite. */
public class InviteSubCommand implements SubCommand {

  private final TeamService teamService;

  public InviteSubCommand(@NotNull TeamService teamService) {
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
          Component.text("❌ Использование: /team invite <игрок> [минуты]", NamedTextColor.RED));
      return true;
    }
    String targetName = args[1];
    Player target = teamService.getPlugin().getServer().getPlayerExact(targetName);
    if (target == null) {
      TeamMessageUtils.sendTeamMessage(player, TeamMessageUtils.playerNotFoundMessage(targetName));
      return true;
    }

    Duration ttl = null;
    if (args.length >= 3) {
      try {
        long minutes = Long.parseLong(args[2]);
        if (minutes > 0) {
          ttl = Duration.ofMinutes(minutes);
        } else if (minutes == 0) {
          ttl = Duration.ZERO;
        } else {
          TeamMessageUtils.sendTeamMessage(player, TeamMessageUtils.invalidInviteDurationMessage());
          return true;
        }
      } catch (NumberFormatException ex) {
        TeamMessageUtils.sendTeamMessage(player, TeamMessageUtils.invalidInviteDurationMessage());
        return true;
      }
    }

    teamService.sendInvite(player, target, ttl);
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
      teamService.getPlugin().getServer().getOnlinePlayers().stream()
          .filter(online -> !online.getUniqueId().equals(player.getUniqueId()))
          .map(Player::getName)
          .filter(name -> name != null && name.toLowerCase(Locale.ROOT).startsWith(partial))
          .forEach(suggestions::add);
    } else if (args.length == 3) {
      suggestions.add("15");
      suggestions.add("30");
      suggestions.add("60");
    }
    return suggestions;
  }
}

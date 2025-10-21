package org.example.command.sub;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.example.config.JoinMode;
import org.example.model.PendingInvite;
import org.example.service.TeamService;
import org.example.util.TeamMessageUtils;
import org.jetbrains.annotations.NotNull;

/** Подкоманда /team accept. */
public class AcceptInviteSubCommand implements SubCommand {

  private final TeamService teamService;

  public AcceptInviteSubCommand(@NotNull TeamService teamService) {
    this.teamService = teamService;
  }

  @Override
  public boolean execute(@NotNull Player player, @NotNull String[] args) {
    if (teamService.getJoinMode() != JoinMode.INVITE_ONLY) {
      TeamMessageUtils.sendTeamMessage(player, TeamMessageUtils.invitesDisabledMessage());
      return true;
    }
    if (args.length < 2) {
      TeamMessageUtils.sendTeamMessage(player, TeamMessageUtils.acceptInviteUsageMessage());
      return true;
    }
    String teamName = args[1];
    teamService.acceptInvite(player, teamName);
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
      UUID playerId = player.getUniqueId();
      for (PendingInvite invite : teamService.getInvitesForPlayer(playerId)) {
        String teamName = invite.getTeamName();
        if (teamName.toLowerCase(Locale.ROOT).startsWith(partial)) {
          suggestions.add(teamName);
        }
      }
    }
    return suggestions;
  }
}


package org.example.command.sub;

import java.util.List;
import java.util.UUID;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.example.config.JoinMode;
import org.example.model.PendingInvite;
import org.example.service.TeamService;
import org.example.util.TeamMessageUtils;
import org.jetbrains.annotations.NotNull;

/** Подкоманда /team invites. */
public class InvitesSubCommand implements SubCommand {

  private final TeamService teamService;

  public InvitesSubCommand(@NotNull TeamService teamService) {
    this.teamService = teamService;
  }

  @Override
  public boolean execute(@NotNull Player player, @NotNull String[] args) {
    if (teamService.getJoinMode() != JoinMode.INVITE_ONLY) {
      TeamMessageUtils.sendTeamMessage(player, TeamMessageUtils.invitesDisabledMessage());
      return true;
    }
    UUID playerId = player.getUniqueId();
    List<PendingInvite> invites = teamService.getInvitesForPlayer(playerId);
    if (invites.isEmpty()) {
      TeamMessageUtils.sendTeamMessage(player, TeamMessageUtils.noInvitesMessage());
      return true;
    }
    TeamMessageUtils.sendTeamMessage(player, TeamMessageUtils.invitesHeaderMessage());
    for (PendingInvite invite : invites) {
      String teamName = invite.getTeamName();
      String acceptCommand = "/team accept " + teamName;
      String declineCommand = "/team decline " + teamName;
      TeamMessageUtils.sendTeamMessage(
          player, TeamMessageUtils.inviteListEntry(invite, acceptCommand, declineCommand));
    }
    return true;
  }

  @Override
  public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
    return List.of();
  }
}


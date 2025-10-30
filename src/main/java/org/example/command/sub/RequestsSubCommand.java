package org.example.command.sub;

import java.util.Collections;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.example.model.PendingRequest;
import org.example.service.TeamService;
import org.example.util.TeamMessageUtils;
import org.jetbrains.annotations.NotNull;

/** Подкоманда /team requests. */
public class RequestsSubCommand implements SubCommand {

  private final TeamService teamService;

  public RequestsSubCommand(@NotNull TeamService teamService) {
    this.teamService = teamService;
  }

  @Override
  public boolean execute(@NotNull Player player, @NotNull String[] args) {
    List<PendingRequest> requests = teamService.getJoinRequestsForPlayer(player.getUniqueId());
    if (requests.isEmpty()) {
      TeamMessageUtils.sendTeamMessage(player, TeamMessageUtils.noJoinRequestsMessage());
      return true;
    }
    TeamMessageUtils.sendTeamMessage(player, TeamMessageUtils.joinRequestsHeaderMessage());
    for (PendingRequest request : requests) {
      TeamMessageUtils.sendTeamMessage(
          player,
          TeamMessageUtils.joinRequestListEntry(
              request, "/team cancelrequest " + request.getTeamName()));
    }
    return true;
  }

  @Override
  public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
    return Collections.emptyList();
  }
}

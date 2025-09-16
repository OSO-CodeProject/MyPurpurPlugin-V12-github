package org.example.command.sub;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.example.service.TeamService;
import org.jetbrains.annotations.NotNull;

/** Подкоманда /team leave. */
public class LeaveSubCommand implements SubCommand {

  private final TeamService teamService;

  public LeaveSubCommand(@NotNull TeamService teamService) {
    this.teamService = teamService;
  }

  @Override
  public boolean execute(@NotNull Player player, @NotNull String[] args) {
    String teamName = teamService.getPlayerTeam(player);
    if (teamName == null) {
      player.sendMessage(Component.text("❌ Вы не состоите в команде!", NamedTextColor.RED));
      return true;
    }
    teamService.removePlayerFromTeam(teamName, player);
    return true;
  }
}

package org.example.command.sub;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.example.config.PluginConfig;
import org.example.service.TeamService;
import org.example.util.TeamUtils;
import org.jetbrains.annotations.NotNull;

/** Подкоманда /team members. */
public class MembersSubCommand implements SubCommand {

  private final TeamService teamService;
  private final PluginConfig pluginConfig;

  public MembersSubCommand(@NotNull TeamService teamService, @NotNull PluginConfig pluginConfig) {
    this.teamService = teamService;
    this.pluginConfig = pluginConfig;
  }

  @Override
  public boolean execute(@NotNull Player player, @NotNull String[] args) {
    String playerTeam = teamService.getPlayerTeam(player);
    if (playerTeam == null) {
      player.sendMessage(Component.text("❌ Вы не состоите в команде!", NamedTextColor.RED));
      return true;
    }

    List<String> members = teamService.getTeamMembers(playerTeam);
    String prefix = teamService.getTeamPrefix(playerTeam);
    NamedTextColor color = teamService.getTeamColor(playerTeam);
    Component prefixComponent = TeamUtils.createPrefixComponent(prefix, color);
    int maxMembers = pluginConfig.getMaxMembers();
    Component teamHeader =
        getTeamHeaderComponent(playerTeam, prefixComponent, members.size(), maxMembers);

    player.sendMessage(Component.text(""));
    player.sendMessage(teamHeader);
    player.sendMessage(Component.text(""));
    for (String memberName : members) {
      Player member = teamService.getPlugin().getServer().getPlayer(memberName);
      if (member != null) {
        player.sendMessage(
            Component.text("● ", NamedTextColor.GREEN)
                .append(Component.text(memberName, NamedTextColor.WHITE)));
      } else {
        player.sendMessage(
            Component.text("● ", NamedTextColor.GRAY)
                .append(Component.text(memberName, NamedTextColor.GRAY)));
      }
    }
    player.sendMessage(Component.text(""));
    return true;
  }

  private Component getTeamHeaderComponent(
      String playerTeam, Component prefixComponent, int memberCount, int maxMembers) {
    if (maxMembers > 0) {
      if (memberCount >= maxMembers) {
        return Component.text("📋 Участники команды ", NamedTextColor.AQUA)
            .append(prefixComponent)
            .append(Component.text(playerTeam + " [Полная]:", NamedTextColor.WHITE));
      } else {
        return Component.text("📋 Участники команды ", NamedTextColor.AQUA)
            .append(prefixComponent)
            .append(Component.text(playerTeam + " [", NamedTextColor.WHITE))
            .append(Component.text(memberCount, NamedTextColor.YELLOW))
            .append(Component.text(" / ", NamedTextColor.WHITE))
            .append(Component.text(maxMembers, NamedTextColor.YELLOW))
            .append(Component.text("]:", NamedTextColor.WHITE));
      }
    }
    return Component.text("📋 Участники команды ", NamedTextColor.AQUA)
        .append(prefixComponent)
        .append(Component.text(playerTeam + ":", NamedTextColor.WHITE));
  }
}

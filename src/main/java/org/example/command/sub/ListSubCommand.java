package org.example.command.sub;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.example.config.PluginConfig;
import org.example.service.TeamService;
import org.example.util.TeamUtils;
import org.jetbrains.annotations.NotNull;

/** Подкоманда /team list. */
public class ListSubCommand implements SubCommand {

  private final TeamService teamService;
  private final PluginConfig pluginConfig;

  public ListSubCommand(@NotNull TeamService teamService, @NotNull PluginConfig pluginConfig) {
    this.teamService = teamService;
    this.pluginConfig = pluginConfig;
  }

  @Override
  public boolean execute(@NotNull Player player, @NotNull String[] args) {
    List<String> allTeams = teamService.getTeamNames();
    if (allTeams.isEmpty()) {
      player.sendMessage(Component.text("❌ Нет активных команд!", NamedTextColor.RED));
      return true;
    }

    int maxMembers = pluginConfig.getMaxMembers();
    sendTeamListHeader(player, maxMembers);
    for (String team : allTeams) {
      sendTeamEntry(player, team, maxMembers);
    }
    player.sendMessage(Component.text(""));
    return true;
  }

  private void sendTeamListHeader(Player player, int maxMembers) {
    player.sendMessage(Component.text(""));
    player.sendMessage(Component.text("📋 Список команд:", NamedTextColor.AQUA));
    String limitText = maxMembers > 0 ? String.valueOf(maxMembers) : "без лимита";
    player.sendMessage(
        Component.text("Текущий лимит участников: " + limitText, NamedTextColor.AQUA));
    player.sendMessage(Component.text(""));
  }

  private void sendTeamEntry(Player player, String team, int maxMembers) {
    int memberCount = teamService.getTeamMembers(team).size();
    String prefix = teamService.getTeamPrefix(team);
    NamedTextColor color = teamService.getTeamColor(team);
    Component prefixComponent = TeamUtils.createPrefixComponent(prefix, color);
    Component teamInfo = getTeamInfoComponent(team, memberCount, maxMembers);
    player.sendMessage(Component.text("- ").append(prefixComponent).append(teamInfo));
  }

  private Component getTeamInfoComponent(String team, int memberCount, int maxMembers) {
    if (maxMembers > 0) {
      if (memberCount > maxMembers) {
        return Component.text(team + " [Переполнена]", NamedTextColor.WHITE);
      } else if (memberCount == maxMembers) {
        return Component.text(team + " [Полная]", NamedTextColor.WHITE);
      } else {
        return Component.text(team + " [", NamedTextColor.WHITE)
            .append(Component.text(memberCount, NamedTextColor.YELLOW))
            .append(Component.text(" / ", NamedTextColor.WHITE))
            .append(Component.text(maxMembers, NamedTextColor.YELLOW))
            .append(Component.text("]", NamedTextColor.WHITE));
      }
    }
    return Component.text(team + " (", NamedTextColor.WHITE)
        .append(Component.text(memberCount, NamedTextColor.YELLOW))
        .append(Component.text(" участников)", NamedTextColor.WHITE));
  }
}

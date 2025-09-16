package org.example.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.example.MyPurpurPlugin;
import org.example.command.sub.CreateSubCommand;
import org.example.command.sub.HelpSubCommand;
import org.example.command.sub.JoinSubCommand;
import org.example.command.sub.LeaveSubCommand;
import org.example.command.sub.ListSubCommand;
import org.example.command.sub.MembersSubCommand;
import org.example.command.sub.SubCommand;
import org.example.config.PluginConfig;
import org.example.service.TeamService;
import org.jetbrains.annotations.NotNull;

/** Обработчик команд, связанных с командами игроков. */
public class TeamCommand implements org.bukkit.command.CommandExecutor, TabCompleter {

  private final TeamService teamManager;
  private final PluginConfig pluginConfig;
  private final Map<String, SubCommand> subCommands = new LinkedHashMap<>();

  public TeamCommand(@NotNull TeamService teamManager, @NotNull PluginConfig pluginConfig) {
    this.teamManager = teamManager;
    this.pluginConfig = pluginConfig;
    registerSubCommands();
  }

  private void registerSubCommands() {
    subCommands.put("create", new CreateSubCommand(teamManager));
    subCommands.put("join", new JoinSubCommand(teamManager));
    subCommands.put("leave", new LeaveSubCommand(teamManager));
    subCommands.put("list", new ListSubCommand(teamManager, pluginConfig));
    subCommands.put("members", new MembersSubCommand(teamManager, pluginConfig));
    subCommands.put("help", new HelpSubCommand());
  }

  @Override
  @SuppressWarnings("unused")
  public boolean onCommand(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String label,
      @NotNull String[] args) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(
          Component.text("❌ Эту команду может использовать только игрок!", NamedTextColor.RED));
      return true;
    }

    if (!player.hasPermission("mypurpurplugin.team")) {
      player.sendMessage(
          Component.text("❌ У вас нет прав для использования этой команды!", NamedTextColor.RED));
      return true;
    }

    if (pluginConfig.isTeamCommandRequiresOp() && !player.isOp()) {
      player.sendMessage(
          Component.text(
              "❌ У вас нет прав для использования этой команды! Требуются права OP.",
              NamedTextColor.RED));
      return true;
    }

    if (args.length < 1) {
      sendUsage(player);
      return true;
    }

    String subCommandName = args[0].toLowerCase(Locale.ROOT);
    ((MyPurpurPlugin) teamManager.getPlugin())
        .debugTeamAction("Команда /team выполнена игроком", player.getName(), null);

    SubCommand subCommand = subCommands.get(subCommandName);
    if (subCommand == null) {
      sendUnknownSubCommandMessage(player);
      return true;
    }

    return subCommand.execute(player, args);
  }

  private void sendUsage(Player player) {
    player.sendMessage(
        Component.text(
            "❌ Использование: /team <" + getSubCommandList() + "> [аргументы]", NamedTextColor.RED));
  }

  private void sendUnknownSubCommandMessage(Player player) {
    player.sendMessage(
        Component.text(
            "❌ Неизвестная подкоманда! Используйте: /team <"
                + getSubCommandList()
                + ">",
            NamedTextColor.RED));
  }

  private String getSubCommandList() {
    return String.join(" | ", subCommands.keySet());
  }

  @Override
  @SuppressWarnings("unused")
  public List<String> onTabComplete(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String alias,
      @NotNull String[] args) {
    if (args.length <= 1) {
      String partial = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
      List<String> suggestions = new ArrayList<>();
      for (String name : subCommands.keySet()) {
        if (name.startsWith(partial)) {
          suggestions.add(name);
        }
      }
      return suggestions;
    }

    SubCommand subCommand = subCommands.get(args[0].toLowerCase(Locale.ROOT));
    if (subCommand == null) {
      return new ArrayList<>();
    }

    return subCommand.tabComplete(sender, args);
  }
}

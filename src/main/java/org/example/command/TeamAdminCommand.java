package org.example.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.example.MyPurpurPlugin;
import org.example.service.TeamService;
import org.example.util.TeamMessageUtils;
import org.jetbrains.annotations.NotNull;

public class TeamAdminCommand implements org.bukkit.command.CommandExecutor, TabCompleter {

  private final TeamService teamManager;

  public TeamAdminCommand(@NotNull TeamService teamManager) {
    this.teamManager = teamManager;
  }

  @Override
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

    if (!player.hasPermission("mypurpurplugin.teamadmin")) {
      player.sendMessage(
          Component.text("❌ У вас нет прав для использования этой команды!", NamedTextColor.RED));
      return true;
    }

    if (args.length < 1) {
      sendUsage(player);
      return true;
    }

    String subCommand = args[0].toLowerCase();
    ((MyPurpurPlugin) teamManager.getPlugin())
        .debugTeamAction("Команда /teamadmin выполнена игроком", player.getName(), null);

    return handleSubCommand(player, subCommand, args);
  }

  private boolean handleSubCommand(Player player, String subCommand, String[] args) {
    return switch (subCommand) {
      case "transfer" -> handleTransferCommand(player, args);
      case "kick" -> handleKickCommand(player, args);
      case "disband" -> handleDisbandCommand(player);
      case "rename" -> handleRenameCommand(player, args);
      case "setprefix" -> handleSetPrefixCommand(player, args);
      case "setcolor" -> handleSetColorCommand(player, args);
      case "help" -> {
        sendHelp(player);
        yield true;
      }
      default -> {
        player.sendMessage(
            Component.text(
                "❌ Неизвестная подкоманда! Используйте: /teamadmin <transfer | kick | disband | rename | setprefix | setcolor | help>",
                NamedTextColor.RED));
        yield true;
      }
    };
  }

  private boolean handleTransferCommand(Player player, String[] args) {
    if (args.length < 2) {
      TeamMessageUtils.sendTeamMessage(
          player, Component.text("❌ Использование: /teamadmin transfer <ник>", NamedTextColor.RED));
      return true;
    }
    String teamName = teamManager.getPlayerTeam(player);
    if (teamName == null) {
      TeamMessageUtils.sendTeamMessage(
          player,
          Component.text(
              "❌ Вы не состоите в команде и не можете передать лидерство!", NamedTextColor.RED));
      return true;
    }
    String newLeaderName = args[1];
    Player newLeader = teamManager.getPlugin().getServer().getPlayerExact(newLeaderName);
    if (newLeader == null || !newLeader.isOnline()) {
      TeamMessageUtils.sendTeamMessage(
          player,
          Component.text("❌ Игрок ", NamedTextColor.RED)
              .append(Component.text(newLeaderName, NamedTextColor.WHITE))
              .append(Component.text(" не в сети!", NamedTextColor.RED)));
      return true;
    }
    teamManager.transferLeadership(teamName, player, newLeader);
    return true;
  }

  private boolean handleKickCommand(Player player, String[] args) {
    if (args.length < 2) {
      TeamMessageUtils.sendTeamMessage(
          player, Component.text("❌ Использование: /teamadmin kick <ник>", NamedTextColor.RED));
      return true;
    }
    String teamName = teamManager.getPlayerTeam(player);
    if (teamName == null) {
      TeamMessageUtils.sendTeamMessage(
          player,
          Component.text(
              "❌ Вы не состоите в команде и не можете исключать игроков!", NamedTextColor.RED));
      return true;
    }
    String targetName = args[1];
    teamManager.kickPlayerFromTeam(teamName, player, targetName);
    return true;
  }

  private boolean handleDisbandCommand(Player player) {
    String teamName = teamManager.getPlayerTeam(player);
    if (teamName == null) {
      TeamMessageUtils.sendTeamMessage(
          player,
          Component.text(
              "❌ Вы не состоите в команде и не можете распустить её!", NamedTextColor.RED));
      return true;
    }
    teamManager.disbandTeam(teamName, player);
    return true;
  }

  private boolean handleRenameCommand(Player player, String[] args) {
    if (args.length < 2) {
      TeamMessageUtils.sendTeamMessage(
          player,
          Component.text(
              "❌ Использование: /teamadmin rename <новое_название>", NamedTextColor.RED));
      return true;
    }
    String oldTeamName = teamManager.getPlayerTeam(player);
    if (oldTeamName == null) {
      TeamMessageUtils.sendTeamMessage(
          player,
          Component.text(
              "❌ Вы не состоите в команде и не можете её переименовать!", NamedTextColor.RED));
      return true;
    }
    String newTeamName = args[1];
    teamManager.renameTeam(oldTeamName, newTeamName, player);
    return true;
  }

  private boolean handleSetPrefixCommand(Player player, String[] args) {
    if (args.length < 2) {
      TeamMessageUtils.sendTeamMessage(
          player,
          Component.text(
              "❌ Использование: /teamadmin setprefix <новый_префикс>", NamedTextColor.RED));
      return true;
    }
    String teamName = teamManager.getPlayerTeam(player);
    if (teamName == null) {
      TeamMessageUtils.sendTeamMessage(
          player,
          Component.text(
              "❌ Вы не состоите в команде и не можете изменить её префикс!", NamedTextColor.RED));
      return true;
    }
    String newPrefix = args[1];
    teamManager.setTeamPrefix(teamName, newPrefix, player);
    return true;
  }

  private boolean handleSetColorCommand(Player player, String[] args) {
    if (args.length < 2) {
      TeamMessageUtils.sendTeamMessage(
          player,
          Component.text("❌ Использование: /teamadmin setcolor <новый_цвет>", NamedTextColor.RED));
      return true;
    }
    String teamName = teamManager.getPlayerTeam(player);
    if (teamName == null) {
      TeamMessageUtils.sendTeamMessage(
          player,
          Component.text(
              "❌ Вы не состоите в команде и не можете изменить её цвет!", NamedTextColor.RED));
      return true;
    }
    String newColor = args[1];
    teamManager.setTeamColor(teamName, newColor, player);
    return true;
  }

  private void sendUsage(Player player) {
    player.sendMessage(
        Component.text(
            "❌ Использование: /teamadmin <transfer | kick | disband | rename | setprefix | setcolor | help> [аргументы]",
            NamedTextColor.RED));
  }

  private void sendHelp(Player player) {
    player.sendMessage(Component.text("")); // Пустая строка перед списком
    player.sendMessage(Component.text("ℹ Использование /teamadmin:", NamedTextColor.AQUA));
    player.sendMessage(Component.text(""));
    player.sendMessage(
        Component.text(
            "/teamadmin transfer <ник> — передать лидерство другому игроку в команде",
            NamedTextColor.AQUA));
    player.sendMessage(
        Component.text(
            "/teamadmin kick <ник> — выгнать участника из команды (требуется быть лидером)",
            NamedTextColor.AQUA));
    player.sendMessage(
        Component.text(
            "/teamadmin disband — распустить команду (требуется быть лидером)",
            NamedTextColor.AQUA));
    player.sendMessage(
        Component.text(
            "/teamadmin rename <новое_название> — переименовать команду (требуется быть лидером)",
            NamedTextColor.AQUA));
    player.sendMessage(
        Component.text(
            "/teamadmin setprefix <новый_префикс> — изменить префикс команды (требуется быть лидером)",
            NamedTextColor.AQUA));
    player.sendMessage(
        Component.text(
            "/teamadmin setcolor <новый_цвет> — изменить цвет команды (требуется быть лидером, цвет: RED, BLUE, GREEN и т.д.)",
            NamedTextColor.AQUA));
    player.sendMessage(
        Component.text("/teamadmin help — показать эту справку", NamedTextColor.AQUA));
    player.sendMessage(Component.text("")); // Пустая строка после списка
  }

  @Override
  public List<String> onTabComplete(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String alias,
      @NotNull String[] args) {
    List<String> suggestions = new ArrayList<>();
    if (args.length == 1) {
      suggestions.addAll(
          Arrays.asList("transfer", "kick", "disband", "rename", "setprefix", "setcolor", "help"));
    } else if (args.length == 2) {
      if (sender instanceof Player player) {
        if (args[0].equalsIgnoreCase("transfer") || args[0].equalsIgnoreCase("kick")) {
          String teamName = teamManager.getPlayerTeam(player);
          if (teamName != null) {
            String leaderName = teamManager.getTeamLeader(teamName);
            if (player.getName().equals(leaderName)) {
              List<String> members = teamManager.getTeamMembers(teamName);
              for (String memberName : members) {
                if (!memberName.equals(player.getName())
                    && memberName.toLowerCase().startsWith(args[1].toLowerCase())) {
                  suggestions.add(memberName);
                }
              }
            }
          }
        } else if (args[0].equalsIgnoreCase("rename")) {
          suggestions.add("<новое_название>");
        } else if (args[0].equalsIgnoreCase("setprefix")) {
          suggestions.add("<новый_префикс>");
        } else if (args[0].equalsIgnoreCase("setcolor")) {
          for (NamedTextColor color : NamedTextColor.NAMES.values()) {
            if (color.toString().toLowerCase().startsWith(args[1].toLowerCase())) {
              suggestions.add(color.toString().toUpperCase());
            }
          }
        }
      }
    }
    return suggestions;
  }
}

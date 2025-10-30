package org.example.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.example.MyPurpurPlugin;
import org.example.config.PluginConfig;
import org.example.service.RenameResult;
import org.example.service.TeamService;
import org.example.model.PendingRequest;
import org.example.util.TeamMessageUtils;
import org.example.util.TeamUtils;
import org.jetbrains.annotations.NotNull;

public class TeamAdminCommand implements org.bukkit.command.CommandExecutor, TabCompleter {

  private final TeamService teamManager;
  private final PluginConfig pluginConfig;

  public TeamAdminCommand(@NotNull TeamService teamManager, @NotNull PluginConfig pluginConfig) {
    this.teamManager = teamManager;
    this.pluginConfig = pluginConfig;
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

    String subCommand = args[0].toLowerCase(Locale.ROOT);
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
      case "getplinfo" -> handleGetPlayerInfoCommand(player, args);
      case "requests" -> handleRequestsCommand(player);
      case "acceptrequest" -> handleAcceptRequestCommand(player, args);
      case "denyrequest" -> handleDenyRequestCommand(player, args);
      case "clearrequests" -> handleClearRequestsCommand(player);
      case "help" -> {
        sendHelp(player);
        yield true;
      }
      default -> {
        player.sendMessage(
            Component.text(
                "❌ Неизвестная подкоманда! Используйте: /teamadmin <transfer | kick | disband | rename | setprefix | setcolor | getplinfo | requests | acceptrequest | denyrequest | clearrequests | help>",
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
    if (!ensurePlayerIsLeader(player, teamName)) {
      return true;
    }
    String newLeaderName = args[1].trim();
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
    if (!ensurePlayerIsLeader(player, teamName)) {
      return true;
    }
    String targetName = args[1].trim();
    if (targetName.equalsIgnoreCase(player.getName())) {
      TeamMessageUtils.sendTeamMessage(
          player, Component.text("❌ Вы не можете исключить себя из команды!", NamedTextColor.RED));
      return true;
    }
    teamManager.kickPlayerFromTeam(teamName, player, targetName);
    return true;
  }

  private boolean handleDisbandCommand(Player player) {
    String teamName = teamManager.getPlayerTeam(player);
    if (!ensurePlayerIsLeader(player, teamName)) {
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
    if (!ensurePlayerIsLeader(player, oldTeamName)) {
      return true;
    }
    String newTeamName = args[1].trim();
    if (TeamUtils.isTeamNameLengthInvalid(newTeamName, pluginConfig, player)) {
      return true;
    }
    RenameResult result = teamManager.renameTeam(oldTeamName, newTeamName, player);
    return handleRenameResult(player, oldTeamName, newTeamName, result);
  }

  private boolean handleRenameResult(
      Player player, String oldTeamName, String newTeamName, RenameResult result) {
    if (result == RenameResult.SUCCESS) {
      TeamMessageUtils.sendTeamMessage(
          player, TeamMessageUtils.teamRenamedLeaderMessage(oldTeamName, newTeamName));
      return true;
    }
    if (result == RenameResult.NAME_TAKEN) {
      TeamMessageUtils.sendTeamMessage(
          player, TeamMessageUtils.teamAlreadyExistsMessage(newTeamName));
      return true;
    }
    if (result == RenameResult.TEAM_NOT_FOUND) {
      TeamMessageUtils.sendTeamMessage(
          player, TeamMessageUtils.teamDoesNotExistMessage(oldTeamName));
      return true;
    }
    if (result == RenameResult.NOT_LEADER) {
      TeamMessageUtils.sendTeamMessage(player, TeamMessageUtils.notTeamLeaderMessage());
    }
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
    if (!ensurePlayerIsLeader(player, teamName)) {
      return true;
    }
    String newPrefix = args[1].trim();
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
    if (!ensurePlayerIsLeader(player, teamName)) {
      return true;
    }
    String newColor = args[1].trim();
    teamManager.setTeamColor(teamName, newColor, player);
    return true;
  }

  private boolean handleRequestsCommand(Player player) {
    String teamName = teamManager.getPlayerTeam(player);
    if (!ensurePlayerIsLeader(player, teamName)) {
      return true;
    }
    List<PendingRequest> requests = teamManager.listJoinRequests(teamName);
    if (requests.isEmpty()) {
      TeamMessageUtils.sendTeamMessage(player, TeamMessageUtils.noJoinRequestsMessage());
      return true;
    }
    TeamMessageUtils.sendTeamMessage(
        player, TeamMessageUtils.joinRequestsTeamHeaderMessage(teamName));
    for (PendingRequest request : requests) {
      TeamMessageUtils.sendTeamMessage(
          player,
          TeamMessageUtils.joinRequestTeamListEntry(
              request,
              "/teamadmin acceptrequest " + request.getPlayerName(),
              "/teamadmin denyrequest " + request.getPlayerName()));
    }
    return true;
  }

  private boolean handleAcceptRequestCommand(Player player, String[] args) {
    if (args.length < 2) {
      TeamMessageUtils.sendTeamMessage(
          player,
          Component.text(
              "❌ Использование: /teamadmin acceptrequest <ник>", NamedTextColor.RED));
      return true;
    }
    String teamName = teamManager.getPlayerTeam(player);
    if (!ensurePlayerIsLeader(player, teamName)) {
      return true;
    }
    String targetName = args[1].trim();
    teamManager.approveJoinRequest(teamName, player, targetName);
    return true;
  }

  private boolean handleDenyRequestCommand(Player player, String[] args) {
    if (args.length < 2) {
      TeamMessageUtils.sendTeamMessage(
          player,
          Component.text(
              "❌ Использование: /teamadmin denyrequest <ник>", NamedTextColor.RED));
      return true;
    }
    String teamName = teamManager.getPlayerTeam(player);
    if (!ensurePlayerIsLeader(player, teamName)) {
      return true;
    }
    String targetName = args[1].trim();
    teamManager.denyJoinRequest(teamName, player, targetName);
    return true;
  }

  private boolean handleClearRequestsCommand(Player player) {
    String teamName = teamManager.getPlayerTeam(player);
    if (!ensurePlayerIsLeader(player, teamName)) {
      return true;
    }
    List<PendingRequest> requests = teamManager.listJoinRequests(teamName);
    if (requests.isEmpty()) {
      TeamMessageUtils.sendTeamMessage(player, TeamMessageUtils.noJoinRequestsMessage());
      return true;
    }
    for (PendingRequest request : requests) {
      teamManager.denyJoinRequest(teamName, player, request.getPlayerName());
    }
    TeamMessageUtils.sendTeamMessage(
        player, TeamMessageUtils.joinRequestsClearedLeaderMessage(requests.size()));
    return true;
  }

  private boolean handleGetPlayerInfoCommand(Player player, String[] args) {
    if (args.length < 2) {
      TeamMessageUtils.sendTeamMessage(
          player,
          Component.text("❌ Использование: /teamadmin getplinfo <ник>", NamedTextColor.RED));
      return true;
    }

    String inputName = args[1].trim();
    if (inputName.isEmpty()) {
      TeamMessageUtils.sendTeamMessage(
          player, Component.text("❌ Имя игрока не может быть пустым!", NamedTextColor.RED));
      return true;
    }

    var server = teamManager.getPlugin().getServer();
    Player onlineTarget = server.getPlayerExact(inputName);
    boolean online = onlineTarget != null && onlineTarget.isOnline();
    OfflinePlayer offlineTarget =
        onlineTarget != null ? onlineTarget : server.getOfflinePlayer(inputName);

    if (!online
        && (offlineTarget.getName() == null
            || (!offlineTarget.hasPlayedBefore() && !offlineTarget.isOnline()))) {
      TeamMessageUtils.sendTeamMessage(player, TeamMessageUtils.playerNotFoundMessage(inputName));
      TeamMessageUtils.sendTeamMessage(
          player,
          Component.text("❌ Игрок ", NamedTextColor.RED)
              .append(Component.text(inputName, NamedTextColor.WHITE))
              .append(Component.text(" не найден!", NamedTextColor.RED)));
      return true;
    }

    UUID targetId = onlineTarget != null ? onlineTarget.getUniqueId() : offlineTarget.getUniqueId();
    String resolvedName =
        onlineTarget != null
            ? onlineTarget.getName()
            : offlineTarget.getName() != null ? offlineTarget.getName() : inputName;

    String teamName = null;
    UUID leaderId = null;
    for (String existingTeam : teamManager.getTeamNames()) {
      List<UUID> members = teamManager.getTeamMembers(existingTeam);
      if (members.contains(targetId)) {
        teamName = existingTeam;
        leaderId = teamManager.getTeamLeaderId(existingTeam);
        break;
      }
    }

    boolean isLeader = teamName != null && leaderId != null && leaderId.equals(targetId);

    Component message =
        Component.text()
            .append(Component.text("ℹ Информация об игроке ", NamedTextColor.AQUA))
            .append(Component.text(resolvedName, NamedTextColor.GOLD))
            .append(Component.text(":", NamedTextColor.AQUA))
            .append(Component.newline())
            .append(Component.text("• UUID: ", NamedTextColor.GRAY))
            .append(Component.text(targetId.toString(), NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text("• Статус: ", NamedTextColor.GRAY))
            .append(
                Component.text(
                    online ? "В сети" : "Оффлайн",
                    online ? NamedTextColor.GREEN : NamedTextColor.RED))
            .append(Component.newline())
            .append(Component.text("• Команда: ", NamedTextColor.GRAY))
            .append(
                Component.text(
                    teamName != null ? teamName : "Не состоит",
                    teamName != null ? NamedTextColor.GOLD : NamedTextColor.GRAY))
            .append(teamName != null ? Component.newline() : Component.empty())
            .append(
                teamName != null
                    ? Component.text("• Роль: ", NamedTextColor.GRAY)
                        .append(
                            Component.text(
                                isLeader ? "Лидер" : "Участник",
                                isLeader ? NamedTextColor.YELLOW : NamedTextColor.WHITE))
                    : Component.empty())
            .build();

    player.sendMessage(message);
    return true;
  }

  private void sendUsage(Player player) {
    player.sendMessage(
        Component.text(
            "❌ Использование: /teamadmin <transfer | kick | disband | rename | setprefix | setcolor | getplinfo | requests | acceptrequest | denyrequest | clearrequests | help> [аргументы]",
            NamedTextColor.RED));
  }

  private void sendHelp(Player player) {
    Component helpMessage =
        Component.text()
            .append(Component.newline())
            .append(
                Component.text("ℹ Использование /teamadmin:", NamedTextColor.AQUA)
                    .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD))
            .append(Component.newline())
            .append(Component.newline())
            .append(
                adminBullet(
                    "/teamadmin transfer <ник>",
                    "передать лидерство другому игроку в команде"))
            .append(Component.newline())
            .append(
                adminBullet(
                    "/teamadmin kick <ник>",
                    "исключить участника команды (требуется быть лидером)"))
            .append(Component.newline())
            .append(adminBullet("/teamadmin disband", "распустить команду (требуется быть лидером)"))
            .append(Component.newline())
            .append(
                adminBullet(
                    "/teamadmin rename <новое_название>",
                    "переименовать команду (требуется быть лидером)"))
            .append(Component.newline())
            .append(
                adminBullet(
                    "/teamadmin setprefix <новый_префикс>",
                    "изменить префикс команды (требуется быть лидером)"))
            .append(Component.newline())
            .append(
                adminBullet(
                    "/teamadmin setcolor <новый_цвет>",
                    "изменить цвет команды (цвет: RED, BLUE, GREEN и т.д.)"))
            .append(Component.newline())
            .append(
                adminBullet(
                    "/teamadmin requests",
                    "посмотреть заявки на вступление в вашу команду"))
            .append(Component.newline())
            .append(
                adminBullet(
                    "/teamadmin acceptrequest <ник>",
                    "одобрить заявку игрока на вступление"))
            .append(Component.newline())
            .append(
                adminBullet(
                    "/teamadmin denyrequest <ник>",
                    "отклонить заявку игрока"))
            .append(Component.newline())
            .append(
                adminBullet(
                    "/teamadmin clearrequests",
                    "очистить все заявки на вступление"))
            .append(Component.newline())
            .append(
                adminBullet(
                    "/teamadmin getplinfo <ник>",
                    "показать командную информацию об игроке"))
            .append(Component.newline())
            .append(adminBullet("/teamadmin help", "показать эту справку"))
            .append(Component.newline())
            .build();

    player.sendMessage(helpMessage);
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
          Arrays.asList(
              "transfer",
              "kick",
              "disband",
              "rename",
              "setprefix",
              "setcolor",
              "getplinfo",
              "help"));
    } else if (args.length == 2) {
      if (sender instanceof Player player) {
        if (args[0].equalsIgnoreCase("transfer") || args[0].equalsIgnoreCase("kick")) {
          String teamName = teamManager.getPlayerTeam(player);
          if (teamName != null && teamManager.getTeamIdByName(teamName) != null) {
            UUID leaderId = teamManager.getTeamLeaderId(teamName);
            if (player.getUniqueId().equals(leaderId)) {
              List<UUID> members = teamManager.getTeamMembers(teamName);
              String lowerInput = args[1].toLowerCase(Locale.ROOT);
              for (UUID memberId : members) {
                if (memberId.equals(player.getUniqueId())) {
                  continue;
                }
                String memberName = resolveName(memberId);
                if (memberName.toLowerCase(Locale.ROOT).startsWith(lowerInput)) {
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
          String lowerInput = args[1].toLowerCase(Locale.ROOT);
          for (NamedTextColor color : NamedTextColor.NAMES.values()) {
            String key = NamedTextColor.NAMES.key(color);
            if (key != null && key.toLowerCase(Locale.ROOT).startsWith(lowerInput)) {
              suggestions.add(key.toUpperCase(Locale.ROOT));
            }
          }
        } else if (args[0].equalsIgnoreCase("getplinfo")) {
          String lowerInput = args[1].toLowerCase(Locale.ROOT);
          Set<String> seen = new HashSet<>();
          for (Player online : teamManager.getPlugin().getServer().getOnlinePlayers()) {
            String name = online.getName();
            if (name != null
                && name.toLowerCase(Locale.ROOT).startsWith(lowerInput)
                && seen.add(name.toLowerCase(Locale.ROOT))) {
              suggestions.add(name);
            }
          }
          for (String teamName : teamManager.getTeamNames()) {
            for (UUID memberId : teamManager.getTeamMembers(teamName)) {
              String memberName = resolveName(memberId);
              if (!memberName.isEmpty()
                  && memberName.toLowerCase(Locale.ROOT).startsWith(lowerInput)
                  && seen.add(memberName.toLowerCase(Locale.ROOT))) {
                suggestions.add(memberName);
              }
            }
          }
        }
      }
    }
    if (!suggestions.isEmpty()) {
      Collections.sort(suggestions);
    }
    return suggestions;
  }

  private String resolveName(UUID playerId) {
    if (playerId == null) {
      return "";
    }
    String name = teamManager.getPlugin().getServer().getOfflinePlayer(playerId).getName();
    return name != null ? name : playerId.toString();
  }

  private boolean ensurePlayerIsLeader(Player player, String teamName) {
    if (!ensurePlayerInTeam(player, teamName)) {
      return false;
    }
    UUID leaderId = teamManager.getTeamLeaderId(teamName);
    if (leaderId == null || !leaderId.equals(player.getUniqueId())) {
      TeamMessageUtils.sendTeamMessage(player, TeamMessageUtils.notTeamLeaderMessage());
      return false;
    }
    return true;
  }

  private boolean ensurePlayerInTeam(Player player, String teamName) {
    if (teamName == null) {
      TeamMessageUtils.sendTeamMessage(player, TeamMessageUtils.playerNotInAnyTeamMessage());
      return false;
    }
    return true;
  }

  private Component adminBullet(String commandText, String description) {
    return Component.text("• ", NamedTextColor.DARK_GRAY)
        .append(Component.text(commandText, NamedTextColor.GOLD))
        .append(Component.text(" — " + description, NamedTextColor.WHITE));
  }
}

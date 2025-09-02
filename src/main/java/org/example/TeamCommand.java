package org.example;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Обработчик команд, связанных с командами игроков.
 */
public class TeamCommand implements org.bukkit.command.CommandExecutor, TabCompleter {

    private final TeamService teamManager;
    private final PluginConfig pluginConfig;

    public TeamCommand(@NotNull TeamService teamManager, @NotNull PluginConfig pluginConfig) {
        this.teamManager = teamManager;
        this.pluginConfig = pluginConfig;
    }

    @Override
    @SuppressWarnings("unused")
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("❌ Эту команду может использовать только игрок!", NamedTextColor.RED));
            return true;
        }

        // Проверка разрешения
        if (!player.hasPermission("mypurpurplugin.team")) {
            player.sendMessage(Component.text("❌ У вас нет прав для использования этой команды!", NamedTextColor.RED));
            return true;
        }

        // Проверка прав из конфига
        if (pluginConfig.isTeamCommandRequiresOp() && !player.isOp()) {
            player.sendMessage(Component.text("❌ У вас нет прав для использования этой команды! Требуются права OP.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 1) {
            sendUsage(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        ((MyPurpurPlugin) teamManager.getPlugin()).debugTeamAction("Команда /team выполнена игроком", player.getName(), null);

        return handleSubCommand(player, subCommand, args);
    }

    private boolean handleSubCommand(Player player, String subCommand, String[] args) {
        return switch (subCommand) {
            case "create" -> handleCreateCommand(player, args);
            case "join" -> handleJoinCommand(player, args);
            case "leave" -> handleLeaveCommand(player);
            case "list" -> handleListCommand(player);
            case "members" -> handleMembersCommand(player);
            case "help" -> {
                sendHelp(player);
                yield true;
            }
            default -> {
                player.sendMessage(Component.text("❌ Неизвестная подкоманда! Используйте: /team <create | join | leave | list | members | help>", NamedTextColor.RED));
                yield true;
            }
        };
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean handleCreateCommand(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(Component.text("❌ Использование: /team create <название> <префикс> <цвет> (цвет: RED, BLUE, GREEN и т.д.)", NamedTextColor.RED));
            return true;
        }
        teamManager.createTeam(args[1], args[2], args[3], player);
        return true; // Всегда возвращаем true, чтобы избежать лишних подсказок от Bukkit
    }

    private boolean handleJoinCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("❌ Использование: /team join <название>", NamedTextColor.RED));
            return true;
        }
        teamManager.addPlayerToTeam(args[1], player);
        return true; // Всегда возвращаем true, чтобы избежать лишних подсказок от Bukkit
    }

    private boolean handleLeaveCommand(Player player) {
        String teamName = teamManager.getPlayerTeam(player);
        if (teamName == null) {
            player.sendMessage(Component.text("❌ Вы не состоите в команде!", NamedTextColor.RED));
            return true;
        }
        teamManager.removePlayerFromTeam(teamName, player);
        return true; // Всегда возвращаем true, чтобы избежать лишних подсказок от Bukkit
    }

    private boolean handleListCommand(Player player) {
        List<String> allTeams = teamManager.getTeamNames();
        if (allTeams.isEmpty()) {
            player.sendMessage(Component.text("❌ Нет активных команд!", NamedTextColor.RED));
            return true;
        }
        player.sendMessage(Component.text("")); // Пустая строка перед списком
        player.sendMessage(Component.text("📋 Список команд:", NamedTextColor.AQUA));
        int maxMembers = pluginConfig.getMaxMembers();
        String limitText = maxMembers > 0 ? String.valueOf(maxMembers) : "без лимита";
        player.sendMessage(Component.text("Текущий лимит участников: " + limitText, NamedTextColor.AQUA));
        player.sendMessage(Component.text("")); // Пустая строка перед списком команд
        for (String team : allTeams) {
            int memberCount = teamManager.getTeamMembers(team).size();
            String prefix = teamManager.getTeamPrefix(team);
            NamedTextColor color = teamManager.getTeamColor(team);
            Component prefixComponent = TeamUtils.createPrefixComponent(prefix, color);
            Component teamInfo = getTeamInfoComponent(team, memberCount, maxMembers);
            player.sendMessage(Component.text("- ")
                    .append(prefixComponent)
                    .append(teamInfo));
        }
        player.sendMessage(Component.text("")); // Пустая строка после списка
        return true;
    }

    private boolean handleMembersCommand(Player player) {
        String playerTeam = teamManager.getPlayerTeam(player);
        if (playerTeam == null) {
            player.sendMessage(Component.text("❌ Вы не состоите в команде!", NamedTextColor.RED));
            return true;
        }
        List<String> members = teamManager.getTeamMembers(playerTeam);
        String prefix = teamManager.getTeamPrefix(playerTeam);
        NamedTextColor color = teamManager.getTeamColor(playerTeam);
        Component prefixComponent = TeamUtils.createPrefixComponent(prefix, color);
        int maxMembers = pluginConfig.getMaxMembers();
        Component teamHeader = getTeamHeaderComponent(playerTeam, prefixComponent, members.size(), maxMembers);
        player.sendMessage(Component.text("")); // Пустая строка перед заголовком
        player.sendMessage(teamHeader);
        player.sendMessage(Component.text("")); // Пустая строка перед списком участников
        for (String memberName : members) {
            Player member = teamManager.getPlugin().getServer().getPlayer(memberName);
            if (member != null) {
                // Игрок в сети: зелёный круг и белый цвет ника
                player.sendMessage(Component.text("● ", NamedTextColor.GREEN)
                        .append(Component.text(memberName, NamedTextColor.WHITE)));
            } else {
                // Игрок не в сети: серый круг и серый цвет ника
                player.sendMessage(Component.text("● ", NamedTextColor.GRAY)
                        .append(Component.text(memberName, NamedTextColor.GRAY)));
            }
        }
        player.sendMessage(Component.text("")); // Пустая строка после списка
        return true;
    }

    /**
     * Формирует компонент с информацией о команде (количество участников, статус заполненности).
     *
     * @param team       Название команды
     * @param memberCount Количество участников в команде
     * @param maxMembers Максимальное количество участников (0 — безлимит)
     * @return Компонент с информацией о команде
     */
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
        } else {
            return Component.text(team + " (", NamedTextColor.WHITE)
                    .append(Component.text(memberCount, NamedTextColor.YELLOW))
                    .append(Component.text(" участников)", NamedTextColor.WHITE));
        }
    }

    /**
     * Формирует компонент заголовка команды для списка участников.
     *
     * @param playerTeam Название команды
     * @param prefixComponent Префикс команды
     * @param memberCount Количество участников в команде
     * @param maxMembers Максимальное количество участников (0 — безлимит)
     * @return Компонент заголовка команды
     */
    private Component getTeamHeaderComponent(String playerTeam, Component prefixComponent, int memberCount, int maxMembers) {
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
        } else {
            return Component.text("📋 Участники команды ", NamedTextColor.AQUA)
                    .append(prefixComponent)
                    .append(Component.text(playerTeam + ":", NamedTextColor.WHITE));
        }
    }

    private void sendUsage(Player player) {
        player.sendMessage(Component.text("❌ Использование: /team <create | join | leave | list | members | help> [аргументы]", NamedTextColor.RED));
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("")); // Пустая строка перед списком
        player.sendMessage(Component.text("ℹ Использование /team:", NamedTextColor.AQUA));
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("/team create <название> <префикс> <цвет> — создать команду (цвет: RED, BLUE, GREEN и т.д.)", NamedTextColor.AQUA));
        player.sendMessage(Component.text("/team join <название> — вступить в команду", NamedTextColor.AQUA));
        player.sendMessage(Component.text("/team leave — покинуть команду", NamedTextColor.AQUA));
        player.sendMessage(Component.text("/team list — показать список всех команд", NamedTextColor.AQUA));
        player.sendMessage(Component.text("/team members — показать участников вашей команды", NamedTextColor.AQUA));
        player.sendMessage(Component.text("/team help — показать эту справку", NamedTextColor.AQUA));
        player.sendMessage(Component.text("")); // Пустая строка после списка
    }

    @Override
    @SuppressWarnings("unused")
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1) {
            suggestions.addAll(Arrays.asList("create", "join", "leave", "list", "members", "help"));
        } else if (args.length == 2) {
            if (sender instanceof Player player) {
                if (args[0].equalsIgnoreCase("join")) {
                    String playerTeam = teamManager.getPlayerTeam(player);
                    if (playerTeam == null) { // Игрок не в команде
                        for (String teamName : teamManager.getTeamNames()) {
                            String leader = teamManager.getTeamLeader(teamName);
                            if (leader == null || !leader.equals(player.getName())) {
                                if (teamName.toLowerCase().startsWith(args[1].toLowerCase())) {
                                    suggestions.add(teamName);
                                }
                            }
                        }
                    }
                } else if (args[0].equalsIgnoreCase("create")) {
                    suggestions.add("<название>");
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
            suggestions.add("<префикс>");
        } else if (args.length == 4 && args[0].equalsIgnoreCase("create")) {
            for (NamedTextColor color : NamedTextColor.NAMES.values()) {
                if (color.toString().toLowerCase().startsWith(args[3].toLowerCase())) {
                    suggestions.add(color.toString().toUpperCase());
                }
            }
        }
        return suggestions;
    }
}
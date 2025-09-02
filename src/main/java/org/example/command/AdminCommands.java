package org.example.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.example.MyPurpurPlugin;
import org.example.service.TeamService;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AdminCommands implements CommandExecutor, TabCompleter {

    private final TeamService teamService;

    public AdminCommands(@NotNull TeamService teamService) {
        this.teamService = teamService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Эта команда доступна только игрокам!", NamedTextColor.RED));
            return true;
        }

        if (!player.isOp()) {
            player.sendMessage(Component.text("❌ У вас нет прав для выполнения этой команды!", NamedTextColor.RED));
            return true;
        }

        String commandName = command.getName().toLowerCase();

        if (commandName.equals("getteamsuuidlist")) {
            return handleGetTeamsUUIDListCommand(player);
        } else if (commandName.equals("getteamuuid")) {
            return handleGetTeamUUIDCommand(player, args);
        }

        return false;
    }

    private boolean handleGetTeamsUUIDListCommand(Player player) {
        List<String> teamNames = teamService.getTeamNames();
        if (teamNames.isEmpty()) {
            player.sendMessage(Component.text("❌ Нет активных команд!", NamedTextColor.RED));
            return true;
        }

        player.sendMessage(Component.text("")); // Пустая строка перед списком
        player.sendMessage(Component.text("📋 Список команд и их UUID:", NamedTextColor.AQUA));
        player.sendMessage(Component.text("")); // Пустая строка перед списком команд
        for (String teamName : teamNames) {
            UUID teamId = teamService.getTeamIdByName(teamName);
            TextComponent uuidComponent = Component.text(teamId.toString(), NamedTextColor.YELLOW)
                    .clickEvent(ClickEvent.copyToClipboard(teamId.toString()))
                    .hoverEvent(HoverEvent.showText(Component.text("Кликните, чтобы скопировать UUID")));
            TextComponent nameComponent = Component.text(teamName, NamedTextColor.YELLOW)
                    .clickEvent(ClickEvent.copyToClipboard(teamName))
                    .hoverEvent(HoverEvent.showText(Component.text("Кликните, чтобы скопировать название")));
            player.sendMessage(Component.text("- UUID: ", NamedTextColor.WHITE)
                    .append(uuidComponent)
                    .append(Component.text(" | Название: ", NamedTextColor.WHITE))
                    .append(nameComponent));
        }
        player.sendMessage(Component.text("")); // Пустая строка после списка
        ((MyPurpurPlugin) teamService.getPlugin()).debugTeamAction("Админ запросил список UUID команд", player.getName(), "");
        return true;
    }

    private boolean handleGetTeamUUIDCommand(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(Component.text("❌ Использование: /getteamuuid <название>", NamedTextColor.RED));
            return true;
        }

        String teamName = args[0];
        UUID teamId = teamService.getTeamIdByName(teamName);
        if (teamId == null) {
            player.sendMessage(Component.text("❌ Команда ", NamedTextColor.RED)
                    .append(Component.text(teamName, NamedTextColor.WHITE))
                    .append(Component.text(" не существует!", NamedTextColor.RED)));
            return true;
        }

        TextComponent uuidComponent = Component.text(teamId.toString(), NamedTextColor.YELLOW)
                .clickEvent(ClickEvent.copyToClipboard(teamId.toString()))
                .hoverEvent(HoverEvent.showText(Component.text("Кликните, чтобы скопировать UUID")));
        TextComponent nameComponent = Component.text(teamName, NamedTextColor.YELLOW)
                .clickEvent(ClickEvent.copyToClipboard(teamName))
                .hoverEvent(HoverEvent.showText(Component.text("Кликните, чтобы скопировать название")));

        player.sendMessage(Component.text("")); // Пустая строка перед выводом
        player.sendMessage(Component.text("ℹ Информация о команде:", NamedTextColor.AQUA));
        player.sendMessage(Component.text("Название: ", NamedTextColor.WHITE)
                .append(nameComponent)
                .append(Component.text(" | UUID: ", NamedTextColor.WHITE)
                        .append(uuidComponent)));
        player.sendMessage(Component.text("")); // Пустая строка после вывода
        ((MyPurpurPlugin) teamService.getPlugin()).debugTeamAction("Админ запросил UUID команды", player.getName(), teamName);
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (!(sender instanceof Player)) {
            return suggestions;
        }

        String commandName = command.getName().toLowerCase();
        if (commandName.equals("getteamuuid") && args.length == 1) {
            // Предлагаем названия команд
            List<String> teamNames = teamService.getTeamNames();
            for (String teamName : teamNames) {
                if (teamName.toLowerCase().startsWith(args[0].toLowerCase())) {
                    suggestions.add(teamName);
                }
            }
        }

        return suggestions;
    }
}
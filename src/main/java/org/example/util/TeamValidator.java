package org.example.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.example.service.TeamService;
import org.jetbrains.annotations.NotNull;

/**
 * Утилитарный класс для валидации команд и лидерства.
 */
public class TeamValidator {

    /**
     * Проверяет, существует ли команда и является ли игрок её лидером.
     *
     * @param teamService Сервис управления командами
     * @param teamName Название команды
     * @param leader Игрок, выполняющий действие
     * @param action Описание действия (например, "распустить команду")
     * @return true, если команда не существует или игрок не лидер, иначе false
     */
    public static boolean isTeamAndLeadershipInvalid(@NotNull TeamService teamService, String teamName, Player leader, String action) {
        if (!teamService.getTeamNames().contains(teamName)) {
            TeamMessageUtils.sendTeamMessage(leader, TeamMessageUtils.teamDoesNotExistMessage(teamName));
            return true;
        }
        String currentLeader = teamService.getTeamLeader(teamName);
        if (!leader.getName().equals(currentLeader)) {
            Component prefix = TeamUtils.createPrefixComponent(teamService.getTeamPrefix(teamName), teamService.getTeamColor(teamName));
            TeamMessageUtils.sendTeamMessage(leader, Component.text("❌ Только лидер может " + action + " ", NamedTextColor.RED)
                    .append(prefix)
                    .append(Component.text(teamName, NamedTextColor.WHITE))
                    .append(Component.text(" !", NamedTextColor.RED)));
            return true;
        }
        return false;
    }
}
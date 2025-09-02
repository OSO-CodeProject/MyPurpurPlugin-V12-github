package org.example;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/**
 * Утилитарный класс для работы с сообщениями команд.
 */
public class TeamMessageUtils {

    /**
     * Отправляет сообщение игроку.
     *
     * @param player Игрок, которому отправляется сообщение
     * @param message Сообщение для отправки
     */
    public static void sendTeamMessage(Player player, Component message) {
        if (player != null) {
            player.sendMessage(message);
        }
    }

    /**
     * Формирует сообщение об ошибке, если команда уже существует.
     *
     * @param teamName Название команды
     * @return Форматированное сообщение об ошибке
     */
    public static Component teamAlreadyExistsMessage(String teamName) {
        return Component.text("❌ Команда ", NamedTextColor.RED)
                .append(Component.text(teamName, NamedTextColor.WHITE))
                .append(Component.text(" уже существует ", NamedTextColor.RED))
                .append(Component.text("!", NamedTextColor.RED));
    }

    /**
     * Формирует сообщение об ошибке, если команда не существует.
     *
     * @param teamName Название команды
     * @return Форматированное сообщение об ошибке
     */
    public static Component teamDoesNotExistMessage(String teamName) {
        String name = (teamName != null) ? teamName : "неизвестная команда";
        return Component.text("❌ Команда ", NamedTextColor.RED)
                .append(Component.text(name, NamedTextColor.WHITE))
                .append(Component.text(" не существует!", NamedTextColor.RED));
    }

    /**
     * Формирует сообщение об ошибке, если игрок уже состоит в команде.
     *
     * @param teamName Название команды
     * @param prefixComponent Префикс команды
     * @return Форматированное сообщение об ошибке
     */
    public static Component playerAlreadyInTeamMessage(String teamName, Component prefixComponent) {
        return Component.text("❌ Вы уже состоите в команде ", NamedTextColor.RED)
                .append(prefixComponent)
                .append(Component.text(teamName, NamedTextColor.WHITE))
                .append(Component.text(" !", NamedTextColor.RED));
    }

    /**
     * Формирует сообщение об ошибке, если игрок не состоит в команде.
     *
     * @param teamName Название команды
     * @param prefixComponent Префикс команды
     * @return Форматированное сообщение об ошибке
     */
    public static Component playerNotInTeamMessage(String teamName, Component prefixComponent) {
        return Component.text("❌ Вы не состоите в команде ", NamedTextColor.RED)
                .append(prefixComponent)
                .append(Component.text(teamName, NamedTextColor.WHITE))
                .append(Component.text(" !", NamedTextColor.RED));
    }

    /**
     * Формирует предупреждение о необходимости сократить команду.
     *
     * @param max    новый лимит участников
     * @param minutes время на сокращение
     * @param excess количество лишних участников
     * @return сообщение-предупреждение
     */
    public static Component deadlineWarningMessage(int max, int minutes, int excess) {
        return Component.text("Максимум игроков уменьшен до ", NamedTextColor.YELLOW)
                .append(Component.text(max, NamedTextColor.WHITE))
                .append(Component.text(". У вас ", NamedTextColor.YELLOW))
                .append(Component.text(minutes + " мин", NamedTextColor.WHITE))
                .append(Component.text(", чтобы исключить ", NamedTextColor.YELLOW))
                .append(Component.text(excess + " участника(ов)", NamedTextColor.WHITE))
                .append(Component.text(".", NamedTextColor.YELLOW));
    }

    /**
     * Сообщение после принудительного удаления участников.
     *
     * @param removed количество удалённых участников
     * @return сообщение для лидера
     */
    public static Component forcedRemovalMessage(int removed) {
        return Component.text("Из вашей команды удалено ", NamedTextColor.RED)
                .append(Component.text(removed + " участника(ов)", NamedTextColor.WHITE))
                .append(Component.text(" из-за превышения лимита.", NamedTextColor.RED));
    }
}
package org.example.command.sub;

import java.util.Collections;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** Интерфейс для обработки подкоманд /team. */
public interface SubCommand {

  /**
   * Выполняет подкоманду.
   *
   * @param player игрок, вызвавший команду
   * @param args аргументы команды
   * @return всегда {@code true}, чтобы Bukkit не выводил сообщение об ошибке
   */
  boolean execute(@NotNull Player player, @NotNull String[] args);

  /**
   * Возвращает варианты автодополнения для подкоманды.
   *
   * @param sender отправитель команды
   * @param args аргументы команды
   * @return список вариантов автодополнения
   */
  default List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
    return Collections.emptyList();
  }
}

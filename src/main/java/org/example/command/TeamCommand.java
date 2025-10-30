package org.example.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.example.MyPurpurPlugin;
import org.example.command.sub.AcceptInviteSubCommand;
import org.example.command.sub.CreateSubCommand;
import org.example.command.sub.CancelRequestSubCommand;
import org.example.command.sub.DeclineInviteSubCommand;
import org.example.command.sub.HelpSubCommand;
import org.example.command.sub.JoinSubCommand;
import org.example.command.sub.LeaveSubCommand;
import org.example.command.sub.ListSubCommand;
import org.example.command.sub.MembersSubCommand;
import org.example.command.sub.InviteSubCommand;
import org.example.command.sub.InvitesSubCommand;
import org.example.command.sub.RequestSubCommand;
import org.example.command.sub.RequestsSubCommand;
import org.example.command.sub.SubCommand;
import org.example.config.PluginConfig;
import org.example.config.JoinMode;
import org.example.service.TeamService;
import org.example.util.TeamMessageUtils;
import org.jetbrains.annotations.NotNull;

/** Обработчик команд, связанных с командами игроков. */
public class TeamCommand implements org.bukkit.command.CommandExecutor, TabCompleter {

  private final TeamService teamManager;
  private final PluginConfig pluginConfig;
  private final Map<String, SubCommand> subCommands = new LinkedHashMap<>();
  private static final Set<String> INVITE_ONLY_SUB_COMMANDS =
      Set.of("invite", "invites", "accept", "decline");
  private static final Set<String> REQUEST_ONLY_SUB_COMMANDS =
      Set.of("request", "cancelrequest", "requests");

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
    subCommands.put("invite", new InviteSubCommand(teamManager));
    subCommands.put("invites", new InvitesSubCommand(teamManager));
    subCommands.put("accept", new AcceptInviteSubCommand(teamManager));
    subCommands.put("decline", new DeclineInviteSubCommand(teamManager));
    subCommands.put("request", new RequestSubCommand(teamManager));
    subCommands.put("cancelrequest", new CancelRequestSubCommand(teamManager));
    subCommands.put("requests", new RequestsSubCommand(teamManager));
    subCommands.put("help", new HelpSubCommand());
  }

  @Override
  public boolean onCommand(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String label,
      @NotNull String[] args) {
    // Проверяем, что команду запускает игрок, потому что остальные отправители не
    // могут взаимодействовать с командной системой корректно.
    if (!(sender instanceof Player player)) {
      sender.sendMessage(
          Component.text("❌ Эту команду может использовать только игрок!", NamedTextColor.RED));
      return true;
    }

    // Убеждаемся, что у игрока есть базовое разрешение на работу с командами.
    if (!player.hasPermission("mypurpurplugin.team")) {
      player.sendMessage(
          Component.text("❌ У вас нет прав для использования этой команды!", NamedTextColor.RED));
      return true;
    }

    // При необходимости проверяем статус оператора, чтобы выполнить требование
    // конфигурации.
    if (pluginConfig.isTeamCommandRequiresOp()
        && !player.hasPermission("mypurpurplugin.team.requiresop")) {
      player.sendMessage(
          Component.text(
              "❌ У вас нет прав для использования этой команды! Требуется разрешение "
                  + "mypurpurplugin.team.requiresop.",
              NamedTextColor.RED));
      return true;
    }

    // Без подкоманды показываем корректное использование команды.
    if (args.length < 1) {
      sendUsage(player);
      return true;
    }

    // Приводим имя подкоманды к единому виду и логируем её запуск для отладки.
    String subCommandName = args[0].toLowerCase(Locale.ROOT);
    ((MyPurpurPlugin) teamManager.getPlugin())
        .debugTeamAction("Команда /team выполнена игроком", player.getName(), null);

    // Находим обработчик подкоманды; если он отсутствует, уведомляем игрока.
    SubCommand subCommand = subCommands.get(subCommandName);
    if (subCommand == null) {
      sendUnknownSubCommandMessage(player);
      return true;
    }
    if (!isSubCommandEnabled(subCommandName)) {
      TeamMessageUtils.sendTeamMessage(player, TeamMessageUtils.invitesDisabledMessage());
      return true;
    }

    return subCommand.execute(player, args);
  }

  private void sendUsage(Player player) {
    player.sendMessage(
        Component.text(
            "❌ Использование: /team <" + getSubCommandList() + "> [аргументы]",
            NamedTextColor.RED));
  }

  private void sendUnknownSubCommandMessage(Player player) {
    player.sendMessage(
        Component.text(
            "❌ Неизвестная подкоманда! Используйте: /team <" + getSubCommandList() + ">",
            NamedTextColor.RED));
  }

  private String getSubCommandList() {
    return subCommands.keySet().stream()
        .filter(this::isSubCommandEnabled)
        .collect(Collectors.joining(" | "));
  }

  @Override
  public List<String> onTabComplete(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String alias,
      @NotNull String[] args) {
    // При вводе первой части команды предлагаем подходящие названия подкоманд.
    if (args.length <= 1) {
      String partial = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
      List<String> suggestions = new ArrayList<>();
      for (String name : subCommands.keySet()) {
        if (!isSubCommandEnabled(name)) {
          continue;
        }
        if (name.startsWith(partial)) {
          suggestions.add(name);
        }
      }
      return suggestions;
    }

    // Для остальных аргументов делегируем генерацию подсказок конкретной
    // подкоманде.
    SubCommand subCommand = subCommands.get(args[0].toLowerCase(Locale.ROOT));
    if (subCommand == null) {
      return new ArrayList<>();
    }
    if (!isSubCommandEnabled(args[0].toLowerCase(Locale.ROOT))) {
      return new ArrayList<>();
    }

    return subCommand.tabComplete(sender, args);
  }

  private boolean isSubCommandEnabled(@NotNull String name) {
    if (INVITE_ONLY_SUB_COMMANDS.contains(name)) {
      return teamManager.getJoinMode() == JoinMode.INVITE_ONLY;
    }
    if (REQUEST_ONLY_SUB_COMMANDS.contains(name)) {
      return teamManager.getJoinMode() == JoinMode.REQUEST_TO_JOIN;
    }
    return true;
  }
}

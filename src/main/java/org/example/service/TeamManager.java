package org.example.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.example.MyPurpurPlugin;
import org.example.config.PluginConfig;
import org.example.listener.TeamChatListener;
import org.example.model.Team;
import org.example.util.TeamMessageUtils;
import org.example.util.TeamUtils;
import org.example.util.TeamValidator;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Реализация сервиса управления командами с использованием UUID как ключа.
 */
public class TeamManager implements TeamService {

    private final JavaPlugin plugin;
    private final PluginConfig pluginConfig;
    private final Map<UUID, Team> teams; // Храним команды по UUID
    private final Map<UUID, Long> deadlines; // дедлайны для команд
    private FileConfiguration teamsConfig;
    private File teamsFile;

    public TeamManager(@NotNull JavaPlugin plugin) {
        this.plugin = plugin;
        this.pluginConfig = new PluginConfig(plugin);
        this.teams = new HashMap<>();
        this.deadlines = new HashMap<>();
        loadTeams();
        enforceTeamSizes();
        startDeadlineTask();
    }

    /**
     * Загружает данные о командах из файла teams.yml.
     * Если файл не существует, он будет создан.
     * Добавлена обработка исключений для повышения надёжности.
     */
    private void loadTeams() {
        teamsFile = new File(plugin.getDataFolder(), "teams.yml");

        // Проверяем и создаём папку плагина, если её нет
        if (!plugin.getDataFolder().exists()) {
            if (!plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().severe("⚠ Не удалось создать папку плагина: " + plugin.getDataFolder().getAbsolutePath());
                return;
            }
            ((MyPurpurPlugin) plugin).debug("📂 Папка плагина создана: " + plugin.getDataFolder().getAbsolutePath());
        }

        // Создаём файл teams.yml, если он отсутствует
        if (!teamsFile.exists()) {
            try {
                if (teamsFile.createNewFile()) {
                    plugin.getLogger().info("📂 Файл teams.yml успешно создан!");
                }
            } catch (IOException e) {
                plugin.getLogger().severe("⚠ Ошибка создания teams.yml: " + e.getMessage());
            }
        }

        // Загружаем данные из файла
        try {
            teamsConfig = YamlConfiguration.loadConfiguration(teamsFile);
            teams.clear();
            deadlines.clear();

            var teamsSection = teamsConfig.getConfigurationSection("teams");
            if (teamsSection != null) {
                for (String teamIdStr : teamsSection.getKeys(false)) {
                    UUID teamId = UUID.fromString(teamIdStr);
                    String name = teamsConfig.getString("teams." + teamIdStr + ".name", "");
                    String leader = teamsConfig.getString("teams." + teamIdStr + ".leader", "");
                    String prefix = teamsConfig.getString("teams." + teamIdStr + ".prefix", "");
                    String color = teamsConfig.getString("teams." + teamIdStr + ".color", "WHITE");
                    Team team = new Team(teamId, name, leader, prefix, color);
                    team.setMembers(teamsConfig.getStringList("teams." + teamIdStr + ".members"));
                    long deadline = teamsConfig.getLong("teams." + teamIdStr + ".deadline", 0L);
                    if (deadline > 0L) {
                        deadlines.put(team.getId(), deadline);
                    }
                    teams.put(team.getId(), team);
                }
            }
            ((MyPurpurPlugin) plugin).debug("📂 Файл teams.yml загружен, загружено команд: " + teams.size());
        } catch (Exception e) {
            plugin.getLogger().warning("⚠ Ошибка при загрузке teams.yml: " + e.getMessage());
        }
    }

    /**
     * Сохраняет данные о командах в файл teams.yml.
     * Полностью перезаписывает секцию "teams" для обеспечения актуальности.
     */
    private void saveTeams() {
        teamsConfig.set("teams", null); // Очищаем старую секцию перед сохранением
        for (Map.Entry<UUID, Team> entry : teams.entrySet()) {
            UUID teamId = entry.getKey();
            Team team = entry.getValue();
            String path = "teams." + teamId.toString();
            teamsConfig.set(path + ".name", team.getName());
            teamsConfig.set(path + ".leader", team.getLeader());
            teamsConfig.set(path + ".members", team.getMembers());
            teamsConfig.set(path + ".prefix", team.getPrefix());
            teamsConfig.set(path + ".color", team.getColor().toString().toUpperCase());
            Long deadline = deadlines.get(teamId);
            if (deadline != null) {
                teamsConfig.set(path + ".deadline", deadline);
            }
        }
        try {
            teamsConfig.save(teamsFile);
            ((MyPurpurPlugin) plugin).debug("📂 Файл teams.yml успешно сохранён.");
        } catch (IOException e) {
            plugin.getLogger().warning("⚠ Ошибка при сохранении teams.yml: " + e.getMessage());
        }
    }

    // Вспомогательные методы
    private Team getTeamByName(String teamName) {
        return teams.values().stream()
                .filter(team -> team.getName().equals(teamName))
                .findFirst()
                .orElse(null);
    }

    @Override
    public UUID getTeamIdByName(String teamName) {
        return teams.entrySet().stream()
                .filter(entry -> entry.getValue().getName().equals(teamName))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    /**
     * Уведомляет администраторов о действиях с командами.
     * Добавлена проверка на null для prefixComponent.
     */
    private void notifyAdmins(Player leader, List<Component> messageParts, String teamName, Component prefixComponent) {
        if (pluginConfig.shouldNotifyAdmins()) {
            Component adminMessage = Component.text("ℹ Игрок ", NamedTextColor.YELLOW)
                    .append(Component.text(leader.getName(), NamedTextColor.WHITE));

            for (Component part : messageParts) {
                adminMessage = adminMessage.append(part != null ? part : Component.empty());
            }

            if (!teamName.isEmpty() && prefixComponent != null && !prefixComponent.equals(Component.empty())) {
                adminMessage = adminMessage.append(Component.text(" ", NamedTextColor.YELLOW))
                        .append(prefixComponent)
                        .append(Component.text(teamName, NamedTextColor.WHITE));
            }

            adminMessage = adminMessage.append(Component.text(" !", NamedTextColor.YELLOW));

            for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
                if (onlinePlayer.hasPermission("mypurpurplugin.admin")) {
                    TeamMessageUtils.sendTeamMessage(onlinePlayer, adminMessage);
                }
            }
        }
    }

    @Override
    public void createTeam(String teamName, String prefix, String color, @NotNull Player leader) {
        ((MyPurpurPlugin) plugin).debugTeamAction("Попытка создания команды", leader.getName(), teamName);
        String existingTeam = getPlayerTeam(leader);
        if (existingTeam != null) {
            Team team = getTeamByName(existingTeam);
            Component prefixComponent = team.getPrefixComponent();
            TeamMessageUtils.sendTeamMessage(leader, TeamMessageUtils.playerAlreadyInTeamMessage(existingTeam, prefixComponent));
            return;
        }

        if (getTeamByName(teamName) != null) {
            TeamMessageUtils.sendTeamMessage(leader, TeamMessageUtils.teamAlreadyExistsMessage(teamName));
            return;
        }

        if (TeamUtils.isPrefixLengthInvalid(prefix, pluginConfig, leader) || TeamUtils.isTeamNameLengthInvalid(teamName, pluginConfig, leader)) {
            return;
        }

        // Проверка уникальности префикса
        for (Team team : teams.values()) {
            if (team.getPrefix().equals(prefix)) {
                Component prefixComponent = team.getPrefixComponent();
                TeamMessageUtils.sendTeamMessage(leader, Component.text("❌ Префикс ", NamedTextColor.RED)
                        .append(Component.text("'" + prefix + "' ", NamedTextColor.WHITE))
                        .append(Component.text("уже используется другой командой ", NamedTextColor.RED))
                        .append(prefixComponent)
                        .append(Component.text(team.getName(), NamedTextColor.WHITE))
                        .append(Component.text(" !", NamedTextColor.WHITE)));
                return;
            }
        }

        NamedTextColor teamColor = NamedTextColor.NAMES.value(color.toLowerCase());
        if (teamColor == null) {
            TeamMessageUtils.sendTeamMessage(leader, Component.text("❌ Неверный цвет команды.\nИспользуйте, например: RED, BLUE, GREEN и т.д.", NamedTextColor.RED));
            return;
        }

        // Проверка уникальности цвета
        for (Team team : teams.values()) {
            if (team.getColor().equals(teamColor)) {
                Component prefixComponent = team.getPrefixComponent();
                TeamMessageUtils.sendTeamMessage(leader, Component.text("❌ Цвет ", NamedTextColor.RED)
                        .append(Component.text("'" + color + "' ", NamedTextColor.WHITE))
                        .append(Component.text("уже используется другой командой ", NamedTextColor.RED))
                        .append(prefixComponent)
                        .append(Component.text(team.getName(), NamedTextColor.WHITE))
                        .append(Component.text(" !", NamedTextColor.WHITE)));
                return;
            }
        }

        Team team = new Team(teamName, leader.getName(), prefix, color);
        teams.put(team.getId(), team);
        saveTeams();

        Component prefixComponent = team.getPrefixComponent();
        Component message = Component.text("✅ Команда ", NamedTextColor.GREEN)
                .append(prefixComponent)
                .append(Component.text(teamName, NamedTextColor.WHITE))
                .append(Component.text(" создана ", NamedTextColor.GREEN))
                .append(Component.text("!", NamedTextColor.GREEN))
                .append(Component.text("\nВы теперь лидер команды ", NamedTextColor.WHITE))
                .append(prefixComponent)
                .append(Component.text(teamName, NamedTextColor.WHITE))
                .append(Component.text(" !", NamedTextColor.WHITE));
        TeamMessageUtils.sendTeamMessage(leader, message);
        updatePlayerPrefixes(teamName);

        notifyAdmins(leader, List.of(Component.text(" создал команду ", NamedTextColor.YELLOW)), teamName, prefixComponent);

        plugin.getLogger().info("Команда " + teamName + " успешно создана лидером " + leader.getName());
    }

    @Override
    public void addPlayerToTeam(String teamName, @NotNull Player player) {
        ((MyPurpurPlugin) plugin).debugTeamAction("Попытка добавления игрока в команду", player.getName(), teamName);
        Team team = getTeamByName(teamName);
        if (team == null) {
            TeamMessageUtils.sendTeamMessage(player, TeamMessageUtils.teamDoesNotExistMessage(teamName));
            return;
        }

        // Проверяем, состоит ли игрок в какой-либо команде
        for (Team t : teams.values()) {
            if (t.hasMember(player.getName())) {
                Component prefixComponent = t.getPrefixComponent();
                TeamMessageUtils.sendTeamMessage(player, TeamMessageUtils.playerAlreadyInTeamMessage(t.getName(), prefixComponent));
                return;
            }
            if (t.isLeader(player.getName())) {
                Component prefixComponent = t.getPrefixComponent();
                TeamMessageUtils.sendTeamMessage(player, Component.text("❌ Вы не можете вступить в другую команду, так как являетесь лидером команды ", NamedTextColor.RED)
                        .append(prefixComponent)
                        .append(Component.text(t.getName(), NamedTextColor.WHITE))
                        .append(Component.text(" !", NamedTextColor.WHITE)));
                return;
            }
        }

        // Проверка максимального количества участников
        int maxMembers = pluginConfig.getMaxMembers();
        if (maxMembers > 0 && team.getMembers().size() >= maxMembers) {
            Component prefixComponent = team.getPrefixComponent();
            TeamMessageUtils.sendTeamMessage(player, Component.text("❌ Команда ", NamedTextColor.RED)
                    .append(prefixComponent)
                    .append(Component.text(teamName + " полная! Максимум участников: " + maxMembers + ".", NamedTextColor.WHITE)));
            return;
        }

        team.addMember(player.getName());
        saveTeams();

        Component prefixComponent = team.getPrefixComponent();
        Component message = Component.text("✅ Игрок ", NamedTextColor.GREEN)
                .append(Component.text(player.getName(), NamedTextColor.WHITE))
                .append(Component.text(" вступил в команду ", NamedTextColor.GREEN))
                .append(prefixComponent)
                .append(Component.text(teamName, NamedTextColor.WHITE))
                .append(Component.text(" !", NamedTextColor.GREEN));
        TeamUtils.notifyTeamMembers(teamName, this, message, Set.of(player.getName()));
        Component playerMessage = Component.text("✅ Вы вступили в команду ", NamedTextColor.GREEN)
                .append(prefixComponent)
                .append(Component.text(teamName, NamedTextColor.WHITE))
                .append(Component.text(" !", NamedTextColor.GREEN));
        TeamMessageUtils.sendTeamMessage(player, playerMessage);
        updatePlayerPrefixes(teamName);
        plugin.getLogger().info("Игрок " + player.getName() + " вступил в команду " + teamName);
    }

    @Override
    public void disbandTeam(String teamName, @NotNull Player leader) {
        ((MyPurpurPlugin) plugin).debugTeamAction("Попытка распустить команду", leader.getName(), teamName);
        Team team = getTeamByName(teamName);
        if (TeamValidator.isTeamAndLeadershipInvalid(this, teamName, leader, "распустить команду")) {
            return;
        }

        Component prefixComponent = team.getPrefixComponent();
        UUID teamId = getTeamIdByName(teamName);
        teams.remove(teamId);
        teamsConfig.set("teams." + teamId.toString(), null); // Удаляем команду из teams.yml
        saveTeams();

        Component leaderMessage = Component.text("✅ Команда ", NamedTextColor.GREEN)
                .append(prefixComponent)
                .append(Component.text(teamName, NamedTextColor.WHITE))
                .append(Component.text(" распущена ", NamedTextColor.RED))
                .append(Component.text("!", NamedTextColor.RED));
        TeamMessageUtils.sendTeamMessage(leader, leaderMessage);
        Component memberMessage = Component.text("❌ Команда ", NamedTextColor.WHITE)
                .append(prefixComponent)
                .append(Component.text(teamName, NamedTextColor.WHITE))
                .append(Component.text(" была ", NamedTextColor.WHITE))
                .append(Component.text("распущена ", NamedTextColor.RED))
                .append(Component.text("лидером ", NamedTextColor.WHITE))
                .append(Component.text(leader.getName(), NamedTextColor.WHITE))
                .append(Component.text(" !", NamedTextColor.WHITE));
        TeamUtils.notifyTeamMembers(teamName, this, memberMessage, Set.of(leader.getName()));

        // Сбрасываем префиксы для всех участников команды
        for (String memberName : team.getMembers()) {
            Player member = plugin.getServer().getPlayer(memberName);
            if (member != null) {
                plugin.getServer().getPluginManager().callEvent(new TeamChatListener.PlayerPrefixUpdateEvent(member, null));
            }
        }

        notifyAdmins(leader, List.of(Component.text(" распустил команду ", NamedTextColor.YELLOW)), teamName, prefixComponent);

        plugin.getLogger().info("Команда " + teamName + " распущена лидером " + leader.getName());
    }

    // Остальные методы опущены для краткости, но их можно добавить при необходимости
    // Вспомогательные методы

    @Override
    public void removePlayerFromTeam(String teamName, @NotNull Player player) {
        ((MyPurpurPlugin) plugin).debugTeamAction("Попытка удаления игрока из команды", player.getName(), teamName);
        Team team = getTeamByName(teamName);
        if (team == null) {
            TeamMessageUtils.sendTeamMessage(player, TeamMessageUtils.teamDoesNotExistMessage(teamName));
            return;
        }

        if (!team.hasMember(player.getName())) {
            Component prefixComponent = team.getPrefixComponent();
            TeamMessageUtils.sendTeamMessage(player, TeamMessageUtils.playerNotInTeamMessage(teamName, prefixComponent));
            return;
        }

        team.removeMember(player.getName());
        Component prefixComponent = team.getPrefixComponent();

        boolean teamDisbanded = false;
        if (team.isLeader(player.getName())) {
            if (team.getMembers().isEmpty()) {
                teamDisbanded = true;
                teams.remove(team.getId());
                saveTeams();
                Component message = Component.text("✅ Команда ", NamedTextColor.GREEN)
                        .append(prefixComponent)
                        .append(Component.text(teamName, NamedTextColor.WHITE))
                        .append(Component.text(" распущена", NamedTextColor.RED))
                        .append(Component.text(", так как вы были ", NamedTextColor.WHITE))
                        .append(Component.text("единственным участником ", NamedTextColor.GREEN))
                        .append(Component.text("!", NamedTextColor.GREEN));
                TeamMessageUtils.sendTeamMessage(player, message);
                notifyAdmins(player, List.of(
                        Component.text(" распустил команду ", NamedTextColor.YELLOW),
                        prefixComponent,
                        Component.text(teamName, NamedTextColor.WHITE),
                        Component.text(", так как был последним участником ", NamedTextColor.YELLOW)
                ), "", Component.empty());
            } else {
                String newLeader = team.getMembers().getFirst();
                team.setLeader(newLeader);
                saveTeams();
                Component leaderMessage = Component.text("✅ Вы покинули команду ", NamedTextColor.GREEN)
                        .append(prefixComponent)
                        .append(Component.text(teamName, NamedTextColor.WHITE))
                        .append(Component.text(", лидерство перешло ", NamedTextColor.GREEN))
                        .append(Component.text(newLeader, NamedTextColor.WHITE))
                        .append(Component.text(" !", NamedTextColor.GREEN));
                TeamMessageUtils.sendTeamMessage(player, leaderMessage);
                Player newLeaderPlayer = plugin.getServer().getPlayer(newLeader);
                if (newLeaderPlayer != null) {
                    Component newLeaderMessage = Component.text("👑 Теперь вы лидер команды ", NamedTextColor.GREEN)
                            .append(prefixComponent)
                            .append(Component.text(teamName, NamedTextColor.WHITE))
                            .append(Component.text(" !", NamedTextColor.GREEN));
                    TeamMessageUtils.sendTeamMessage(newLeaderPlayer, newLeaderMessage);
                }
                Component leaveMessage = Component.text("⚡ Игрок ", NamedTextColor.YELLOW)
                        .append(Component.text(player.getName(), NamedTextColor.WHITE))
                        .append(Component.text(" покинул ", NamedTextColor.RED))
                        .append(Component.text("команду ", NamedTextColor.YELLOW))
                        .append(prefixComponent)
                        .append(Component.text(teamName, NamedTextColor.WHITE))
                        .append(Component.text(" !", NamedTextColor.YELLOW));
                TeamUtils.notifyTeamMembers(teamName, this, leaveMessage, Set.of(player.getName()));
                Component memberMessage = Component.text("⚡ Лидерство в команде ", NamedTextColor.YELLOW)
                        .append(prefixComponent)
                        .append(Component.text(teamName, NamedTextColor.WHITE))
                        .append(Component.text(" перешло к ", NamedTextColor.YELLOW))
                        .append(Component.text(newLeader, NamedTextColor.WHITE))
                        .append(Component.text(" !", NamedTextColor.WHITE));
                TeamUtils.notifyTeamMembers(teamName, this, memberMessage, Set.of(player.getName(), newLeader));
            }
        } else {
            saveTeams();
            Component playerMessage = Component.text("✅ Вы покинули команду ", NamedTextColor.GREEN)
                    .append(prefixComponent)
                    .append(Component.text(teamName, NamedTextColor.WHITE))
                    .append(Component.text(" !", NamedTextColor.GREEN));
            TeamMessageUtils.sendTeamMessage(player, playerMessage);
            Component memberMessage = Component.text("⚡ Игрок ", NamedTextColor.YELLOW)
                    .append(Component.text(player.getName(), NamedTextColor.WHITE))
                    .append(Component.text(" покинул ", NamedTextColor.RED))
                    .append(Component.text("команду ", NamedTextColor.YELLOW))
                    .append(prefixComponent)
                    .append(Component.text(teamName, NamedTextColor.WHITE))
                    .append(Component.text(" !", NamedTextColor.YELLOW));
            TeamUtils.notifyTeamMembers(teamName, this, memberMessage, Set.of(player.getName()));
        }

        ((MyPurpurPlugin) plugin).debug("Вызываем событие сброса префикса для игрока " + player.getName());
        plugin.getServer().getPluginManager().callEvent(new TeamChatListener.PlayerPrefixUpdateEvent(player, null));
        ((MyPurpurPlugin) plugin).debug("Событие сброса префикса вызвано для игрока " + player.getName());

        if (!teamDisbanded) {
            updatePlayerPrefixes(teamName);
        }

        plugin.getLogger().info("Игрок " + player.getName() + " покинул команду " + teamName);
    }

    @Override
    public void kickPlayerFromTeam(String teamName, @NotNull Player leader, @NotNull String targetName) {
        ((MyPurpurPlugin) plugin).debugTeamAction("Попытка исключения игрока из команды", targetName, teamName);
        Team team = getTeamByName(teamName);
        if (TeamValidator.isTeamAndLeadershipInvalid(this, teamName, leader, "выгнать участника из команды")) {
            return;
        }

        if (!team.hasMember(targetName)) {
            Component prefixComponent = team.getPrefixComponent();
            TeamMessageUtils.sendTeamMessage(leader, Component.text("❌ Игрок ", NamedTextColor.RED)
                    .append(Component.text(targetName, NamedTextColor.WHITE))
                    .append(Component.text(" не состоит в вашей команде ", NamedTextColor.RED))
                    .append(prefixComponent)
                    .append(Component.text(teamName, NamedTextColor.WHITE))
                    .append(Component.text(" !", NamedTextColor.WHITE)));
            return;
        }

        if (targetName.equals(leader.getName())) {
            Component prefixComponent = team.getPrefixComponent();
            TeamMessageUtils.sendTeamMessage(leader, Component.text("❌ Вы не можете выгнать себя из команды ", NamedTextColor.RED)
                    .append(prefixComponent)
                    .append(Component.text(teamName, NamedTextColor.WHITE))
                    .append(Component.text(" !", NamedTextColor.WHITE)));
            return;
        }

        team.removeMember(targetName);
        saveTeams();

        Component prefixComponent = team.getPrefixComponent();
        Component leaderMessage = Component.text("✅ Игрок ", NamedTextColor.GREEN)
                .append(Component.text(targetName, NamedTextColor.WHITE))
                .append(Component.text(" выгнан из команды ", NamedTextColor.GREEN))
                .append(prefixComponent)
                .append(Component.text(teamName, NamedTextColor.WHITE))
                .append(Component.text(" !", NamedTextColor.GREEN));
        TeamMessageUtils.sendTeamMessage(leader, leaderMessage);
        Player target = plugin.getServer().getPlayer(targetName);
        if (target != null) {
            Component targetMessage = Component.text("❌ Вы были выгнаны из команды ", NamedTextColor.RED)
                    .append(prefixComponent)
                    .append(Component.text(teamName, NamedTextColor.WHITE))
                    .append(Component.text(" лидером ", NamedTextColor.RED))
                    .append(Component.text(leader.getName(), NamedTextColor.WHITE))
                    .append(Component.text(" !", NamedTextColor.RED));
            TeamMessageUtils.sendTeamMessage(target, targetMessage);
            plugin.getServer().getPluginManager().callEvent(new TeamChatListener.PlayerPrefixUpdateEvent(target, null));
        }
        Component memberMessage = Component.text("⚡ Игрок ", NamedTextColor.YELLOW)
                .append(Component.text(targetName, NamedTextColor.WHITE))
                .append(Component.text(" был исключён из команды ", NamedTextColor.YELLOW))
                .append(prefixComponent)
                .append(Component.text(teamName, NamedTextColor.WHITE))
                .append(Component.text(" лидером ", NamedTextColor.YELLOW))
                .append(Component.text(leader.getName(), NamedTextColor.WHITE))
                .append(Component.text(" !", NamedTextColor.YELLOW));
        TeamUtils.notifyTeamMembers(teamName, this, memberMessage, Set.of(leader.getName(), targetName));

        notifyAdmins(leader, List.of(
                Component.text(" исключил игрока ", NamedTextColor.YELLOW),
                Component.text(targetName, NamedTextColor.WHITE),
                Component.text(" в команде ", NamedTextColor.YELLOW),
                prefixComponent,
                Component.text(teamName, NamedTextColor.WHITE)
        ), "", Component.empty());

        updatePlayerPrefixes(teamName);
        plugin.getLogger().info("Игрок " + targetName + " исключён из команды " + teamName + " лидером " + leader.getName());
    }

    @Override
    public void transferLeadership(String teamName, @NotNull Player leader, @NotNull Player newLeader) {
        ((MyPurpurPlugin) plugin).debugTeamAction("Попытка передачи лидерства в команде", leader.getName(), teamName);
        Team team = getTeamByName(teamName);
        if (TeamValidator.isTeamAndLeadershipInvalid(this, teamName, leader, "передавать лидерство в команде")) {
            return;
        }

        if (leader.getName().equals(newLeader.getName())) {
            Component prefixComponent = team.getPrefixComponent();
            TeamMessageUtils.sendTeamMessage(leader, Component.text("❌ Вы не можете передать лидерство самому себе в команде ", NamedTextColor.RED)
                    .append(prefixComponent)
                    .append(Component.text(teamName, NamedTextColor.WHITE))
                    .append(Component.text(" !", NamedTextColor.WHITE)));
            return;
        }

        if (!team.hasMember(newLeader.getName())) {
            Component prefixComponent = team.getPrefixComponent();
            TeamMessageUtils.sendTeamMessage(leader, Component.text("❌ Игрок ", NamedTextColor.RED)
                    .append(Component.text(newLeader.getName(), NamedTextColor.WHITE))
                    .append(Component.text(" не состоит в вашей команде ", NamedTextColor.RED))
                    .append(prefixComponent)
                    .append(Component.text(teamName, NamedTextColor.WHITE))
                    .append(Component.text(" !", NamedTextColor.WHITE)));
            return;
        }

        team.setLeader(newLeader.getName());
        saveTeams();

        Component prefixComponent = team.getPrefixComponent();
        Component leaderMessage = Component.text("✅ Лидерство передано ", NamedTextColor.GREEN)
                .append(Component.text(newLeader.getName(), NamedTextColor.WHITE))
                .append(Component.text(" в команде ", NamedTextColor.GREEN))
                .append(prefixComponent)
                .append(Component.text(teamName, NamedTextColor.WHITE))
                .append(Component.text(" !", NamedTextColor.GREEN));
        TeamMessageUtils.sendTeamMessage(leader, leaderMessage);
        Component newLeaderMessage = Component.text("👑 Теперь вы лидер команды ", NamedTextColor.GREEN)
                .append(prefixComponent)
                .append(Component.text(teamName, NamedTextColor.WHITE))
                .append(Component.text(" !", NamedTextColor.GREEN));
        TeamMessageUtils.sendTeamMessage(newLeader, newLeaderMessage);
        Component memberMessage = Component.text("⚡ Лидер команды ", NamedTextColor.YELLOW)
                .append(prefixComponent)
                .append(Component.text(teamName, NamedTextColor.WHITE))
                .append(Component.text(" сменился на ", NamedTextColor.YELLOW))
                .append(Component.text(newLeader.getName(), NamedTextColor.WHITE))
                .append(Component.text(" !", NamedTextColor.YELLOW));
        TeamUtils.notifyTeamMembers(teamName, this, memberMessage, Set.of(leader.getName(), newLeader.getName()));

        notifyAdmins(leader, List.of(
                Component.text(" передал лидерство игроку ", NamedTextColor.YELLOW),
                Component.text(newLeader.getName(), NamedTextColor.WHITE)
        ), teamName, prefixComponent);

        updatePlayerPrefixes(teamName);
        plugin.getLogger().info("Лидерство в команде " + teamName + " передано от " + leader.getName() + " к " + newLeader.getName());
    }

    @Override
    public void renameTeam(String oldTeamName, String newTeamName, @NotNull Player leader) {
        ((MyPurpurPlugin) plugin).debugTeamAction("Попытка переименования команды", leader.getName(), oldTeamName);
        Team team = getTeamByName(oldTeamName);
        if (TeamValidator.isTeamAndLeadershipInvalid(this, oldTeamName, leader, "переименовать команду")) {
            return;
        }

        if (getTeamByName(newTeamName) != null) {
            TeamMessageUtils.sendTeamMessage(leader, TeamMessageUtils.teamAlreadyExistsMessage(newTeamName));
            return;
        }

        if (TeamUtils.isTeamNameLengthInvalid(newTeamName, pluginConfig, leader)) {
            return;
        }

        Component oldPrefixComponent = team.getPrefixComponent();
        team.setName(newTeamName);
        saveTeams();

        Component prefixComponent = team.getPrefixComponent();
        Component leaderMessage = Component.text("✅ Команда переименована в ", NamedTextColor.GREEN)
                .append(prefixComponent)
                .append(Component.text(newTeamName, NamedTextColor.WHITE))
                .append(Component.text(" !", NamedTextColor.GREEN));
        TeamMessageUtils.sendTeamMessage(leader, leaderMessage);

        notifyAdmins(leader, List.of(
                Component.text(" переименовал команду ", NamedTextColor.YELLOW),
                oldPrefixComponent,
                Component.text(oldTeamName, NamedTextColor.WHITE),
                Component.text(" в ", NamedTextColor.YELLOW),
                prefixComponent,
                Component.text(newTeamName, NamedTextColor.WHITE)
        ), "", Component.empty());

        updatePlayerPrefixes(newTeamName);
        plugin.getLogger().info("Команда " + oldTeamName + " переименована в " + newTeamName + " лидером " + leader.getName());
    }

    @Override
    public void setTeamPrefix(String teamName, String newPrefix, @NotNull Player leader) {
        ((MyPurpurPlugin) plugin).debugTeamAction("Попытка изменения префикса команды", leader.getName(), teamName);
        Team team = getTeamByName(teamName);
        if (TeamValidator.isTeamAndLeadershipInvalid(this, teamName, leader, "изменить префикс команды")) {
            return;
        }

        if (TeamUtils.isPrefixLengthInvalid(newPrefix, pluginConfig, leader)) {
            return;
        }

        for (Team t : teams.values()) {
            if (!t.getName().equals(teamName) && t.getPrefix().equals(newPrefix)) {
                Component prefixComponent = t.getPrefixComponent();
                TeamMessageUtils.sendTeamMessage(leader, Component.text("❌ Префикс ", NamedTextColor.RED)
                        .append(Component.text("'" + newPrefix + "' ", NamedTextColor.WHITE))
                        .append(Component.text("уже используется другой командой ", NamedTextColor.RED))
                        .append(prefixComponent)
                        .append(Component.text(t.getName(), NamedTextColor.WHITE))
                        .append(Component.text(" !", NamedTextColor.WHITE)));
                return;
            }
        }

        String oldPrefix = team.getPrefix();
        team.setPrefix(newPrefix);
        saveTeams();

        Component oldPrefixComponent = TeamUtils.createPrefixComponent(oldPrefix, team.getColor());
        Component prefixComponent = team.getPrefixComponent();
        Component leaderMessage = Component.text("✅ Префикс команды ", NamedTextColor.GREEN)
                .append(Component.text(teamName, NamedTextColor.WHITE))
                .append(Component.text(" изменён с ", NamedTextColor.GREEN))
                .append(oldPrefixComponent)
                .append(Component.text(" на ", NamedTextColor.GREEN))
                .append(prefixComponent)
                .append(Component.text(" !", NamedTextColor.GREEN));
        TeamMessageUtils.sendTeamMessage(leader, leaderMessage);
        TeamUtils.notifyTeamMembers(teamName, this, leaderMessage, Set.of(leader.getName()));

        notifyAdmins(leader, List.of(
                Component.text(" изменил префикс команды с ", NamedTextColor.YELLOW),
                Component.text(oldPrefix, NamedTextColor.WHITE),
                Component.text(" на ", NamedTextColor.YELLOW),
                Component.text(newPrefix, NamedTextColor.WHITE)
        ), teamName, prefixComponent);

        updatePlayerPrefixes(teamName);
        plugin.getLogger().info("Префикс команды " + teamName + " изменён на " + newPrefix + " лидером " + leader.getName());
    }

    @Override
    public void setTeamColor(String teamName, String newColor, @NotNull Player leader) {
        ((MyPurpurPlugin) plugin).debugTeamAction("Попытка изменения цвета команды", leader.getName(), teamName);
        Team team = getTeamByName(teamName);
        if (TeamValidator.isTeamAndLeadershipInvalid(this, teamName, leader, "изменить цвет команды")) {
            return;
        }

        NamedTextColor colorEnum = NamedTextColor.NAMES.value(newColor.toLowerCase());
        if (colorEnum == null) {
            TeamMessageUtils.sendTeamMessage(leader, Component.text("❌ Неверный цвет команды.\nИспользуйте, например: RED, BLUE, GREEN и т.д.", NamedTextColor.RED));
            return;
        }

        for (Team t : teams.values()) {
            if (!t.getName().equals(teamName) && t.getColor().equals(colorEnum)) {
                Component prefixComponent = t.getPrefixComponent();
                TeamMessageUtils.sendTeamMessage(leader, Component.text("❌ Цвет ", NamedTextColor.RED)
                        .append(Component.text("'" + newColor + "' ", NamedTextColor.WHITE))
                        .append(Component.text("уже используется другой командой ", NamedTextColor.RED))
                        .append(prefixComponent)
                        .append(Component.text(t.getName(), NamedTextColor.WHITE))
                        .append(Component.text(" !", NamedTextColor.WHITE)));
                return;
            }
        }

        NamedTextColor oldColor = team.getColor();
        team.setColor(newColor);
        saveTeams();

        Component oldPrefixComponent = TeamUtils.createPrefixComponent(team.getPrefix(), oldColor);
        Component prefixComponent = team.getPrefixComponent();
        Component leaderMessage = Component.text("✅ Цвет команды ", NamedTextColor.GREEN)
                .append(Component.text(teamName, NamedTextColor.WHITE))
                .append(Component.text(" изменён с ", NamedTextColor.GREEN))
                .append(oldPrefixComponent)
                .append(Component.text(" на ", NamedTextColor.GREEN))
                .append(prefixComponent)
                .append(Component.text(" !", NamedTextColor.GREEN));
        TeamMessageUtils.sendTeamMessage(leader, leaderMessage);
        TeamUtils.notifyTeamMembers(teamName, this, leaderMessage, Set.of(leader.getName()));

        notifyAdmins(leader, List.of(
                Component.text(" изменил цвет команды с ", NamedTextColor.YELLOW),
                oldPrefixComponent,
                Component.text(" на ", NamedTextColor.YELLOW),
                prefixComponent
        ), teamName, prefixComponent);

        updatePlayerPrefixes(teamName);
        plugin.getLogger().info("Цвет команды " + teamName + " изменён на " + newColor + " лидером " + leader.getName());
    }

    @Override
    public void updatePlayerPrefixes(String teamName) {
        ((MyPurpurPlugin) plugin).debugTeamAction("Обновление префиксов для команды", null, teamName);
        Team team = getTeamByName(teamName);
        if (team == null) {
            ((MyPurpurPlugin) plugin).debug("Команда " + teamName + " больше не существует, пропускаем обновление префиксов");
            return;
        }

        Component prefixComponent = team.getPrefixComponent();
        for (String memberName : team.getMembers()) {
            Player member = plugin.getServer().getPlayerExact(memberName);
            if (member != null) {
                plugin.getServer().getPluginManager().callEvent(new TeamChatListener.PlayerPrefixUpdateEvent(member, prefixComponent));
                member.playerListName(prefixComponent.append(Component.text(member.getName(), NamedTextColor.WHITE)));
            }
        }
    }

    @Override
    public String getPlayerTeam(@NotNull Player player) {
        return teams.values().stream()
                .filter(team -> team.hasMember(player.getName()))
                .map(Team::getName)
                .findFirst()
                .orElse(null);
    }

    @Override
    public @NotNull List<String> getTeamMembers(String teamName) {
        Team team = getTeamByName(teamName);
        return team != null ? team.getMembers() : new ArrayList<>();
    }

    @Override
    public @NotNull List<String> getTeamNames() {
        return teams.values().stream()
                .map(Team::getName)
                .collect(Collectors.toList());
    }

    @Override
    public String getTeamPrefix(String teamName) {
        Team team = getTeamByName(teamName);
        return team != null ? team.getPrefix() : "";
    }

    @Override
    public @NotNull NamedTextColor getTeamColor(String teamName) {
        Team team = getTeamByName(teamName);
        return team != null ? team.getColor() : NamedTextColor.WHITE;
    }

    @Override
    public String getTeamLeader(String teamName) {
        Team team = getTeamByName(teamName);
        return team != null ? team.getLeader() : null;
    }

    @Override
    public @NotNull JavaPlugin getPlugin() {
        return plugin;
    }

    @Override
    public @NotNull PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    @Override
    public Long getTeamDeadline(String teamName) {
        UUID id = getTeamIdByName(teamName);
        return id != null ? deadlines.get(id) : null;
    }

    @Override
    public void reloadConfig() {
        pluginConfig.reloadConfig();
        loadTeams();
        enforceTeamSizes();
    }

    private void startDeadlineTask() {
        long seconds = pluginConfig.getDeadlineNotifyPeriodSeconds();
        long period = 20L * Math.max(1, seconds);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::checkDeadlines, period, period);
    }

    private void enforceTeamSizes() {
        int max = pluginConfig.getMaxMembers();
        if (max <= 0) {
            deadlines.clear();
            return;
        }
        boolean changed = false;
        for (Map.Entry<UUID, Team> entry : teams.entrySet()) {
            Team team = entry.getValue();
            int size = team.getMembers().size();
            if (size > max) {
                if (!pluginConfig.isEnforceMaxMembersOnReload()) {
                    plugin.getLogger().warning("Команда " + team.getName() + " превышает лимит участников (" + size + "/" + max + ")");
                    continue;
                }
                int grace = pluginConfig.getGracePeriodMinutes();
                if (grace > 0) {
                    long deadline = System.currentTimeMillis() + grace * 60L * 1000L;
                    Long old = deadlines.put(entry.getKey(), deadline);
                    if (old == null) {
                        Player leader = plugin.getServer().getPlayer(team.getLeader());
                        if (leader != null) {
                            int excess = size - max;
                            TeamMessageUtils.sendTeamMessage(leader,
                                    TeamMessageUtils.deadlineWarningMessage(max, grace, excess));
                        }
                    }
                    changed = true;
                } else {
                    removeExtraPlayers(entry.getKey(), max);
                    changed = true;
                }
            } else {
                if (deadlines.remove(entry.getKey()) != null) {
                    changed = true;
                }
            }
        }
        if (changed) {
            saveTeams();
        }
    }

    private void checkDeadlines() {
        long now = System.currentTimeMillis();
        int max = pluginConfig.getMaxMembers();
        if (max <= 0) {
            deadlines.clear();
            return;
        }
        boolean changed = false;
        Iterator<Map.Entry<UUID, Long>> it = deadlines.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            UUID teamId = entry.getKey();
            long deadline = entry.getValue();
            Team team = teams.get(teamId);
            if (team == null) {
                it.remove();
                changed = true;
                continue;
            }
            int size = team.getMembers().size();
            if (size <= max) {
                it.remove();
                changed = true;
                Player leader = plugin.getServer().getPlayer(team.getLeader());
                if (leader != null && pluginConfig.getDeadlineDisplayMode().equalsIgnoreCase("SCOREBOARD")) {
                    ScoreboardManager manager = plugin.getServer().getScoreboardManager();
                    if (manager != null) {
                        leader.setScoreboard(manager.getMainScoreboard());
                    }
                }
                continue;
            }
            if (deadline <= now) {
                removeExtraPlayers(teamId, max);
                it.remove();
                changed = true;
                Player leader = plugin.getServer().getPlayer(team.getLeader());
                if (leader != null && pluginConfig.getDeadlineDisplayMode().equalsIgnoreCase("SCOREBOARD")) {
                    ScoreboardManager manager = plugin.getServer().getScoreboardManager();
                    if (manager != null) {
                        leader.setScoreboard(manager.getMainScoreboard());
                    }
                }
            } else {
                Player leader = plugin.getServer().getPlayer(team.getLeader());
                if (leader != null) {
                    long remainingMillis = deadline - now;
                    long remainingSeconds = remainingMillis / 1000;
                    long minutes = remainingSeconds / 60;
                    long seconds = remainingSeconds % 60;
                    int excess = size - max;
                    Component message = TeamMessageUtils.deadlineRemainingMessage(minutes, seconds, excess);
                    String mode = pluginConfig.getDeadlineDisplayMode();
                    if ("ACTION_BAR".equalsIgnoreCase(mode)) {
                        leader.sendActionBar(message);
                    } else if ("SCOREBOARD".equalsIgnoreCase(mode)) {
                        showDeadlineScoreboard(leader, minutes, seconds, excess);
                    } else {
                        TeamMessageUtils.sendTeamMessage(leader, message);
                    }
                }
            }
        }
        if (changed) {
            saveTeams();
        }
    }

    private void showDeadlineScoreboard(Player player, long minutes, long seconds, int excess) {
        ScoreboardManager manager = plugin.getServer().getScoreboardManager();
        if (manager == null) return;
        Scoreboard board = manager.getNewScoreboard();
        Objective obj = board.registerNewObjective("deadline", "dummy", Component.text("Deadline"));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.getScore("Осталось: " + minutes + "m " + seconds + "s").setScore(2);
        obj.getScore("Удалить: " + excess).setScore(1);
        player.setScoreboard(board);
    }

    private void removeExtraPlayers(UUID teamId, int max) {
        Team team = teams.get(teamId);
        if (team == null) return;
        int toRemove = team.getMembers().size() - max;
        if (toRemove <= 0) return;
        List<String> removedMembers = new ArrayList<>();
        for (String member : new ArrayList<>(team.getMembers())) {
            if (member.equals(team.getLeader())) continue;
            if (!plugin.getServer().getOfflinePlayer(member).isOnline()) {
                team.removeMember(member);
                removedMembers.add(member);
                plugin.getLogger().info("Игрок " + member + " удалён из команды " + team.getName());
                toRemove--;
            }
            if (toRemove <= 0) break;
        }
        if (toRemove > 0) {
            List<String> members = new ArrayList<>(team.getMembers());
            Collections.reverse(members);
            for (String member : members) {
                if (member.equals(team.getLeader())) continue;
                if (!removedMembers.contains(member)) {
                    team.removeMember(member);
                    removedMembers.add(member);
                    Player player = plugin.getServer().getPlayer(member);
                    if (player != null) {
                        plugin.getServer().getPluginManager().callEvent(
                                new TeamChatListener.PlayerPrefixUpdateEvent(player, null));
                        player.playerListName(Component.text(player.getName(), NamedTextColor.WHITE));
                        plugin.getLogger().info("Сброшен префикс для игрока " + player.getName() +
                                " после исключения из команды " + team.getName());
                    }
                    plugin.getLogger().info("Игрок " + member + " удалён из команды " + team.getName());
                    toRemove--;
                }
                if (toRemove <= 0) break;
            }
        }
        if (!removedMembers.isEmpty()) {
            Player leader = plugin.getServer().getPlayer(team.getLeader());
            if (leader != null) {
                TeamMessageUtils.sendTeamMessage(leader,
                        TeamMessageUtils.forcedRemovalMessage(removedMembers.size()));
            }
            plugin.getLogger().info("Из команды " + team.getName() + " удалено " + removedMembers.size() + " участника(ов): " + removedMembers);
        }
    }
}

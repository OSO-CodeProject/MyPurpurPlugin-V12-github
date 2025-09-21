package org.example.listener;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import be.seeseemelk.mockbukkit.entity.PlayerMock;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.example.MockBukkitTestBase;
import org.example.config.PluginConfig;
import org.example.service.TeamService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TeamChatListenerTest extends MockBukkitTestBase {

  private PluginConfig pluginConfig;
  private StubTeamService teamService;
  private TeamChatListener listener;

  @BeforeEach
  void setUpListener() {
    pluginConfig = mock(PluginConfig.class);
    when(pluginConfig.isForceWhiteChat()).thenReturn(false);
    when(pluginConfig.getMaxMembers()).thenReturn(5);

    teamService = new StubTeamService(plugin, pluginConfig);
    listener = new TeamChatListener(teamService);
    server.getPluginManager().registerEvents(listener, plugin);
  }

  @AfterEach
  void tearDownListener() {
    HandlerList.unregisterAll(listener);
  }

  @Test
  void playerJoinUpdatesPrefix() {
    PlayerMock leader = server.addPlayer("Leader");
    teamService.createTeam("Alpha", leader, "TAG", NamedTextColor.RED);
    teamService.setDeadline("Alpha", System.currentTimeMillis() + Duration.ofMinutes(5).toMillis());

    PlayerJoinEvent event = new PlayerJoinEvent(leader, (Component) null);
    server.getPluginManager().callEvent(event);

    Component listName = leader.playerListName();
    assertNotNull(listName, "Префикс должен быть установлен");
    assertEquals("[TAG] Leader", PlainTextComponentSerializer.plainText().serialize(listName));
  }

  @Test
  void chatEventAppliesPrefixAndForcesWhiteChat() {
    when(pluginConfig.isForceWhiteChat()).thenReturn(true);
    PlayerMock player = server.addPlayer("Chatter");
    teamService.createTeam("Beta", player, "B", NamedTextColor.BLUE);

    Component prefixComponent = Component.text("[B] ", NamedTextColor.BLUE);
    server
        .getPluginManager()
        .callEvent(new TeamChatListener.PlayerPrefixUpdateEvent(player, prefixComponent));

    Component message = Component.text("Привет", NamedTextColor.GREEN);
    Set<Audience> viewers = new HashSet<>();
    ChatRenderer initialRenderer = ChatRenderer.defaultRenderer();
    SignedMessage signedMessage = SignedMessage.system("Привет", message);
    AsyncChatEvent event =
        new AsyncChatEvent(
            false,
            player,
            viewers,
            initialRenderer,
            message,
            message,
            signedMessage);

    server.getPluginManager().callEvent(event);

    Component rendered =
        event.renderer().render(player, Component.text(player.getName()), event.message(), Audience.empty());
    assertEquals("[B] Chatter: Привет", PlainTextComponentSerializer.plainText().serialize(rendered));
  }

  @Test
  void prefixUpdateEventAdjustsPlayerListName() {
    PlayerMock player = server.addPlayer("Updater");
    teamService.assignPlayer(player, "Gamma", "G", NamedTextColor.GOLD);

    Component prefixComponent = Component.text("[G] ", NamedTextColor.GOLD);
    server
        .getPluginManager()
        .callEvent(new TeamChatListener.PlayerPrefixUpdateEvent(player, prefixComponent));
    assertEquals("[G] Updater", PlainTextComponentSerializer.plainText().serialize(player.playerListName()));

    server
        .getPluginManager()
        .callEvent(new TeamChatListener.PlayerPrefixUpdateEvent(player, null));
    assertEquals("Updater", PlainTextComponentSerializer.plainText().serialize(player.playerListName()));
  }

  @Test
  void playerQuitClearsCachedPrefix() {
    PlayerMock player = server.addPlayer("Quitter");
    teamService.assignPlayer(player, "Omega", "O", NamedTextColor.GRAY);
    Component prefix = Component.text("[O] ", NamedTextColor.GRAY);
    server
        .getPluginManager()
        .callEvent(new TeamChatListener.PlayerPrefixUpdateEvent(player, prefix));

    PlayerQuitEvent quitEvent = new PlayerQuitEvent(player, (Component) null);
    server.getPluginManager().callEvent(quitEvent);

    player.playerListName(Component.text("Manual", NamedTextColor.WHITE));
    server.getPluginManager().callEvent(new TeamChatListener.PlayerPrefixUpdateEvent(player, prefix));

    assertEquals("[O] Manual", PlainTextComponentSerializer.plainText().serialize(player.playerListName()));
  }

  @Test
  void clearingPrefixRestoresCustomPlayerListName() {
    PlayerMock player = server.addPlayer("Custom");
    Component customName =
        Component.text("Fancy ", NamedTextColor.GOLD)
            .append(Component.text("Name", NamedTextColor.AQUA));
    player.playerListName(customName);
    teamService.assignPlayer(player, "Stylish", "S", NamedTextColor.LIGHT_PURPLE);

    Component prefix = Component.text("[S] ", NamedTextColor.LIGHT_PURPLE);
    server
        .getPluginManager()
        .callEvent(new TeamChatListener.PlayerPrefixUpdateEvent(player, prefix));

    assertEquals(prefix.append(customName), player.playerListName());

    server
        .getPluginManager()
        .callEvent(new TeamChatListener.PlayerPrefixUpdateEvent(player, null));

    assertEquals(customName, player.playerListName());
  }

  @Test
  void customListNameRestoredAfterMultiplePrefixUpdates() {
    PlayerMock player = server.addPlayer("Formatter");
    Component customName =
        Component.text("Rainbow ", NamedTextColor.LIGHT_PURPLE)
            .append(Component.text("Player", NamedTextColor.GREEN));
    player.playerListName(customName);
    teamService.assignPlayer(player, "Palette", "P", NamedTextColor.DARK_AQUA);

    Component firstPrefix = Component.text("[P] ", NamedTextColor.DARK_AQUA);
    Component secondPrefix = Component.text("{P} ", NamedTextColor.DARK_PURPLE);

    server
        .getPluginManager()
        .callEvent(new TeamChatListener.PlayerPrefixUpdateEvent(player, firstPrefix));
    assertEquals(firstPrefix.append(customName), player.playerListName());

    server
        .getPluginManager()
        .callEvent(new TeamChatListener.PlayerPrefixUpdateEvent(player, secondPrefix));
    assertEquals(secondPrefix.append(customName), player.playerListName());

    server
        .getPluginManager()
        .callEvent(new TeamChatListener.PlayerPrefixUpdateEvent(player, null));
    assertEquals(customName, player.playerListName());
  }

  @Test
  void clearCachedPrefixesRestoresOriginalCustomListName() {
    PlayerMock player = server.addPlayer("Shutdown");
    Component customName =
        Component.text("Fancy ", NamedTextColor.GOLD)
            .append(Component.text("List", NamedTextColor.DARK_GREEN));
    player.playerListName(customName);
    teamService.assignPlayer(player, "Shutdowners", "S", NamedTextColor.GREEN);

    Component prefix = Component.text("[S] ", NamedTextColor.GREEN);
    server
        .getPluginManager()
        .callEvent(new TeamChatListener.PlayerPrefixUpdateEvent(player, prefix));
    assertEquals(prefix.append(customName), player.playerListName());

    listener.clearCachedPrefixes();

    assertEquals(customName, player.playerListName());
  }

  @Test
  void chatEventUsesLatestCachedPrefixImmediately() {
    PlayerMock player = server.addPlayer("Immediate");
    teamService.assignPlayer(player, "Delta", "D", NamedTextColor.DARK_GREEN);

    Component prefix = Component.text("[NEW] ", NamedTextColor.DARK_GREEN);
    server
        .getPluginManager()
        .callEvent(new TeamChatListener.PlayerPrefixUpdateEvent(player, prefix));

    Component message = Component.text("Test", NamedTextColor.YELLOW);
    Set<Audience> viewers = new HashSet<>();
    ChatRenderer initialRenderer = ChatRenderer.defaultRenderer();
    SignedMessage signedMessage = SignedMessage.system("Test", message);
    AsyncChatEvent event =
        new AsyncChatEvent(
            false, player, viewers, initialRenderer, message, message, signedMessage);

    assertDoesNotThrow(() -> server.getPluginManager().callEvent(event));

    Component rendered =
        event.renderer()
            .render(player, Component.text(player.getName()), event.message(), Audience.empty());
    assertEquals(
        "[NEW] Immediate: Test", PlainTextComponentSerializer.plainText().serialize(rendered));
  }

  @Test
  void warmCacheAppliesPrefixForOnlinePlayersOnPluginEnable() {
    HandlerList.unregisterAll(listener);
    listener.clearCachedPrefixes();

    PlayerMock player = server.addPlayer("Reloaded");
    teamService.assignPlayer(player, "Reload", "R", NamedTextColor.DARK_AQUA);

    listener = new TeamChatListener(teamService);
    server.getPluginManager().registerEvents(listener, plugin);

    Component message = Component.text("Hi", NamedTextColor.WHITE);
    Set<Audience> viewers = new HashSet<>();
    ChatRenderer initialRenderer = ChatRenderer.defaultRenderer();
    SignedMessage signedMessage = SignedMessage.system("Hi", message);
    AsyncChatEvent event =
        new AsyncChatEvent(false, player, viewers, initialRenderer, message, message, signedMessage);

    server.getPluginManager().callEvent(event);

    Component rendered =
        event.renderer().render(player, Component.text(player.getName()), event.message(), Audience.empty());
    assertEquals(
        "[R] Reloaded: Hi", PlainTextComponentSerializer.plainText().serialize(rendered));
  }

  private static class StubTeamService implements TeamService {
    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final PluginConfig config;
    private final Map<String, NamedTextColor> colors = new HashMap<>();
    private final Map<String, String> prefixes = new HashMap<>();
    private final Map<String, Set<java.util.UUID>> members = new HashMap<>();
    private final Map<java.util.UUID, String> playerTeams = new HashMap<>();
    private final Map<String, java.util.UUID> leaders = new HashMap<>();
    private final Map<String, java.util.UUID> teamIds = new HashMap<>();
    private final Map<String, Long> deadlines = new HashMap<>();

    StubTeamService(org.bukkit.plugin.java.JavaPlugin plugin, PluginConfig config) {
      this.plugin = plugin;
      this.config = config;
    }

    void createTeam(String name, PlayerMock leader, String prefix, NamedTextColor color) {
      java.util.UUID teamId = java.util.UUID.randomUUID();
      String key = name.toLowerCase();
      teamIds.put(key, teamId);
      leaders.put(key, leader.getUniqueId());
      prefixes.put(key, prefix);
      colors.put(key, color);
      assignPlayer(leader, name, prefix, color);
    }

    void assignPlayer(PlayerMock player, String name, String prefix, NamedTextColor color) {
      String key = name.toLowerCase();
      java.util.UUID teamId = teamIds.computeIfAbsent(key, value -> java.util.UUID.randomUUID());
      teamIds.put(key, teamId);
      prefixes.put(key, prefix);
      colors.put(key, color);
      members.computeIfAbsent(key, value -> new HashSet<>()).add(player.getUniqueId());
      playerTeams.put(player.getUniqueId(), name);
      leaders.putIfAbsent(key, player.getUniqueId());
    }

    void setDeadline(String name, long value) {
      deadlines.put(name.toLowerCase(), value);
    }

    @Override
    public void createTeam(String teamName, String prefix, String color, org.bukkit.entity.Player leader) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void addPlayerToTeam(String teamName, org.bukkit.entity.Player player) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void removePlayerFromTeam(String teamName, org.bukkit.entity.Player player) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void kickPlayerFromTeam(
        String teamName, org.bukkit.entity.Player leader, String targetName) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void transferLeadership(
        String teamName, org.bukkit.entity.Player leader, org.bukkit.entity.Player newLeader) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void disbandTeam(String teamName, org.bukkit.entity.Player leader) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void renameTeam(String oldTeamName, String newTeamName, org.bukkit.entity.Player leader) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setTeamPrefix(String teamName, String newPrefix, org.bukkit.entity.Player leader) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setTeamColor(String teamName, String newColor, org.bukkit.entity.Player leader) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void updatePlayerPrefixes(String teamName) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getPlayerTeam(org.bukkit.entity.Player player) {
      return playerTeams.get(player.getUniqueId());
    }

    @Override
    public java.util.List<java.util.UUID> getTeamMembers(String teamName) {
      return java.util.List.copyOf(members.getOrDefault(teamName.toLowerCase(), Set.of()));
    }

    @Override
    public java.util.List<String> getTeamNames() {
      return java.util.List.copyOf(prefixes.keySet());
    }

    @Override
    public String getTeamPrefix(String teamName) {
      return prefixes.getOrDefault(teamName.toLowerCase(), "");
    }

    @Override
    public NamedTextColor getTeamColor(String teamName) {
      return colors.getOrDefault(teamName.toLowerCase(), NamedTextColor.WHITE);
    }

    @Override
    public java.util.UUID getTeamLeaderId(String teamName) {
      return leaders.get(teamName.toLowerCase());
    }

    @Override
    public org.bukkit.plugin.java.JavaPlugin getPlugin() {
      return plugin;
    }

    @Override
    public PluginConfig getPluginConfig() {
      return config;
    }

    @Override
    public boolean isEnforceMaxMembersOnReload() {
      return false;
    }

    @Override
    public boolean isGracePeriodEnabled() {
      return false;
    }

    @Override
    public void reloadConfig() {
      throw new UnsupportedOperationException();
    }

    @Override
    public java.util.UUID getTeamIdByName(String teamName) {
      return teamIds.get(teamName.toLowerCase());
    }

    @Override
    public Long getTeamDeadline(String teamName) {
      return deadlines.get(teamName.toLowerCase());
    }
  }
}

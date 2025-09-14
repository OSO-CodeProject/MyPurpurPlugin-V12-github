package org.example.command;

import static org.junit.jupiter.api.Assertions.*;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.lang.reflect.Field;
import java.util.Set;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandMap;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.player.PlayerQuitEvent;
import org.example.MyPurpurPlugin;
import org.example.config.PluginConfig;
import org.example.service.DeadlineScheduler;
import org.example.service.TeamManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Integration tests for team commands and related events. */
class TeamCommandTest {

  private ServerMock server;
  private MyPurpurPlugin plugin;
  private TeamManager teamManager;
  private PluginConfig pluginConfig;
  private FileConfiguration config;
  private DeadlineScheduler scheduler;

  @BeforeEach
  void setUp() throws Exception {
    server = MockBukkit.mock();
    plugin = MockBukkit.load(MyPurpurPlugin.class);

    Field tmField = MyPurpurPlugin.class.getDeclaredField("teamManager");
    tmField.setAccessible(true);
    teamManager = (TeamManager) tmField.get(plugin);

    Field cfgField = MyPurpurPlugin.class.getDeclaredField("pluginConfig");
    cfgField.setAccessible(true);
    pluginConfig = (PluginConfig) cfgField.get(plugin);

    Field internal = PluginConfig.class.getDeclaredField("config");
    internal.setAccessible(true);
    config = (FileConfiguration) internal.get(pluginConfig);

    Field schedField = TeamManager.class.getDeclaredField("scheduler");
    schedField.setAccessible(true);
    scheduler = (DeadlineScheduler) schedField.get(teamManager);
  }

  @AfterEach
  void tearDown() {
    MockBukkit.unmock();
  }

  @Test
  void dispatchesCreateJoinAndListCommands() {
    CommandMap commandMap = server.getCommandMap();

    PlayerMock leader = server.addPlayer("Leader");
    leader.addAttachment(plugin, "mypurpurplugin.team", true);
    assertTrue(commandMap.dispatch(leader, "team create Alpha AA WHITE"));
    assertEquals("Alpha", teamManager.getPlayerTeam(leader));

    PlayerMock member = server.addPlayer("Member");
    member.addAttachment(plugin, "mypurpurplugin.team", true);
    assertTrue(commandMap.dispatch(member, "team join Alpha"));
    assertEquals("Alpha", teamManager.getPlayerTeam(member));

    // Clear previous messages before listing teams
    while (member.nextComponentMessage() != null) {}

    assertTrue(commandMap.dispatch(member, "team list"));
    boolean found = false;
    Component msg;
    while ((msg = member.nextComponentMessage()) != null) {
      String plain = PlainTextComponentSerializer.plainText().serialize(msg);
      if (plain.contains("Alpha")) {
        found = true;
        break;
      }
    }
    assertTrue(found, "Team list should contain created team");
  }

  @Test
  void deniesCommandWithoutPermission() {
    CommandMap commandMap = server.getCommandMap();
    PlayerMock player = server.addPlayer("Player");
    player.setOp(false);
    player.addAttachment(plugin, "mypurpurplugin.team", false);
    commandMap.dispatch(player, "team create Bravo BB WHITE");
    assertFalse(teamManager.getTeamNames().contains("Bravo"));
  }

  @Test
  void preventsJoinWhenTeamFull() {
    config.set("team.max-members", 1);
    CommandMap commandMap = server.getCommandMap();

    PlayerMock leader = server.addPlayer("Leader");
    leader.addAttachment(plugin, "mypurpurplugin.team", true);
    commandMap.dispatch(leader, "team create FullTeam FT WHITE");

    PlayerMock member = server.addPlayer("Member");
    member.addAttachment(plugin, "mypurpurplugin.team", true);
    commandMap.dispatch(member, "team join FullTeam");

    Component msg = member.nextComponentMessage();
    String plain = PlainTextComponentSerializer.plainText().serialize(msg);
    assertTrue(plain.contains("Команда полная"));
    assertNull(teamManager.getPlayerTeam(member));
  }

  @Test
  void rejectsInvalidTeamName() {
    CommandMap commandMap = server.getCommandMap();
    PlayerMock player = server.addPlayer("Leader");
    player.addAttachment(plugin, "mypurpurplugin.team", true);

    commandMap.dispatch(player, "team create AB ZZ WHITE");
    Component msg = player.nextComponentMessage();
    String plain = PlainTextComponentSerializer.plainText().serialize(msg);
    assertTrue(plain.contains("Название команды слишком короткое"));
    assertFalse(teamManager.getTeamNames().contains("AB"));
  }

  @Test
  void asyncChatEventAddsTeamPrefix() {
    CommandMap commandMap = server.getCommandMap();
    PlayerMock leader = server.addPlayer("Leader");
    leader.addAttachment(plugin, "mypurpurplugin.team", true);
    commandMap.dispatch(leader, "team create Alpha AA WHITE");

    Component message = Component.text("hello");
    AsyncChatEvent event =
        new AsyncChatEvent(
            false,
            leader,
            Set.<Audience>of(),
            ChatRenderer.defaultRenderer(),
            message,
            message,
            SignedMessage.system("hello", message));
    server.getPluginManager().callEvent(event);

    Component rendered =
        event.renderer().render(leader, Component.text("Leader"), message, leader);
    String plain = PlainTextComponentSerializer.plainText().serialize(rendered);
    assertEquals("[AA] Leader: hello", plain);
  }

  @Test
  void playerQuitRemovesDeadlineAfterLeaving() {
    // Allow two players initially
    config.set("team.max-members", 2);
    CommandMap commandMap = server.getCommandMap();

    PlayerMock leader = server.addPlayer("Leader");
    leader.addAttachment(plugin, "mypurpurplugin.team", true);
    commandMap.dispatch(leader, "team create Beta BB WHITE");

    PlayerMock member = server.addPlayer("Member");
    member.addAttachment(plugin, "mypurpurplugin.team", true);
    commandMap.dispatch(member, "team join Beta");

    // Reduce max members and enforce deadlines
    config.set("team.max-members", 1);
    scheduler.enforceTeamSizes();
    assertNotNull(teamManager.getTeamDeadline("Beta"));

    // Simulate player quit
    server.getPluginManager().callEvent(new PlayerQuitEvent(member, Component.text("bye")));

    // Remove player from team and ensure deadline cleared
    teamManager.removePlayerFromTeam("Beta", member);
    assertNull(teamManager.getTeamDeadline("Beta"));
  }
}


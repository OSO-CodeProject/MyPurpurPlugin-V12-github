package org.example.command;

import static org.junit.jupiter.api.Assertions.*;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import be.seeseemelk.mockbukkit.plugin.PluginManagerMock;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandMap;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.example.MyPurpurPlugin;
import org.example.config.PluginConfig;
import org.example.service.DeadlineScheduler;
import org.example.service.TeamManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Integration tests for team commands and related events. */
class TeamCommandTest {

  private static ServerMock server;
  private MyPurpurPlugin plugin;
  private TeamManager teamManager;
  private PluginConfig pluginConfig;
  private FileConfiguration config;
  private DeadlineScheduler scheduler;

  @BeforeAll
  static void initServer() {
    server = MockBukkit.mock();
  }

  @BeforeEach
  void setUp() throws Exception {
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
    if (scheduler != null) {
      scheduler.stop();
      scheduler = null;
    }
    File dataFolder = plugin != null ? plugin.getDataFolder() : null;
    if (plugin != null) {
      ServerMock current = MockBukkit.getMock();
      if (current != null) {
        PluginManagerMock pluginManager = current.getPluginManager();
        if (pluginManager.isPluginEnabled(plugin)) {
          pluginManager.disablePlugin(plugin);
        }
        pluginManager.clearPlugins();
      }
      plugin = null;
    }
    if (dataFolder != null && dataFolder.exists()) {
      try (Stream<Path> walk = Files.walk(dataFolder.toPath())) {
        walk.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
      } catch (IOException ignored) {
      }
    }
    if (server != null) {
      new ArrayList<>(server.getOnlinePlayers())
          .forEach(
              player -> {
                if (player instanceof PlayerMock mockPlayer) {
                  mockPlayer.disconnect();
                }
              });
      server.getCommandMap().clearCommands();
    }
    teamManager = null;
    pluginConfig = null;
    config = null;
  }

  @AfterAll
  static void shutdownServer() {
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
  void leaderReceivesConfirmationAndTabPrefixAfterCreate() {
    CommandMap commandMap = server.getCommandMap();

    PlayerMock leader = server.addPlayer("Leader");
    leader.addAttachment(plugin, "mypurpurplugin.team", true);

    assertTrue(commandMap.dispatch(leader, "team create Alpha AA WHITE"));

    Component confirmation = leader.nextComponentMessage();
    assertNotNull(confirmation, "Leader should receive confirmation message");
    String confirmationPlain = PlainTextComponentSerializer.plainText().serialize(confirmation);
    assertTrue(confirmationPlain.contains("Команда создана"));

    Component playerListName = leader.playerListName();
    assertNotNull(playerListName, "Tab name should not be null");
    String playerListPlain = PlainTextComponentSerializer.plainText().serialize(playerListName);
    assertEquals("[AA] Leader", playerListPlain);
  }

  @Test
  void joinCommandMatchesTeamNameIgnoringCase() {
    CommandMap commandMap = server.getCommandMap();

    PlayerMock leader = server.addPlayer("CaseLeader");
    leader.addAttachment(plugin, "mypurpurplugin.team", true);
    assertTrue(commandMap.dispatch(leader, "team create Alpha AA WHITE"));

    PlayerMock member = server.addPlayer("CaseRecruit");
    member.addAttachment(plugin, "mypurpurplugin.team", true);
    assertTrue(commandMap.dispatch(member, "team join alpha"));

    assertEquals("Alpha", teamManager.getPlayerTeam(member));
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
  void transfersLeadershipRestoresScoreboardForPreviousLeader() {
    config.set("team.deadline-display-mode", "SCOREBOARD");
    config.set("team.max-members", 0);
    config.set("team.grace-period-enabled", true);
    config.set("team.grace-period-minutes", 5);

    PlayerMock oldLeader = server.addPlayer("OldLeader");
    PlayerMock newLeader = server.addPlayer("NewLeader");

    teamManager.createTeam("Omega", "OM", "WHITE", oldLeader);
    teamManager.addPlayerToTeam("Omega", newLeader);

    Scoreboard oldLeaderOriginal = oldLeader.getScoreboard();
    Scoreboard newLeaderOriginal = newLeader.getScoreboard();

    config.set("team.max-members", 1);
    scheduler.enforceTeamSizes();

    Scoreboard warnedOldLeaderBoard = oldLeader.getScoreboard();
    assertNotSame(oldLeaderOriginal, warnedOldLeaderBoard);
    assertNotNull(warnedOldLeaderBoard.getObjective("deadlineWarn"));

    teamManager.transferLeadership("Omega", oldLeader, newLeader);

    scheduler.checkDeadlines();

    Scoreboard restoredOldLeaderBoard = oldLeader.getScoreboard();
    assertSame(oldLeaderOriginal, restoredOldLeaderBoard);
    assertNull(restoredOldLeaderBoard.getObjective("deadlineWarn"));

    Scoreboard warnedNewLeaderBoard = newLeader.getScoreboard();
    assertNotSame(newLeaderOriginal, warnedNewLeaderBoard);
    assertNotNull(warnedNewLeaderBoard.getObjective("deadlineWarn"));
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
  void keepsBlockingJoinsAfterReloadWhenEnforcementDisabled() {
    config.set("team.max-members", 0);
    config.set("team.grace-period-enabled", true);
    config.set("team.grace-period-minutes", 5);
    config.set("team.enforce-max-members-on-reload", false);

    PlayerMock leader = server.addPlayer("ReloadLeader");
    PlayerMock memberOne = server.addPlayer("ReloadMemberOne");
    PlayerMock memberTwo = server.addPlayer("ReloadMemberTwo");

    teamManager.createTeam("Reloaded", "RL", "WHITE", leader);
    teamManager.addPlayerToTeam("Reloaded", memberOne);
    teamManager.addPlayerToTeam("Reloaded", memberTwo);

    assertEquals(3, teamManager.getTeamMembers("Reloaded").size());

    config.set("team.max-members", 1);

    scheduler.enforceTeamSizes(true);
    assertTrue(scheduler.getDeadlines().isEmpty());

    teamManager.removePlayerFromTeam("Reloaded", memberOne);

    assertEquals(2, teamManager.getTeamMembers("Reloaded").size());
    Long deadline = scheduler.getTeamDeadline("Reloaded");
    assertNotNull(deadline);

    PlayerMock lateJoiner = server.addPlayer("LateJoiner");
    teamManager.addPlayerToTeam("Reloaded", lateJoiner);

    Component msg = lateJoiner.nextComponentMessage();
    assertNotNull(msg);
    String plain = PlainTextComponentSerializer.plainText().serialize(msg);
    assertTrue(plain.contains("Команда полная"));
    assertNull(teamManager.getPlayerTeam(lateJoiner));
    assertEquals(deadline, scheduler.getTeamDeadline("Reloaded"));
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
  void adminRenameRejectsInvalidTeamNameLength() {
    CommandMap commandMap = server.getCommandMap();

    PlayerMock leader = server.addPlayer("AdminLeader");
    leader.addAttachment(plugin, "mypurpurplugin.team", true);
    leader.addAttachment(plugin, "mypurpurplugin.teamadmin", true);

    assertTrue(commandMap.dispatch(leader, "team create Alpha AA WHITE"));
    assertEquals("Alpha", teamManager.getPlayerTeam(leader));

    while (leader.nextComponentMessage() != null) {}

    assertTrue(commandMap.dispatch(leader, "teamadmin rename AB"));
    Component msg = leader.nextComponentMessage();
    assertNotNull(msg);
    String plain = PlainTextComponentSerializer.plainText().serialize(msg);
    assertTrue(plain.contains("Название команды слишком короткое"));
    assertEquals("Alpha", teamManager.getPlayerTeam(leader));
  }

  @Test
  void adminKickCommandMatchesMemberIgnoringCase() {
    CommandMap commandMap = server.getCommandMap();

    PlayerMock leader = server.addPlayer("KickBoss");
    leader.addAttachment(plugin, "mypurpurplugin.team", true);
    leader.addAttachment(plugin, "mypurpurplugin.teamadmin", true);

    assertTrue(commandMap.dispatch(leader, "team create Delta DD WHITE"));
    assertEquals("Delta", teamManager.getPlayerTeam(leader));

    PlayerMock member = server.addPlayer("KickTarget");
    teamManager.addPlayerToTeam("Delta", member);
    assertEquals("Delta", teamManager.getPlayerTeam(member));

    while (leader.nextComponentMessage() != null) {}

    assertTrue(commandMap.dispatch(leader, "teamadmin kick kicktarget"));

    assertFalse(teamManager.getTeamMembers("Delta").contains("KickTarget"));
    assertNull(teamManager.getPlayerTeam(member));
  }

  @Test
  void adminTransferCommandMatchesMemberIgnoringCase() {
    CommandMap commandMap = server.getCommandMap();

    PlayerMock leader = server.addPlayer("TransferChief");
    leader.addAttachment(plugin, "mypurpurplugin.team", true);
    leader.addAttachment(plugin, "mypurpurplugin.teamadmin", true);

    assertTrue(commandMap.dispatch(leader, "team create Sigma SG WHITE"));
    assertEquals("Sigma", teamManager.getPlayerTeam(leader));

    PlayerMock successor = server.addPlayer("TransferHeir");
    teamManager.addPlayerToTeam("Sigma", successor);
    assertEquals("Sigma", teamManager.getPlayerTeam(successor));

    while (leader.nextComponentMessage() != null) {}

    assertTrue(commandMap.dispatch(leader, "teamadmin transfer transferheir"));

    assertEquals("TransferHeir", teamManager.getTeamLeader("Sigma"));
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

    Component rendered = event.renderer().render(leader, Component.text("Leader"), message, leader);
    String plain = PlainTextComponentSerializer.plainText().serialize(rendered);
    assertEquals("[AA] Leader: hello", plain);
  }

  @Test
  void asyncChatEventUsesDisplayNameWithPrefix() {
    CommandMap commandMap = server.getCommandMap();
    PlayerMock leader = server.addPlayer("Leader");
    leader.addAttachment(plugin, "mypurpurplugin.team", true);
    commandMap.dispatch(leader, "team create Alpha AA WHITE");

    Component message = Component.text("hello");
    Component displayName = Component.text("Captain", NamedTextColor.GOLD);
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

    PlainTextComponentSerializer plainSerializer = PlainTextComponentSerializer.plainText();
    Component rendered = event.renderer().render(leader, displayName, message, leader);

    assertEquals("[AA] Captain: hello", plainSerializer.serialize(rendered));
    Component nameComponent =
        rendered.children().stream()
            .filter(component -> "Captain".equals(plainSerializer.serialize(component)))
            .findFirst()
            .orElseThrow();
    assertEquals(NamedTextColor.GOLD, nameComponent.color());
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

  @Test
  void enforceTeamSizesReusesExistingDeadline() throws Exception {
    CommandMap commandMap = server.getCommandMap();

    PlayerMock leader = server.addPlayer("Leader");
    leader.addAttachment(plugin, "mypurpurplugin.team", true);
    assertTrue(commandMap.dispatch(leader, "team create Beta BB WHITE"));

    PlayerMock member = server.addPlayer("Member");
    member.addAttachment(plugin, "mypurpurplugin.team", true);
    assertTrue(commandMap.dispatch(member, "team join Beta"));

    config.set("team.max-members", 1);
    config.set("team.grace-period-minutes", 5);
    assertEquals(2, teamManager.getTeamMembers("Beta").size());
    assertEquals(1, pluginConfig.getMaxMembers());

    while (leader.nextComponentMessage() != null) {}

    scheduler.enforceTeamSizes();
    Long firstDeadline = teamManager.getTeamDeadline("Beta");
    assertNotNull(firstDeadline);
    Component firstWarning = leader.nextComponentMessage();
    assertNotNull(firstWarning);
    assertNull(leader.nextComponentMessage());

    scheduler.enforceTeamSizes();
    Long secondDeadline = teamManager.getTeamDeadline("Beta");
    assertEquals(firstDeadline, secondDeadline);
    assertNull(leader.nextComponentMessage());
  }

  @Test
  void reloadKeepsExistingDeadlineTimestamp() throws Exception {
    CommandMap commandMap = server.getCommandMap();

    PlayerMock leader = server.addPlayer("Leader");
    leader.addAttachment(plugin, "mypurpurplugin.team", true);
    assertTrue(commandMap.dispatch(leader, "team create Beta BB WHITE"));

    PlayerMock member = server.addPlayer("Member");
    member.addAttachment(plugin, "mypurpurplugin.team", true);
    assertTrue(commandMap.dispatch(member, "team join Beta"));

    config.set("team.max-members", 1);
    config.set("team.grace-period-minutes", 5);
    assertEquals(2, teamManager.getTeamMembers("Beta").size());
    assertEquals(1, pluginConfig.getMaxMembers());
    config.save(new File(plugin.getDataFolder(), "config.yml"));

    while (leader.nextComponentMessage() != null) {}

    scheduler.enforceTeamSizes();
    Long originalDeadline = teamManager.getTeamDeadline("Beta");
    assertNotNull(originalDeadline);
    Component warning = leader.nextComponentMessage();
    assertNotNull(warning);
    assertNull(leader.nextComponentMessage());

    teamManager.reloadConfig();

    Long reloadedDeadline = teamManager.getTeamDeadline("Beta");
    assertEquals(originalDeadline, reloadedDeadline);
    assertNull(leader.nextComponentMessage());
  }

  @Test
  void enforceTeamSizesRemovesOverdueTeamImmediately() throws Exception {
    CommandMap commandMap = server.getCommandMap();

    PlayerMock leader = server.addPlayer("Leader");
    leader.addAttachment(plugin, "mypurpurplugin.team", true);
    assertTrue(commandMap.dispatch(leader, "team create Beta BB WHITE"));

    PlayerMock member = server.addPlayer("Member");
    member.addAttachment(plugin, "mypurpurplugin.team", true);
    assertTrue(commandMap.dispatch(member, "team join Beta"));

    PlayerMock extra = server.addPlayer("Extra");
    extra.addAttachment(plugin, "mypurpurplugin.team", true);
    assertTrue(commandMap.dispatch(extra, "team join Beta"));

    config.set("team.max-members", 1);
    config.set("team.grace-period-minutes", 5);

    while (leader.nextComponentMessage() != null) {}

    scheduler.enforceTeamSizes();
    Long firstDeadline = teamManager.getTeamDeadline("Beta");
    assertNotNull(firstDeadline);

    UUID teamId = teamManager.getTeamIdByName("Beta");
    assertNotNull(teamId);
    scheduler.getDeadlines().put(teamId, System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(1));

    while (leader.nextComponentMessage() != null) {}

    scheduler.enforceTeamSizes();

    assertEquals(1, teamManager.getTeamMembers("Beta").size());
    assertNull(teamManager.getTeamDeadline("Beta"));

    Component forcedMessage = leader.nextComponentMessage();
    assertNotNull(forcedMessage);
    PlainTextComponentSerializer serializer = PlainTextComponentSerializer.plainText();
    assertEquals(
        "Из вашей команды удалено 2 участника(ов) из-за превышения лимита.",
        serializer.serialize(forcedMessage));
    assertNull(leader.nextComponentMessage());
  }
}

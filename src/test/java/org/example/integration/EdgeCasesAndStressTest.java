package org.example.integration;

import static org.junit.jupiter.api.Assertions.*;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.example.MyPurpurPlugin;
import org.example.config.JoinMode;
import org.example.config.PluginConfig;
import org.example.listener.LocalChatListener;
import org.example.model.PendingInvite;
import org.example.service.DeadlineScheduler;
import org.example.service.MembershipService;
import org.example.service.TeamManager;
import org.example.service.TeamStorage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Suite of tests specifically targeting edge cases, player mistakes, multi-world interactions,
 * and concurrent/lifecycle anomalies.
 */
class EdgeCasesAndStressTest {

  private static ServerMock server;
  private MyPurpurPlugin plugin;
  private TeamManager teamManager;
  private MembershipService membershipService;
  private PluginConfig pluginConfig;
  private FileConfiguration config;
  private TeamStorage storage;
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

    Field msField = TeamManager.class.getDeclaredField("membership");
    msField.setAccessible(true);
    membershipService = (MembershipService) msField.get(teamManager);

    Field tsField = TeamManager.class.getDeclaredField("storage");
    tsField.setAccessible(true);
    storage = (TeamStorage) tsField.get(teamManager);

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
      if (server != null && server.getPluginManager().isPluginEnabled(plugin)) {
        server.getPluginManager().disablePlugin(plugin);
        server.getPluginManager().clearPlugins();
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
  }

  @AfterAll
  static void shutdownServer() {
    MockBukkit.unmock();
  }

  @Test
  void localChatDoesNotCrashWhenPlayersAreInDifferentWorlds() {
    LocalChatListener listener = new LocalChatListener(pluginConfig);

    World overworld = server.addSimpleWorld("overworld");
    World nether = server.addSimpleWorld("nether");

    PlayerMock sender = server.addPlayer("Sender");
    sender.teleport(new Location(overworld, 100, 64, 100));

    PlayerMock netherViewer = server.addPlayer("NetherViewer");
    netherViewer.teleport(new Location(nether, 100, 64, 100));

    Component message = Component.text("Hello world");
    AsyncChatEvent event =
        new AsyncChatEvent(
            false,
            sender,
            Set.<Audience>of(netherViewer),
            ChatRenderer.defaultRenderer(),
            message,
            message,
            SignedMessage.system("Hello world", message));

    assertDoesNotThrow(
        () -> listener.onPlayerChat(event),
        "Chat event in multi-world scenario should not throw an exception");

    assertDoesNotThrow(
        () -> event.renderer().render(sender, Component.text("Sender"), message, netherViewer),
        "Renderer should safely handle rendering across different worlds");
  }

  @Test
  void acceptingInviteToTeamAClearsInvitesAndRequestsToOtherTeams() {
    config.set(PluginConfig.Keys.Team.Membership.JOIN_MODE, JoinMode.INVITE_ONLY.name());

    PlayerMock leaderA = server.addPlayer("LeaderA");
    PlayerMock leaderB = server.addPlayer("LeaderB");
    PlayerMock recruit = server.addPlayer("Recruit");

    teamManager.createTeam("TeamA", "TA", "RED", leaderA);
    teamManager.createTeam("TeamB", "TB", "BLUE", leaderB);

    membershipService.sendInvite(leaderA, recruit, Duration.ofMinutes(5));
    membershipService.sendInvite(leaderB, recruit, Duration.ofMinutes(5));

    List<PendingInvite> invitesBefore = membershipService.getInvitesForPlayer(recruit.getUniqueId());
    assertEquals(2, invitesBefore.size(), "Recruit should have 2 pending invites");

    membershipService.acceptInvite(recruit, "TeamA");

    assertEquals("TeamA", teamManager.getPlayerTeam(recruit));
    assertTrue(
        membershipService.getInvitesForPlayer(recruit.getUniqueId()).isEmpty(),
        "All pending invites should be cleared once recruit joins a team");
  }

  @Test
  void disbandingTeamWhileInvitePendingFailsAcceptGracefully() {
    config.set(PluginConfig.Keys.Team.Membership.JOIN_MODE, JoinMode.INVITE_ONLY.name());

    PlayerMock leader = server.addPlayer("DisbandLeader");
    PlayerMock recruit = server.addPlayer("DisbandRecruit");

    teamManager.createTeam("DisbandTeam", "DT", "GREEN", leader);
    membershipService.sendInvite(leader, recruit, Duration.ofMinutes(5));

    teamManager.disbandTeam("DisbandTeam", leader);

    assertDoesNotThrow(
        () -> membershipService.acceptInvite(recruit, "DisbandTeam"),
        "Accepting invite for disbanded team should not crash");
    assertNull(teamManager.getPlayerTeam(recruit), "Recruit should not belong to any team");
  }

  @Test
  void leaderCannotTransferLeadershipToThemselves() {
    PlayerMock leader = server.addPlayer("SelfLeader");
    teamManager.createTeam("SelfTeam", "ST", "YELLOW", leader);

    membershipService.transferLeadership("SelfTeam", leader, leader);

    assertEquals(
        leader.getUniqueId(),
        teamManager.getTeamLeaderId("SelfTeam"),
        "Leader should remain unchanged when trying to transfer to self");
  }

  @Test
  void whitespaceTrimmingInTeamNamesWorksConsistently() {
    PlayerMock leader = server.addPlayer("TrimLeader");
    teamManager.createTeam("  PaddedTeam  ", "PT", "WHITE", leader);

    assertEquals("PaddedTeam", teamManager.getPlayerTeam(leader));
    assertNotNull(storage.getTeamByName("paddedteam"));
    assertNotNull(storage.getTeamByName("  PADDEDTEAM  "));
  }
}

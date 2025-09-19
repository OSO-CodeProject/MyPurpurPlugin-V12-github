package org.example.service;

import static org.junit.jupiter.api.Assertions.*;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.example.MyPurpurPlugin;
import org.example.config.PluginConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Unit tests for {@link TeamManager}. */
class TeamManagerTest {

  private static ServerMock server;
  private static TeamManager teamManager;

  @BeforeAll
  static void setUp() {
    server = MockBukkit.mock();
    MyPurpurPlugin plugin = MockBukkit.load(MyPurpurPlugin.class);
    try {
      Field field = MyPurpurPlugin.class.getDeclaredField("teamManager");
      field.setAccessible(true);
      teamManager = (TeamManager) field.get(plugin);

      // Ensure max team members is limited for tests
      Field cfgField = MyPurpurPlugin.class.getDeclaredField("pluginConfig");
      cfgField.setAccessible(true);
      PluginConfig pluginConfig = (PluginConfig) cfgField.get(plugin);
      Field internal = PluginConfig.class.getDeclaredField("config");
      internal.setAccessible(true);
      org.bukkit.configuration.file.FileConfiguration config =
          (org.bukkit.configuration.file.FileConfiguration) internal.get(pluginConfig);
      config.set("team.max-members", 5);
    } catch (ReflectiveOperationException e) {
      fail(e);
    }
  }

  @AfterAll
  static void tearDown() {
    if (teamManager != null) {
      teamManager.shutdown();
    }
    MockBukkit.unmock();
  }

  @ParameterizedTest
  @ValueSource(strings = {"Alice", "Bob", "Charlie"})
  void playersJoinAndLeaveTeam(String playerName) {
    PlayerMock leader = server.addPlayer("Leader" + playerName);
    String teamName = "Team" + playerName;
    teamManager.createTeam(teamName, "PX", "white", leader);
    PlayerMock player = server.addPlayer(playerName);
    teamManager.addPlayerToTeam(teamName, player);
    assertEquals(teamName, teamManager.getPlayerTeam(player));
    teamManager.removePlayerFromTeam(teamName, player);
    assertNull(teamManager.getPlayerTeam(player));
  }

  @Test
  void createsAndManagesTeamMembership() {
    PlayerMock leader = server.addPlayer("Leader1");
    PlayerMock member = server.addPlayer("Member1");

    teamManager.createTeam("Alpha", "AA", "white", leader);
    assertEquals("Alpha", teamManager.getPlayerTeam(leader));
    assertTrue(teamManager.getTeamMembers("Alpha").contains(leader.getName()));

    teamManager.addPlayerToTeam("Alpha", member);
    assertEquals("Alpha", teamManager.getPlayerTeam(member));

    teamManager.removePlayerFromTeam("Alpha", member);
    assertNull(teamManager.getPlayerTeam(member));
  }

  @Test
  void stressTestAddRemovePlayers() {
    PlayerMock leader = server.addPlayer("StressLeader");
    teamManager.createTeam("Stress", "ST", "white", leader);
    List<PlayerMock> players = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      players.add(server.addPlayer("Stress" + i));
    }
    Runtime runtime = Runtime.getRuntime();
    long before = runtime.totalMemory() - runtime.freeMemory();
    assertDoesNotThrow(
        () -> {
          for (int i = 0; i < 100; i++) {
            for (PlayerMock p : players) {
              teamManager.addPlayerToTeam("Stress", p);
            }
            for (PlayerMock p : players) {
              teamManager.removePlayerFromTeam("Stress", p);
            }
          }
        });
    System.gc();
    long after = runtime.totalMemory() - runtime.freeMemory();
    if (after - before > 10_000_000) {
      teamManager.getPlugin().getLogger().warning("Potential memory leak: " + (after - before));
    }
    assertEquals(1, teamManager.getTeamMembers("Stress").size());
  }

  @Test
  void createsTeamAndAllowsJoin() {
    PlayerMock leader = server.addPlayer("Captain");
    PlayerMock member = server.addPlayer("Recruit");

    String teamName = "Voyagers";
    teamManager.createTeam(teamName, "VG", "white", leader);

    assertEquals(teamName, teamManager.getPlayerTeam(leader));
    assertTrue(teamManager.getTeamMembers(teamName).contains(leader.getName()));

    teamManager.addPlayerToTeam(teamName, member);
    assertEquals(teamName, teamManager.getPlayerTeam(member));
    assertTrue(teamManager.getTeamMembers(teamName).contains(member.getName()));
  }

  @Test
  void enforcesMaximumMembers() {
    PlayerMock leader = server.addPlayer("Leader2");
    teamManager.createTeam("Beta", "BB", "white", leader);

    // Default max-members is 5, so only four additional players are allowed
    for (int i = 0; i < 4; i++) {
      PlayerMock p = server.addPlayer("P" + i);
      teamManager.addPlayerToTeam("Beta", p);
    }

    PlayerMock extra = server.addPlayer("Extra");
    teamManager.addPlayerToTeam("Beta", extra);

    assertNull(teamManager.getPlayerTeam(extra));
    assertEquals(5, teamManager.getTeamMembers("Beta").size());
  }

  @Test
  void playerListNameUpdatedOnJoinAndLeave() {
    PlayerMock leader = server.addPlayer("PrefixLeader");
    PlayerMock member = server.addPlayer("PrefixMember");

    teamManager.createTeam("PrefixTeam", "PF", "gold", leader);
    teamManager.addPlayerToTeam("PrefixTeam", member);

    Component expected =
        Component.text("[PF] ", NamedTextColor.GOLD)
            .append(Component.text(member.getName(), NamedTextColor.WHITE));
    assertEquals(expected, member.playerListName());

    teamManager.removePlayerFromTeam("PrefixTeam", member);
    Component reset = Component.text(member.getName(), NamedTextColor.WHITE);
    assertEquals(reset, member.playerListName());
  }

  @Test
  void playerListNameResetOnKick() {
    PlayerMock leader = server.addPlayer("KickLeader");
    PlayerMock member = server.addPlayer("KickTarget");

    teamManager.createTeam("KickTeam", "KT", "green", leader);
    teamManager.addPlayerToTeam("KickTeam", member);

    Component expected =
        Component.text("[KT] ", NamedTextColor.GREEN)
            .append(Component.text(member.getName(), NamedTextColor.WHITE));
    assertEquals(expected, member.playerListName());

    teamManager.kickPlayerFromTeam("KickTeam", leader, member.getName());
    Component reset = Component.text(member.getName(), NamedTextColor.WHITE);
    assertEquals(reset, member.playerListName());
  }

  @Test
  void leaderKickSelfDelegatesToRemovalFlow() {
    PlayerMock leader = server.addPlayer("SelfKickLeader");
    PlayerMock successor = server.addPlayer("SelfKickMember");

    String teamName = "SelfKickTeam";
    teamManager.createTeam(teamName, "SK", "blue", leader);
    teamManager.addPlayerToTeam(teamName, successor);

    teamManager.kickPlayerFromTeam(teamName, leader, leader.getName());

    assertNull(teamManager.getPlayerTeam(leader));
    assertEquals(teamName, teamManager.getPlayerTeam(successor));
    assertEquals(successor.getName(), teamManager.getTeamLeader(teamName));
    assertFalse(teamManager.getTeamMembers(teamName).contains(leader.getName()));
  }

  @Test
  void playerListNameUpdatedOnPrefixAndColorChange() {
    PlayerMock leader = server.addPlayer("StyleLeader");
    PlayerMock member = server.addPlayer("StyleMember");

    teamManager.createTeam("Stylists", "ST", "white", leader);
    teamManager.addPlayerToTeam("Stylists", member);

    teamManager.setTeamPrefix("Stylists", "NW", leader);
    Component newPrefix =
        Component.text("[NW] ", NamedTextColor.WHITE)
            .append(Component.text(member.getName(), NamedTextColor.WHITE));
    assertEquals(newPrefix, member.playerListName());

    Component leaderPrefix =
        Component.text("[NW] ", NamedTextColor.WHITE)
            .append(Component.text(leader.getName(), NamedTextColor.WHITE));
    assertEquals(leaderPrefix, leader.playerListName());

    teamManager.setTeamColor("Stylists", "red", leader);
    Component recolored =
        Component.text("[NW] ", NamedTextColor.RED)
            .append(Component.text(member.getName(), NamedTextColor.WHITE));
    assertEquals(recolored, member.playerListName());

    Component leaderRecolored =
        Component.text("[NW] ", NamedTextColor.RED)
            .append(Component.text(leader.getName(), NamedTextColor.WHITE));
    assertEquals(leaderRecolored, leader.playerListName());
  }
}

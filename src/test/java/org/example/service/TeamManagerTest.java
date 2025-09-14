package org.example.service;

import static org.junit.jupiter.api.Assertions.*;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import java.lang.reflect.Field;
import org.example.MyPurpurPlugin;
import org.example.config.PluginConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
    MockBukkit.unmock();
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
}

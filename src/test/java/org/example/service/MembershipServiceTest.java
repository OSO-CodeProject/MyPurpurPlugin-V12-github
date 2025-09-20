package org.example.service;

import static org.junit.jupiter.api.Assertions.*;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Scoreboard;
import org.example.config.PluginConfig;
import org.example.model.Team;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MembershipServiceTest {

  private ServerMock server;
  private JavaPlugin plugin;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    plugin = MockBukkit.createMockPlugin();
  }

  @AfterEach
  void tearDown() {
    MockBukkit.unmock();
  }

  @Test
  void transferLeadershipRetargetsDeadlineWarning() throws IOException {
    prepareConfig();
    PluginConfig config = new PluginConfig(plugin);
    TeamStorage storage = new TeamStorage(plugin, config);
    DeadlineScheduler scheduler = new DeadlineScheduler(plugin, config, storage);
    MembershipService membershipService = new MembershipService(plugin, config, storage, scheduler);

    PlayerMock captain = server.addPlayer("Captain");
    PlayerMock successor = server.addPlayer("Successor");
    PlayerMock reserve = server.addPlayer("Reserve");

    Team team =
        new Team(UUID.randomUUID(), "Alpha", captain.getUniqueId(), "AA", "WHITE");
    team.setMembers(List.of(captain.getUniqueId(), successor.getUniqueId(), reserve.getUniqueId()));
    storage.getTeams().put(team.getId(), team);
    storage.getPlayerTeams().put(captain.getUniqueId(), team.getId());
    storage.getPlayerTeams().put(successor.getUniqueId(), team.getId());
    storage.getPlayerTeams().put(reserve.getUniqueId(), team.getId());
    Scoreboard captainOriginal = captain.getScoreboard();
    Scoreboard successorOriginal = successor.getScoreboard();

    scheduler.enforceTeamSizes(false);

    assertNotNull(captain.getScoreboard().getObjective(DisplaySlot.SIDEBAR));
    assertSame(successorOriginal, successor.getScoreboard());
    assertNull(successor.getScoreboard().getObjective(DisplaySlot.SIDEBAR));

    membershipService.transferLeadership("Alpha", captain, successor);

    assertSame(captainOriginal, captain.getScoreboard());
    assertNull(captain.getScoreboard().getObjective(DisplaySlot.SIDEBAR));

    assertNotSame(successorOriginal, successor.getScoreboard());
    assertNotNull(successor.getScoreboard().getObjective(DisplaySlot.SIDEBAR));
  }

  @Test
  void removingLeaderRetargetsDeadlineWarning() throws IOException {
    prepareConfig();
    PluginConfig config = new PluginConfig(plugin);
    TeamStorage storage = new TeamStorage(plugin, config);
    DeadlineScheduler scheduler = new DeadlineScheduler(plugin, config, storage);
    MembershipService membershipService = new MembershipService(plugin, config, storage, scheduler);

    PlayerMock captain = server.addPlayer("Captain");
    PlayerMock successor = server.addPlayer("Successor");
    PlayerMock reserve = server.addPlayer("Reserve");

    Team team =
        new Team(UUID.randomUUID(), "Alpha", captain.getUniqueId(), "AA", "WHITE");
    team.setMembers(List.of(captain.getUniqueId(), successor.getUniqueId(), reserve.getUniqueId()));
    storage.getTeams().put(team.getId(), team);
    storage.getPlayerTeams().put(captain.getUniqueId(), team.getId());
    storage.getPlayerTeams().put(successor.getUniqueId(), team.getId());
    storage.getPlayerTeams().put(reserve.getUniqueId(), team.getId());
    Scoreboard captainOriginal = captain.getScoreboard();
    Scoreboard successorOriginal = successor.getScoreboard();

    scheduler.enforceTeamSizes(false);

    assertNotNull(captain.getScoreboard().getObjective(DisplaySlot.SIDEBAR));
    assertNull(successor.getScoreboard().getObjective(DisplaySlot.SIDEBAR));

    membershipService.removePlayerFromTeam("Alpha", captain);

    assertSame(captainOriginal, captain.getScoreboard());
    assertNull(captain.getScoreboard().getObjective(DisplaySlot.SIDEBAR));

    assertEquals(successor.getUniqueId(), team.getLeaderId());
    assertNotSame(successorOriginal, successor.getScoreboard());
    assertNotNull(successor.getScoreboard().getObjective(DisplaySlot.SIDEBAR));
  }

  @Test
  void disbandingTeamClearsDeadlineWarningImmediately() throws IOException {
    prepareConfig();
    PluginConfig config = new PluginConfig(plugin);
    TeamStorage storage = new TeamStorage(plugin, config);
    DeadlineScheduler scheduler = new DeadlineScheduler(plugin, config, storage);
    MembershipService membershipService = new MembershipService(plugin, config, storage, scheduler);

    PlayerMock captain = server.addPlayer("Captain");
    PlayerMock memberOne = server.addPlayer("MemberOne");
    PlayerMock memberTwo = server.addPlayer("MemberTwo");

    Team team =
        new Team(UUID.randomUUID(), "Alpha", captain.getUniqueId(), "AA", "WHITE");
    team.setMembers(List.of(captain.getUniqueId(), memberOne.getUniqueId(), memberTwo.getUniqueId()));
    storage.getTeams().put(team.getId(), team);
    storage.getPlayerTeams().put(captain.getUniqueId(), team.getId());
    storage.getPlayerTeams().put(memberOne.getUniqueId(), team.getId());
    storage.getPlayerTeams().put(memberTwo.getUniqueId(), team.getId());
    Scoreboard captainOriginal = captain.getScoreboard();

    scheduler.enforceTeamSizes(false);

    assertNotNull(captain.getScoreboard().getObjective(DisplaySlot.SIDEBAR));
    assertNotSame(captainOriginal, captain.getScoreboard());

    membershipService.disbandTeam("Alpha", captain);

    assertTrue(scheduler.getDeadlines().isEmpty());
    assertSame(captainOriginal, captain.getScoreboard());
    assertNull(captain.getScoreboard().getObjective(DisplaySlot.SIDEBAR));
  }

  private void prepareConfig() throws IOException {
    File dataFolder = plugin.getDataFolder();
    if (!dataFolder.exists() && !dataFolder.mkdirs()) {
      fail("Failed to create plugin data folder");
    }
    File configFile = new File(dataFolder, "config.yml");
    String contents =
        "team:\n"
            + "  max-members: 2\n"
            + "  enforce-max-members-on-reload: true\n"
            + "  grace-period-enabled: true\n"
            + "  grace-period-minutes: 5\n"
            + "  deadline-display-mode: SCOREBOARD\n";
    Files.writeString(configFile.toPath(), contents, StandardCharsets.UTF_8);
  }
}

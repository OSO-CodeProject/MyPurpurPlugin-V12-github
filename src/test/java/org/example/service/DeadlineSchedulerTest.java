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

class DeadlineSchedulerTest {

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
  void clearsScoreboardWhenTeamTrimmedAfterReloadWithEnforcementDisabled() throws IOException {
    prepareConfig();
    PluginConfig config = new PluginConfig(plugin);
    TeamStorage storage = new TeamStorage(plugin, config);
    DeadlineScheduler scheduler = new DeadlineScheduler(plugin, config, storage);

    PlayerMock leader = server.addPlayer("Leader");
    PlayerMock memberOne = server.addPlayer("MemberOne");
    PlayerMock memberTwo = server.addPlayer("MemberTwo");

    Team team =
        new Team(UUID.randomUUID(), "Alpha", leader.getUniqueId(), "", "WHITE");
    team.setMembers(
        List.of(leader.getUniqueId(), memberOne.getUniqueId(), memberTwo.getUniqueId()));
    storage.getTeams().put(team.getId(), team);
    Scoreboard original = leader.getScoreboard();

    scheduler.enforceTeamSizes(false);
    assertNotNull(leader.getScoreboard().getObjective(DisplaySlot.SIDEBAR));

    scheduler.getDeadlines().clear();
    assertNotNull(leader.getScoreboard().getObjective(DisplaySlot.SIDEBAR));

    team.setMembers(List.of(leader.getUniqueId(), memberOne.getUniqueId()));
    scheduler.enforceTeamSizes(true);

    assertNull(leader.getScoreboard().getObjective(DisplaySlot.SIDEBAR));
    assertSame(original, leader.getScoreboard());
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
            + "  enforce-max-members-on-reload: false\n"
            + "  grace-period-enabled: true\n"
            + "  grace-period-minutes: 5\n"
            + "  deadline-display-mode: SCOREBOARD\n";
    Files.writeString(configFile.toPath(), contents, StandardCharsets.UTF_8);
  }
}

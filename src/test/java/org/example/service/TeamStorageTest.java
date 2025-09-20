package org.example.service;

import static org.junit.jupiter.api.Assertions.*;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.example.model.Team;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TeamStorageTest {

  private ServerMock server;
  private JavaPlugin plugin;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    plugin = MockBukkit.createMockPlugin();
    plugin.getDataFolder().mkdirs();
  }

  @AfterEach
  void tearDown() {
    MockBukkit.unmock();
  }

  @Test
  void saveAndLoadPreservesTeamColor() throws IOException {
    TeamStorage storage = new TeamStorage(plugin, null);
    Map<UUID, Long> deadlines = new HashMap<>();
    storage.loadTeams(deadlines);

    Team team = new Team(UUID.randomUUID(), "TestTeam", "Leader", "[T]", "red");
    storage.addTeam(team);
    storage.saveTeams(deadlines);

    File teamsFile = new File(plugin.getDataFolder(), "teams.yml");
    assertTrue(teamsFile.exists(), "teams.yml should have been created");
    YamlConfiguration config = YamlConfiguration.loadConfiguration(teamsFile);
    String savedColor = config.getString("teams." + team.getId() + ".color");
    assertEquals("red", savedColor, "Color should be saved as a stable key");

    config.set("teams." + team.getId() + ".color", NamedTextColor.RED.toString());
    config.save(teamsFile);

    TeamStorage reloaded = new TeamStorage(plugin, null);
    Map<UUID, Long> reloadedDeadlines = new HashMap<>();
    reloaded.loadTeams(reloadedDeadlines);

    Team loadedTeam = reloaded.getTeams().get(team.getId());
    assertNotNull(loadedTeam, "Team should be present after reload");
    assertEquals(NamedTextColor.RED, loadedTeam.getColor());
  }

  @Test
  void saveAndReloadKeepsDeadlineTimestamps() throws IOException {
    TeamStorage storage = new TeamStorage(plugin, null);
    Map<UUID, Long> deadlines = new HashMap<>();
    storage.loadTeams(deadlines);

    UUID teamId = UUID.randomUUID();
    Team team = new Team(teamId, "DeadlineTeam", "Leader", "[D]", "blue");
    team.setMembers(new ArrayList<>(List.of("Leader", "Member")));
    storage.addTeam(team);

    long deadline = System.currentTimeMillis() + 60000L;
    deadlines.put(teamId, deadline);
    storage.saveTeams(deadlines);

    TeamStorage reloaded = new TeamStorage(plugin, null);
    Map<UUID, Long> reloadedDeadlines = new HashMap<>();
    reloaded.loadTeams(reloadedDeadlines);

    assertEquals(deadline, reloadedDeadlines.get(teamId));
  }

  @Test
  void loadTeamsSkipsEntriesWithInvalidUuid() throws IOException {
    TeamStorage storage = new TeamStorage(plugin, null);
    Map<UUID, Long> deadlines = new HashMap<>();
    storage.loadTeams(deadlines);

    File teamsFile = new File(plugin.getDataFolder(), "teams.yml");
    YamlConfiguration config = YamlConfiguration.loadConfiguration(teamsFile);

    config.set("teams.not-a-uuid.name", "BrokenTeam");
    config.set("teams.not-a-uuid.leader", "Nobody");

    UUID validId = UUID.randomUUID();
    config.set("teams." + validId + ".name", "ValidTeam");
    config.set("teams." + validId + ".leader", "Leader");
    config.save(teamsFile);

    TeamStorage reloaded = new TeamStorage(plugin, null);
    Map<UUID, Long> reloadedDeadlines = new HashMap<>();
    reloaded.loadTeams(reloadedDeadlines);

    assertEquals(1, reloaded.getTeams().size(), "Only the valid team should be loaded");
    Team loadedTeam = reloaded.getTeams().get(validId);
    assertNotNull(loadedTeam, "Valid team should be present after reload");
    assertEquals("ValidTeam", loadedTeam.getName());
    assertTrue(reloadedDeadlines.isEmpty(), "Invalid entries should not add deadlines");
  }
}

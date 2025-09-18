package org.example.service;

import static org.junit.jupiter.api.Assertions.*;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
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
}

package org.example.service;

import static org.junit.jupiter.api.Assertions.*;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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

    PlayerMock leader = server.addPlayer("Leader");
    Team team = new Team(UUID.randomUUID(), "TestTeam", leader.getUniqueId(), "[T]", "red");
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
    PlayerMock leader = server.addPlayer("Leader");
    PlayerMock member = server.addPlayer("Member");
    Team team = new Team(teamId, "DeadlineTeam", leader.getUniqueId(), "[D]", "blue");
    team.setMembers(new ArrayList<>(List.of(leader.getUniqueId(), member.getUniqueId())));
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
    PlayerMock leader = server.addPlayer("ValidLeader");
    config.set("teams." + validId + ".leader", leader.getUniqueId().toString());
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

  @Test
  void autoSaveContinuesAsynchronouslyAndFlushesOnShutdown() throws IOException {
    TeamStorage storage = new TeamStorage(plugin, null);
    Map<UUID, Long> deadlines = new HashMap<>();
    storage.loadTeams(deadlines);

    PlayerMock leader = server.addPlayer("AsyncLeader");
    Team team = new Team(UUID.randomUUID(), "AsyncTeam", leader.getUniqueId(), "[A]", "green");
    storage.addTeam(team);

    storage.startAutoSave(1L, deadlines);
    server.getScheduler().performTicks(40L);
    server.getScheduler().waitAsyncTasksFinished();

    File teamsFile = new File(plugin.getDataFolder(), "teams.yml");
    assertTrue(teamsFile.exists(), "Auto-save should create the teams.yml file");
    YamlConfiguration config = YamlConfiguration.loadConfiguration(teamsFile);
    assertEquals("AsyncTeam", config.getString("teams." + team.getId() + ".name"));

    storage.removeTeam(team);
    storage.stopAutoSave();
    server.getScheduler().waitAsyncTasksFinished();
    storage.flushNow();

    YamlConfiguration afterShutdown = YamlConfiguration.loadConfiguration(teamsFile);
    assertNull(afterShutdown.getString("teams." + team.getId() + ".name"));
  }

  @Test
  void loadTeamsSkipsUncachedPlayerNames() throws IOException {
    TeamStorage storage = new TeamStorage(plugin, null);
    Map<UUID, Long> deadlines = new HashMap<>();
    storage.loadTeams(deadlines);

    File teamsFile = new File(plugin.getDataFolder(), "teams.yml");
    YamlConfiguration config = YamlConfiguration.loadConfiguration(teamsFile);

    UUID teamId = UUID.randomUUID();
    PlayerMock leader = server.addPlayer("KnownLeader");
    config.set("teams." + teamId + ".name", "LegacyTeam");
    config.set("teams." + teamId + ".leader", leader.getUniqueId().toString());
    config.set("teams." + teamId + ".members", List.of("UncachedMember"));
    config.save(teamsFile);

    TeamStorage reloaded = new TeamStorage(plugin, null);
    Map<UUID, Long> reloadedDeadlines = new HashMap<>();
    reloaded.loadTeams(reloadedDeadlines);

    Team loadedTeam = reloaded.getTeams().get(teamId);
    assertNotNull(loadedTeam, "Team should load even with unresolved member names");
    assertEquals(
        List.of(leader.getUniqueId()),
        loadedTeam.getMembers(),
        "Only cached members should remain after loading");

    long uncachedEntries =
        Arrays.stream(server.getOfflinePlayers())
            .filter(player -> "UncachedMember".equalsIgnoreCase(player.getName()))
            .count();
    assertEquals(0L, uncachedEntries, "No offline profile should be created for uncached names");
  }
}

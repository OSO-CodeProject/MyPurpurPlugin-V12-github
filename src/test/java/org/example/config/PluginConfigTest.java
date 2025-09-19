package org.example.config;

import static org.junit.jupiter.api.Assertions.*;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import be.seeseemelk.mockbukkit.plugin.PluginManagerMock;
import java.io.File;
import java.lang.reflect.Field;
import org.bukkit.command.CommandMap;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.example.MyPurpurPlugin;
import org.example.service.TeamManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Tests covering PluginConfig default regeneration logic. */
class PluginConfigTest {

  private ServerMock server;

  @AfterEach
  void tearDown() {
    if (MockBukkit.getMock() != null) {
      MockBukkit.unmock();
    }
    server = null;
  }

  @Test
  void regeneratesConfigWithDefaultsAfterDeletion() throws Exception {
    server = MockBukkit.mock();
    MyPurpurPlugin plugin = MockBukkit.load(MyPurpurPlugin.class);

    File dataFolder = plugin.getDataFolder();
    File configFile = new File(dataFolder, "config.yml");
    assertTrue(configFile.exists(), "Config file should be created on first load");
    assertTrue(configFile.delete(), "Config file should be deletable");

    PluginManagerMock pluginManager = server.getPluginManager();
    pluginManager.disablePlugin(plugin);
    pluginManager.clearPlugins();

    MockBukkit.unmock();

    server = MockBukkit.mock();
    plugin = MockBukkit.load(MyPurpurPlugin.class);
    CommandMap commandMap = server.getCommandMap();

    File regenerated = new File(plugin.getDataFolder(), "config.yml");
    assertTrue(regenerated.exists(), "Config file should be regenerated on reload");

    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(regenerated);
    assertEquals(1, yaml.getInt("team.min-prefix-length"));
    assertEquals(16, yaml.getInt("team.max-prefix-length"));
    assertEquals(32, yaml.getInt("team.max-team-name-length"));
    assertEquals(0, yaml.getInt("team.max-members"));

    PlayerMock leader = server.addPlayer("Leader");
    leader.addAttachment(plugin, "mypurpurplugin.team", true);

    assertTrue(commandMap.dispatch(leader, "team create Zeta Z WHITE"));

    Field teamManagerField = MyPurpurPlugin.class.getDeclaredField("teamManager");
    teamManagerField.setAccessible(true);
    TeamManager teamManager = (TeamManager) teamManagerField.get(plugin);

    assertEquals("Zeta", teamManager.getPlayerTeam(leader));
  }

  @Test
  void returnsDefaultMaxTeamNameLengthWhenMissing() throws Exception {
    server = MockBukkit.mock();
    MyPurpurPlugin plugin = MockBukkit.load(MyPurpurPlugin.class);

    Field cfgField = MyPurpurPlugin.class.getDeclaredField("pluginConfig");
    cfgField.setAccessible(true);
    PluginConfig pluginConfig = (PluginConfig) cfgField.get(plugin);

    Field configField = PluginConfig.class.getDeclaredField("config");
    configField.setAccessible(true);
    FileConfiguration configuration = (FileConfiguration) configField.get(pluginConfig);

    Field fileField = PluginConfig.class.getDeclaredField("configFile");
    fileField.setAccessible(true);
    File configFile = (File) fileField.get(pluginConfig);

    configuration.set("team.max-team-name-length", null);
    configuration.save(configFile);

    pluginConfig.reloadConfig();

    assertEquals(32, pluginConfig.getMaxTeamNameLength());
  }
}

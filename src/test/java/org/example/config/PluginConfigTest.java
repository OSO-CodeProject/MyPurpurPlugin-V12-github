package org.example.config;

import static org.junit.jupiter.api.Assertions.*;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import be.seeseemelk.mockbukkit.plugin.PluginManagerMock;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import org.bukkit.command.CommandMap;
import org.bukkit.command.ConsoleCommandSender;
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
    assertEquals(1, yaml.getInt(PluginConfig.Keys.Team.Naming.Prefix.MIN_LENGTH));
    assertEquals(16, yaml.getInt(PluginConfig.Keys.Team.Naming.Prefix.MAX_LENGTH));
    assertEquals(32, yaml.getInt(PluginConfig.Keys.Team.Naming.TeamName.MAX_LENGTH));
    assertEquals(0, yaml.getInt(PluginConfig.Keys.Team.Membership.MAX_MEMBERS));

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

    configuration.set(PluginConfig.Keys.Team.Naming.TeamName.MAX_LENGTH, null);
    configuration.save(configFile);

    pluginConfig.reloadConfig();

    assertEquals(32, pluginConfig.getMaxTeamNameLength());
  }

  @Test
  void reloadConfigRestoresDeletedKeyWithDefault() throws Exception {
    server = MockBukkit.mock();
    MyPurpurPlugin plugin = MockBukkit.load(MyPurpurPlugin.class);

    Field cfgField = MyPurpurPlugin.class.getDeclaredField("pluginConfig");
    cfgField.setAccessible(true);
    PluginConfig pluginConfig = (PluginConfig) cfgField.get(plugin);

    Field fileField = PluginConfig.class.getDeclaredField("configFile");
    fileField.setAccessible(true);
    File configFile = (File) fileField.get(pluginConfig);

    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);
    yaml.set(PluginConfig.Keys.Menu.Sound.OPEN, null);
    yaml.save(configFile);

    pluginConfig.reloadConfig();

    Field configField = PluginConfig.class.getDeclaredField("config");
    configField.setAccessible(true);
    FileConfiguration configuration = (FileConfiguration) configField.get(pluginConfig);

    assertEquals(
        "BLOCK_NOTE_BLOCK_PLING",
        configuration.getString(PluginConfig.Keys.Menu.Sound.OPEN));
  }

  @Test
  void teamReloadCommandRepopulatesDefaultsAndKeepsOverrides() throws Exception {
    server = MockBukkit.mock();
    MyPurpurPlugin plugin = MockBukkit.load(MyPurpurPlugin.class);

    Field cfgField = MyPurpurPlugin.class.getDeclaredField("pluginConfig");
    cfgField.setAccessible(true);
    PluginConfig pluginConfig = (PluginConfig) cfgField.get(plugin);

    Field fileField = PluginConfig.class.getDeclaredField("configFile");
    fileField.setAccessible(true);
    File configFile = (File) fileField.get(pluginConfig);

    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);
    yaml.set(PluginConfig.Keys.Menu.PARTICLE_EFFECT, null);
    yaml.set(PluginConfig.Keys.Team.Membership.MAX_MEMBERS, 7);
    yaml.save(configFile);

    ConsoleCommandSender console = server.getConsoleSender();
    CommandMap commandMap = server.getCommandMap();
    assertTrue(commandMap.dispatch(console, "teamreload"));

    Field configField = PluginConfig.class.getDeclaredField("config");
    configField.setAccessible(true);
    FileConfiguration configuration = (FileConfiguration) configField.get(pluginConfig);

    assertEquals(
        "FIREWORK",
        configuration.getString(PluginConfig.Keys.Menu.PARTICLE_EFFECT));
    assertEquals(7, configuration.getInt(PluginConfig.Keys.Team.Membership.MAX_MEMBERS));
  }

  @Test
  void reloadConfigKeepsFileHashAndComments() throws Exception {
    server = MockBukkit.mock();
    MyPurpurPlugin plugin = MockBukkit.load(MyPurpurPlugin.class);

    Field cfgField = MyPurpurPlugin.class.getDeclaredField("pluginConfig");
    cfgField.setAccessible(true);
    PluginConfig pluginConfig = (PluginConfig) cfgField.get(plugin);

    Field fileField = PluginConfig.class.getDeclaredField("configFile");
    fileField.setAccessible(true);
    File configFile = (File) fileField.get(pluginConfig);

    String customComment = "# admin-note: keep this comment";
    Files.writeString(
        configFile.toPath(),
        System.lineSeparator() + customComment + System.lineSeparator(),
        StandardOpenOption.APPEND);

    byte[] initialContent = Files.readAllBytes(configFile.toPath());
    byte[] initialHash = MessageDigest.getInstance("SHA-256").digest(initialContent);

    pluginConfig.reloadConfig();

    byte[] afterFirstReload = Files.readAllBytes(configFile.toPath());
    byte[] firstReloadHash = MessageDigest.getInstance("SHA-256").digest(afterFirstReload);

    assertArrayEquals(initialHash, firstReloadHash, "Hashes should match after first reload");
    assertTrue(new String(afterFirstReload).contains(customComment));

    pluginConfig.reloadConfig();

    byte[] afterSecondReload = Files.readAllBytes(configFile.toPath());
    byte[] secondReloadHash = MessageDigest.getInstance("SHA-256").digest(afterSecondReload);

    assertArrayEquals(
        initialHash, secondReloadHash, "Hashes should remain stable after repeated reloads");
    assertTrue(new String(afterSecondReload).contains(customComment));
  }
}

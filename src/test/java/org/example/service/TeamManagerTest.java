package org.example.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import be.seeseemelk.mockbukkit.entity.PlayerMock;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import org.bukkit.configuration.file.YamlConfiguration;
import org.example.MockBukkitTestBase;
import org.example.command.sub.JoinSubCommand;
import org.example.config.JoinMode;
import org.example.config.PluginConfig;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

class TeamManagerTest extends MockBukkitTestBase {

  @Test
  void delegatesMembershipOperations() {
    PluginConfig pluginConfig = mock(PluginConfig.class);
    when(pluginConfig.getSaveIntervalSeconds()).thenReturn(0L);
    when(pluginConfig.isEnforceMaxMembersOnReload()).thenReturn(false);
    when(pluginConfig.isGracePeriodEnabled()).thenReturn(false);

    try (MockedConstruction<TeamStorage> storageMock =
            mockConstruction(
                TeamStorage.class,
                (mock, context) -> {
                  when(mock.getPlayerTeam(any())).thenReturn(null);
                  when(mock.getTeamMembers(anyString())).thenReturn(java.util.List.of());
                  when(mock.getTeamNames()).thenReturn(java.util.List.of());
                  when(mock.getTeamPrefix(anyString())).thenReturn("");
                  when(mock.getTeamColor(anyString()))
                      .thenReturn(net.kyori.adventure.text.format.NamedTextColor.WHITE);
                  when(mock.getTeamLeaderId(anyString())).thenReturn(null);
                  when(mock.getTeams()).thenReturn(new HashMap<>());
                  when(mock.getPlayerTeams()).thenReturn(new HashMap<>());
                  when(mock.getTeamIdByName(anyString())).thenReturn(null);
                });
        MockedConstruction<DeadlineScheduler> schedulerMock =
            mockConstruction(
                DeadlineScheduler.class,
                (mock, context) -> when(mock.getDeadlines()).thenReturn(new HashMap<>()));
        MockedConstruction<MembershipService> membershipMock =
            mockConstruction(MembershipService.class)) {

      TeamManager manager = new TeamManager(plugin, pluginConfig);
      MembershipService membership = membershipMock.constructed().getFirst();

      PlayerMock leader = server.addPlayer("Leader");
      PlayerMock member = server.addPlayer("Member");

      manager.createTeam("Alpha", "A", "red", leader);
      verify(membership).createTeam("Alpha", "A", "red", leader);

      manager.addPlayerToTeam("Alpha", member);
      verify(membership).addPlayerToTeam("Alpha", member);

      manager.removePlayerFromTeam("Alpha", member);
      verify(membership).removePlayerFromTeam("Alpha", member);

      manager.kickPlayerFromTeam("Alpha", leader, member.getName());
      verify(membership).kickPlayerFromTeam("Alpha", leader, member.getName());

      manager.transferLeadership("Alpha", leader, member);
      verify(membership).transferLeadership("Alpha", leader, member);

      manager.disbandTeam("Alpha", leader);
      verify(membership).disbandTeam("Alpha", leader);

      manager.setTeamPrefix("Alpha", "NEW", leader);
      verify(membership).setTeamPrefix("Alpha", "NEW", leader);

      manager.setTeamColor("Alpha", "blue", leader);
      verify(membership).setTeamColor("Alpha", "blue", leader);

      manager.updatePlayerPrefixes("Alpha");
      verify(membership).updatePlayerPrefixes("Alpha");
    }
  }

  @Test
  void joinCommandAllowsImmediateJoinInOpenMode() throws IOException {
    PluginConfig config = createConfigWithJoinMode(JoinMode.OPEN);
    TeamManager manager = new TeamManager(plugin, config);
    try {
      PlayerMock leader = server.addPlayer("OpenLeader");
      manager.createTeam("OpenTeam", "OT", "white", leader);
      leader.nextMessage();

      PlayerMock applicant = server.addPlayer("OpenApplicant");
      JoinSubCommand joinCommand = new JoinSubCommand(manager);
      joinCommand.execute(applicant, new String[] {"join", "OpenTeam"});

      assertEquals("OpenTeam", manager.getPlayerTeam(applicant));
      assertEquals("✅ Вы вступили в команду", stripColors(applicant.nextMessage()));
    } finally {
      manager.shutdown();
      config.shutdown();
      resetJoinMode();
    }
  }

  @Test
  void joinCommandBlocksJoinInInviteOnlyMode() throws IOException {
    PluginConfig config = createConfigWithJoinMode(JoinMode.INVITE_ONLY);
    TeamManager manager = new TeamManager(plugin, config);
    try {
      PlayerMock leader = server.addPlayer("InviteLeader");
      manager.createTeam("InviteTeam", "IT", "white", leader);
      leader.nextMessage();

      PlayerMock applicant = server.addPlayer("InviteApplicant");
      JoinSubCommand joinCommand = new JoinSubCommand(manager);
      joinCommand.execute(applicant, new String[] {"join", "InviteTeam"});

      assertNull(manager.getPlayerTeam(applicant));
      assertEquals(
          "ℹ️ Эта команда принимает новых участников только по приглашению лидера.",
          stripColors(applicant.nextMessage()));
    } finally {
      manager.shutdown();
      config.shutdown();
      resetJoinMode();
    }
  }

  @Test
  void joinCommandCreatesPendingRequestInRequestMode() throws IOException {
    PluginConfig config = createConfigWithJoinMode(JoinMode.REQUEST_TO_JOIN);
    TeamManager manager = new TeamManager(plugin, config);
    try {
      PlayerMock leader = server.addPlayer("RequestLeader");
      manager.createTeam("RequestTeam", "RT", "white", leader);
      leader.nextMessage();

      PlayerMock applicant = server.addPlayer("RequestApplicant");
      JoinSubCommand joinCommand = new JoinSubCommand(manager);
      joinCommand.execute(applicant, new String[] {"join", "RequestTeam"});

      assertNull(manager.getPlayerTeam(applicant));
      assertTrue(manager.hasPendingJoinRequest("RequestTeam", applicant.getUniqueId()));
      assertEquals(
          "ℹ️ Заявка на вступление в команду RequestTeam отправлена лидеру.",
          stripColors(applicant.nextMessage()));
    } finally {
      manager.shutdown();
      config.shutdown();
      resetJoinMode();
    }
  }

  private PluginConfig createConfigWithJoinMode(JoinMode joinMode) throws IOException {
    File configFile = new File(plugin.getDataFolder(), "config.yml");
    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);
    yaml.set(PluginConfig.Keys.Team.Membership.JOIN_MODE, joinMode.name());
    yaml.save(configFile);
    return new PluginConfig(plugin);
  }

  private void resetJoinMode() throws IOException {
    File configFile = new File(plugin.getDataFolder(), "config.yml");
    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);
    yaml.set(PluginConfig.Keys.Team.Membership.JOIN_MODE, JoinMode.OPEN.name());
    yaml.save(configFile);
  }

  private String stripColors(String message) {
    return message.replaceAll("§[0-9A-FK-ORa-fk-or]", "");
  }
}

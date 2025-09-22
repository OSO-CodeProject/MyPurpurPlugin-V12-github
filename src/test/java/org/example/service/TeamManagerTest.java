package org.example.service;

import static org.mockito.Mockito.*;

import be.seeseemelk.mockbukkit.entity.PlayerMock;
import java.util.HashMap;
import org.example.MockBukkitTestBase;
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
      MembershipService membership = membershipMock.constructed().get(0);

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
}

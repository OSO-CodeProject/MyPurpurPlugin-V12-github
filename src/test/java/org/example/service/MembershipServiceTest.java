package org.example.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import be.seeseemelk.mockbukkit.entity.PlayerMock;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.plugin.java.JavaPlugin;
import org.example.MockBukkitTestBase;
import org.example.config.PluginConfig;
import org.example.model.Team;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MembershipServiceTest extends MockBukkitTestBase {

  private PluginConfig pluginConfig;
  private TeamStorage storage;
  private TestDeadlineScheduler scheduler;
  private MembershipService membership;

  @BeforeEach
  void setUpMembershipService() {
    pluginConfig = mock(PluginConfig.class);
    when(pluginConfig.getMinTeamNameLength()).thenReturn(3);
    when(pluginConfig.getMaxTeamNameLength()).thenReturn(32);
    when(pluginConfig.getMinPrefixLength()).thenReturn(1);
    when(pluginConfig.getMaxPrefixLength()).thenReturn(16);
    when(pluginConfig.getMaxMembers()).thenReturn(10);
    when(pluginConfig.isGracePeriodEnabled()).thenReturn(true);
    when(pluginConfig.getGracePeriodMinutes()).thenReturn(10);

    storage = new TeamStorage(plugin, pluginConfig);
    scheduler = new TestDeadlineScheduler(plugin, pluginConfig, storage);
    membership = new MembershipService(plugin, pluginConfig, storage, scheduler);
  }

  @Test
  void createTeamRegistersLeaderAndPrefix() {
    PlayerMock leader = server.addPlayer("Leader");

    membership.createTeam("Alpha", "A", "red", leader);

    Team team = storage.getTeamByName("Alpha");
    assertNotNull(team, "Команда должна создаваться");
    assertEquals(leader.getUniqueId(), team.getLeaderId(), "Лидер сохраняется");
    assertEquals("A", team.getPrefix(), "Префикс сохраняется");
    assertEquals("Alpha", storage.getPlayerTeam(leader), "Игрок связан с командой");
    assertEquals(1, scheduler.enforceCalls, "После создания вызывается проверка лимитов");
  }

  @Test
  void addPlayerToTeamRespectsLimit() {
    when(pluginConfig.getMaxMembers()).thenReturn(2);
    PlayerMock leader = server.addPlayer("Leader");
    membership.createTeam("Alpha", "A", "red", leader);

    PlayerMock firstMember = server.addPlayer("MemberOne");
    membership.addPlayerToTeam("Alpha", firstMember);

    PlayerMock secondMember = server.addPlayer("MemberTwo");
    membership.addPlayerToTeam("Alpha", secondMember);

    Team team = storage.getTeamByName("Alpha");
    assertEquals(2, team.getMembers().size(), "Размер команды ограничен лимитом");
    assertTrue(team.hasMember(firstMember.getUniqueId()), "Первый участник добавлен");
    assertFalse(
        team.hasMember(secondMember.getUniqueId()), "Второй участник не должен добавляться");
    assertFalse(
        storage.getPlayerTeams().containsKey(secondMember.getUniqueId()),
        "Игрок не должен иметь записи о команде");
    assertEquals(2, scheduler.enforceCalls, "Проверка лимитов вызывается при успешном добавлении");
  }

  @Test
  void leaderCanKickMemberByName() {
    PlayerMock leader = server.addPlayer("Leader");
    PlayerMock member = server.addPlayer("Member");
    membership.createTeam("Alpha", "A", "red", leader);
    membership.addPlayerToTeam("Alpha", member);

    membership.kickPlayerFromTeam("Alpha", leader, member.getName());

    Team team = storage.getTeamByName("Alpha");
    assertFalse(team.hasMember(member.getUniqueId()), "Игрок должен быть исключён");
    assertFalse(
        storage.getPlayerTeams().containsKey(member.getUniqueId()),
        "У игрока не должно оставаться привязки к команде");
  }

  @Test
  void leaderCanDisbandTeam() {
    PlayerMock leader = server.addPlayer("Leader");
    PlayerMock member = server.addPlayer("Member");
    membership.createTeam("Alpha", "A", "red", leader);
    membership.addPlayerToTeam("Alpha", member);

    membership.disbandTeam("Alpha", leader);

    assertNull(storage.getTeamByName("Alpha"), "Команда должна быть удалена");
    assertFalse(
        storage.getPlayerTeams().containsKey(leader.getUniqueId()),
        "Лидер должен потерять принадлежность");
    assertFalse(
        storage.getPlayerTeams().containsKey(member.getUniqueId()),
        "Участник также должен потерять принадлежность");
    assertTrue(scheduler.cancelledTeams.contains("Alpha"), "Дедлайн должен быть отменён");
  }

  @Test
  void leaderCanTransferLeadership() {
    PlayerMock leader = server.addPlayer("Leader");
    PlayerMock newLeader = server.addPlayer("NewLeader");
    membership.createTeam("Alpha", "A", "red", leader);
    membership.addPlayerToTeam("Alpha", newLeader);

    membership.transferLeadership("Alpha", leader, newLeader);

    Team team = storage.getTeamByName("Alpha");
    assertEquals(
        newLeader.getUniqueId(), team.getLeaderId(), "Лидерство должно перейти новому игроку");
    assertTrue(
        scheduler.leaderTransfers.contains("Alpha"),
        "Планировщик должен быть уведомлён о смене лидера");
  }

  private static class TestDeadlineScheduler extends DeadlineScheduler {
    int enforceCalls;
    final Set<String> cancelledTeams = new HashSet<>();
    final Set<String> leaderTransfers = new HashSet<>();

    TestDeadlineScheduler(JavaPlugin plugin, PluginConfig pluginConfig, TeamStorage storage) {
      super(plugin, pluginConfig, storage);
    }

    @Override
    public void enforceTeamSizes(boolean triggeredByReload) {
      enforceCalls++;
    }

    @Override
    public void enforceTeamSizes() {
      enforceCalls++;
    }

    @Override
    public void cancelDeadline(Team team) {
      cancelledTeams.add(team.getName());
    }

    @Override
    public void handleLeaderTransfer(Team team) {
      leaderTransfers.add(team.getName());
    }

    @Override
    public void start() {}

    @Override
    public void stop() {}
  }
}

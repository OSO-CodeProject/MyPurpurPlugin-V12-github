package org.example.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import be.seeseemelk.mockbukkit.entity.PlayerMock;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.plugin.java.JavaPlugin;
import org.example.MockBukkitTestBase;
import org.example.config.JoinMode;
import org.example.config.PluginConfig;
import org.example.model.PendingInvite;
import org.example.model.Team;
import org.example.util.TeamMessageUtils;
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
    when(pluginConfig.getJoinMode()).thenReturn(JoinMode.OPEN);
    when(pluginConfig.isGracePeriodEnabled()).thenReturn(true);
    when(pluginConfig.getGracePeriodMinutes()).thenReturn(10);
    when(pluginConfig.shouldNotifyAdmins()).thenReturn(false);

    storage = new TeamStorage(plugin, pluginConfig);
    scheduler = new TestDeadlineScheduler(plugin, pluginConfig, storage);
    membership = new MembershipService(plugin, pluginConfig, storage, scheduler);
  }

  @Test
  void sendInviteStoresPendingInvite() {
    when(pluginConfig.getJoinMode()).thenReturn(JoinMode.INVITE_ONLY);
    PlayerMock leader = server.addPlayer("LeaderInvite");
    PlayerMock target = server.addPlayer("TargetInvite");
    membership.createTeam("InviteTeam", "IT", "red", leader);
    drainMessages(leader);
    drainMessages(target);

    membership.sendInvite(leader, target, Duration.ofMinutes(5));

    List<PendingInvite> invites = membership.getInvitesForPlayer(target.getUniqueId());
    assertEquals(1, invites.size(), "Приглашение должно сохраняться");
    PendingInvite invite = invites.get(0);
    assertEquals("InviteTeam", invite.getTeamName(), "Название команды в приглашении сохраняется");

    Component targetMessage = target.nextComponentMessage();
    String plain = PlainTextComponentSerializer.plainText().serialize(targetMessage);
    assertTrue(plain.contains("InviteTeam"), "Сообщение должно содержать имя команды");
    Component leaderMessage = leader.nextComponentMessage();
    String leaderPlain = PlainTextComponentSerializer.plainText().serialize(leaderMessage);
    assertTrue(
        leaderPlain.contains(target.getName()),
        "Подтверждение для лидера должно содержать имя приглашённого");
  }

  @Test
  void acceptInviteAddsPlayerAndBroadcasts() {
    when(pluginConfig.getJoinMode()).thenReturn(JoinMode.INVITE_ONLY);
    PlayerMock leader = server.addPlayer("LeaderAccept");
    PlayerMock teammate = server.addPlayer("TeammateAccept");
    PlayerMock target = server.addPlayer("TargetAccept");
    membership.createTeam("AcceptTeam", "AT", "red", leader);
    membership.addPlayerToTeam("AcceptTeam", teammate);
    drainMessages(leader);
    drainMessages(teammate);
    drainMessages(target);

    membership.sendInvite(leader, target, Duration.ofMinutes(2));
    // consume invite messages
    target.nextComponentMessage();
    leader.nextComponentMessage();
    teammate.nextComponentMessage();

    membership.acceptInvite(target, "AcceptTeam");

    Team team = storage.getTeamByName("AcceptTeam");
    assertNotNull(team, "Команда должна существовать");
    assertTrue(team.hasMember(target.getUniqueId()), "Игрок должен стать участником команды");
    assertTrue(
        membership.getInvitesForPlayer(target.getUniqueId()).isEmpty(),
        "Приглашение удаляется после принятия");

    // Первое сообщение — стандартное уведомление о вступлении
    target.nextComponentMessage();
    assertEquals(
        TeamMessageUtils.inviteAcceptedMessage("AcceptTeam"),
        target.nextComponentMessage(),
        "Игрок получает подтверждение принятия");
    assertEquals(
        TeamMessageUtils.inviteAcceptedBroadcastMessage(target.getName()),
        teammate.nextComponentMessage(),
        "Сокоманник получает уведомление");
  }

  @Test
  void acceptInviteFailsWhenExpired() {
    when(pluginConfig.getJoinMode()).thenReturn(JoinMode.INVITE_ONLY);
    PlayerMock leader = server.addPlayer("LeaderExpire");
    PlayerMock target = server.addPlayer("TargetExpire");
    membership.createTeam("ExpireTeam", "ET", "red", leader);
    drainMessages(leader);
    drainMessages(target);

    membership.sendInvite(leader, target, Duration.ZERO);
    // consume invite notifications
    target.nextComponentMessage();
    leader.nextComponentMessage();

    membership.acceptInvite(target, "ExpireTeam");

    Team team = storage.getTeamByName("ExpireTeam");
    assertNotNull(team);
    assertFalse(team.hasMember(target.getUniqueId()), "Истёкшее приглашение не добавляет игрока");
    assertEquals(
        TeamMessageUtils.inviteExpiredMessage("ExpireTeam"),
        target.nextComponentMessage(),
        "Игрок информируется об истечении приглашения");
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
    assertEquals(1, scheduler.evaluateCalls, "После создания вызывается проверка лимитов");
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
    assertEquals(2, scheduler.evaluateCalls, "Проверка лимитов вызывается при успешном добавлении");
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
  void memberLeavingTeamSendsNotifications() {
    PlayerMock leader = server.addPlayer("LeaderLeave");
    PlayerMock member = server.addPlayer("MemberLeave");
    membership.createTeam("Alpha", "A", "red", leader);
    membership.addPlayerToTeam("Alpha", member);
    drainMessages(leader);
    drainMessages(member);

    membership.removePlayerFromTeam("Alpha", member);

    assertEquals(
        TeamMessageUtils.memberLeftSelfMessage("Alpha"),
        member.nextComponentMessage(),
        "Участник должен получить сообщение о выходе");
    assertEquals(
        TeamMessageUtils.memberLeftBroadcastMessage("MemberLeave"),
        leader.nextComponentMessage(),
        "Лидер получает уведомление о выходе участника");
  }

  @Test
  void leaderLeavingAloneDisbandsTeamAndSendsMessage() {
    PlayerMock leader = server.addPlayer("SoloLeader");
    membership.createTeam("Solo", "S", "red", leader);
    drainMessages(leader);

    membership.removePlayerFromTeam("Solo", leader);

    assertEquals(
        TeamMessageUtils.memberLeftSelfMessage("Solo"),
        leader.nextComponentMessage(),
        "Лидер получает сообщение о выходе");
    assertEquals(
        TeamMessageUtils.teamDisbandedLeaderMessage("Solo"),
        leader.nextComponentMessage(),
        "Лидер информируется о роспуске команды");
    assertNull(storage.getTeamByName("Solo"), "Команда должна быть удалена");
  }

  @Test
  void kickPlayerFromTeamSendsMessagesToAllParties() {
    PlayerMock leader = server.addPlayer("LeaderKick");
    PlayerMock member = server.addPlayer("MemberKick");
    PlayerMock teammate = server.addPlayer("TeammateKick");
    membership.createTeam("Omega", "O", "red", leader);
    membership.addPlayerToTeam("Omega", member);
    membership.addPlayerToTeam("Omega", teammate);
    drainMessages(leader);
    drainMessages(member);
    drainMessages(teammate);

    membership.kickPlayerFromTeam("Omega", leader, member.getName());

    assertEquals(
        TeamMessageUtils.memberKickedLeaderMessage(member.getName()),
        leader.nextComponentMessage(),
        "Лидер получает подтверждение об исключении");
    assertEquals(
        TeamMessageUtils.memberKickedTargetMessage("Omega", leader.getName()),
        member.nextComponentMessage(),
        "Исключённый игрок получает уведомление");
    assertEquals(
        TeamMessageUtils.memberKickedBroadcastMessage(member.getName()),
        teammate.nextComponentMessage(),
        "Остальные участники получают уведомление");
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

  @Test
  void disbandTeamSendsNotifications() {
    PlayerMock leader = server.addPlayer("LeaderDisband");
    PlayerMock member = server.addPlayer("MemberDisband");
    membership.createTeam("Omega", "O", "red", leader);
    membership.addPlayerToTeam("Omega", member);
    drainMessages(leader);
    drainMessages(member);

    membership.disbandTeam("Omega", leader);

    Component leaderMessage = leader.nextComponentMessage();
    Component memberMessage = member.nextComponentMessage();
    assertEquals(
        TeamMessageUtils.teamDisbandedLeaderMessage("Omega"),
        leaderMessage,
        "Лидер должен получить подтверждение о роспуске");
    assertEquals(
        TeamMessageUtils.teamDisbandedMemberMessage("Omega", leader.getName()),
        memberMessage,
        "Участники должны быть уведомлены о роспуске");
  }

  @Test
  void disbandTeamDoesNotNotifyNonLeader() {
    PlayerMock leader = server.addPlayer("LeaderDisbandFail");
    PlayerMock member = server.addPlayer("MemberDisbandFail");
    membership.createTeam("Sigma", "S", "red", leader);
    membership.addPlayerToTeam("Sigma", member);
    drainMessages(leader);
    drainMessages(member);

    membership.disbandTeam("Sigma", member);

    assertNull(
        member.nextComponentMessage(),
        "Игрок не должен получать сообщение при неудачной попытке роспуска");
  }

  @Test
  void transferLeadershipSendsNotifications() {
    PlayerMock leader = server.addPlayer("LeaderTransfer");
    PlayerMock newLeader = server.addPlayer("NewLeaderTransfer");
    PlayerMock teammate = server.addPlayer("TeammateTransfer");
    membership.createTeam("Zeta", "Z", "red", leader);
    membership.addPlayerToTeam("Zeta", newLeader);
    membership.addPlayerToTeam("Zeta", teammate);
    drainMessages(leader);
    drainMessages(newLeader);
    drainMessages(teammate);

    membership.transferLeadership("Zeta", leader, newLeader);

    assertEquals(
        TeamMessageUtils.leadershipTransferOutgoingMessage(newLeader.getName()),
        leader.nextComponentMessage(),
        "Исходящий лидер получает подтверждение");
    assertEquals(
        TeamMessageUtils.leadershipTransferIncomingMessage("Zeta"),
        newLeader.nextComponentMessage(),
        "Новый лидер получает уведомление");
    assertEquals(
        TeamMessageUtils.leadershipTransferBroadcastMessage(newLeader.getName()),
        teammate.nextComponentMessage(),
        "Остальные участники получают уведомление о смене лидера");
  }

  @Test
  void transferLeadershipDoesNotNotifyWhenInvalid() {
    PlayerMock leader = server.addPlayer("LeaderTransferFail");
    PlayerMock teammate = server.addPlayer("TeammateTransferFail");
    membership.createTeam("Eta", "E", "red", leader);
    membership.addPlayerToTeam("Eta", teammate);
    drainMessages(leader);
    drainMessages(teammate);

    membership.transferLeadership("Eta", teammate, leader);

    assertNull(
        teammate.nextComponentMessage(),
        "Игрок без прав не должен получать сообщение о передаче лидерства");
  }

  @Test
  void kickPlayerFromTeamSendsNotifications() {
    PlayerMock leader = server.addPlayer("LeaderKick");
    PlayerMock target = server.addPlayer("TargetKick");
    PlayerMock teammate = server.addPlayer("TeammateKick");
    membership.createTeam("Theta", "T", "red", leader);
    membership.addPlayerToTeam("Theta", target);
    membership.addPlayerToTeam("Theta", teammate);
    drainMessages(leader);
    drainMessages(target);
    drainMessages(teammate);

    membership.kickPlayerFromTeam("Theta", leader, target.getName());

    assertEquals(
        TeamMessageUtils.memberKickedLeaderMessage(target.getName()),
        leader.nextComponentMessage(),
        "Лидер получает подтверждение об исключении");
    assertEquals(
        TeamMessageUtils.memberKickedTargetMessage("Theta", leader.getName()),
        target.nextComponentMessage(),
        "Исключённый игрок получает уведомление");
    assertEquals(
        TeamMessageUtils.memberKickedBroadcastMessage(target.getName()),
        teammate.nextComponentMessage(),
        "Остальные участники уведомляются об исключении");
  }

  @Test
  void kickPlayerFromTeamDoesNotNotifyWhenTargetMissing() {
    PlayerMock leader = server.addPlayer("LeaderKickFail");
    membership.createTeam("Iota", "I", "red", leader);
    drainMessages(leader);

    membership.kickPlayerFromTeam("Iota", leader, "Ghost");

    assertNull(
        leader.nextComponentMessage(), "Сообщение не должно отправляться при отсутствии цели");
  }

  @Test
  void setTeamPrefixSendsNotifications() {
    PlayerMock leader = server.addPlayer("LeaderPrefix");
    PlayerMock member = server.addPlayer("MemberPrefix");
    membership.createTeam("Kappa", "K", "red", leader);
    membership.addPlayerToTeam("Kappa", member);
    drainMessages(leader);
    drainMessages(member);

    membership.setTeamPrefix("Kappa", "KP", leader);

    assertEquals(
        TeamMessageUtils.teamPrefixUpdatedLeaderMessage("KP"),
        leader.nextComponentMessage(),
        "Лидер получает сообщение об обновлении префикса");
    assertEquals(
        TeamMessageUtils.teamPrefixUpdatedMemberMessage("KP"),
        member.nextComponentMessage(),
        "Участники получают уведомление об обновлении префикса");
  }

  @Test
  void setTeamPrefixDoesNotNotifyNonLeader() {
    PlayerMock leader = server.addPlayer("LeaderPrefixFail");
    PlayerMock member = server.addPlayer("MemberPrefixFail");
    membership.createTeam("Lambda", "L", "red", leader);
    membership.addPlayerToTeam("Lambda", member);
    drainMessages(leader);
    drainMessages(member);

    membership.setTeamPrefix("Lambda", "LP", member);

    assertNull(
        member.nextComponentMessage(),
        "Игрок без прав не должен получать сообщение об изменении префикса");
  }

  @Test
  void setTeamColorSendsNotifications() {
    PlayerMock leader = server.addPlayer("LeaderColor");
    PlayerMock member = server.addPlayer("MemberColor");
    String teamName = "MuTeam";
    membership.createTeam(teamName, "M", "red", leader);
    membership.addPlayerToTeam(teamName, member);
    drainMessages(leader);
    drainMessages(member);

    membership.setTeamColor(teamName, "blue", leader);

    Component leaderMessage = leader.nextComponentMessage();
    Component memberMessage = member.nextComponentMessage();

    assertEquals(
        TeamMessageUtils.teamColorUpdatedLeaderMessage("blue"),
        leaderMessage,
        "Лидер получает сообщение об изменении цвета");
    assertEquals(
        TeamMessageUtils.teamColorUpdatedMemberMessage("blue"),
        memberMessage,
        "Участники получают уведомление об изменении цвета");
  }

  @Test
  void setTeamColorDoesNotNotifyNonLeader() {
    PlayerMock leader = server.addPlayer("LeaderColorFail");
    PlayerMock member = server.addPlayer("MemberColorFail");
    String teamName = "NuTeam";
    membership.createTeam(teamName, "N", "red", leader);
    membership.addPlayerToTeam(teamName, member);
    drainMessages(leader);
    drainMessages(member);

    membership.setTeamColor(teamName, "blue", member);

    assertNull(
        member.nextComponentMessage(), "Игрок без прав не получает сообщение об изменении цвета");
  }

  @Test
  void requestToJoinTeamStoresPendingRequest() {
    PlayerMock leader = server.addPlayer("JoinLeader");
    membership.createTeam("JoinTeam", "JT", "red", leader);
    drainMessages(leader);

    PlayerMock applicant = server.addPlayer("JoinApplicant");
    membership.requestToJoinTeam("JoinTeam", applicant);

    assertTrue(
        membership.hasPendingJoinRequest("JoinTeam", applicant.getUniqueId()),
        "Заявка должна быть сохранена");
    assertEquals(
        TeamMessageUtils.joinRequestSentMessage("JoinTeam"),
        applicant.nextComponentMessage(),
        "Игрок получает уведомление о заявке");
    assertEquals(
        TeamMessageUtils.joinRequestReceivedLeaderMessage("JoinApplicant", "JoinTeam"),
        leader.nextComponentMessage(),
        "Лидер получает уведомление о новой заявке");
  }

  @Test
  void requestToJoinTeamPreventsDuplicateRequests() {
    PlayerMock leader = server.addPlayer("JoinLeaderDup");
    membership.createTeam("JoinDup", "JD", "red", leader);
    drainMessages(leader);

    PlayerMock applicant = server.addPlayer("JoinApplicantDup");
    membership.requestToJoinTeam("JoinDup", applicant);
    // Сбрасываем сообщения из первой заявки
    applicant.nextComponentMessage();
    leader.nextComponentMessage();

    membership.requestToJoinTeam("JoinDup", applicant);

    assertTrue(
        membership.hasPendingJoinRequest("JoinDup", applicant.getUniqueId()),
        "Заявка остаётся в списке");
    assertEquals(
        TeamMessageUtils.joinRequestAlreadySentMessage("JoinDup"),
        applicant.nextComponentMessage(),
        "Игрок информируется о повторной заявке");
  }

  private void drainMessages(PlayerMock player) {
    Component message;
    do {
      message = player.nextComponentMessage();
    } while (message != null);
  }

  private static class TestDeadlineScheduler extends DeadlineScheduler {
    int enforceCalls;
    int evaluateCalls;
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
    public void evaluateTeam(Team team) {
      evaluateCalls++;
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

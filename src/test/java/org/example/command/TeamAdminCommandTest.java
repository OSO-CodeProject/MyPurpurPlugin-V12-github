package org.example.command;

import static org.junit.jupiter.api.Assertions.*;

import be.seeseemelk.mockbukkit.entity.PlayerMock;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandMap;
import org.example.MockBukkitTestBase;
import org.example.service.TeamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TeamAdminCommandTest extends MockBukkitTestBase {

  private TeamService teamService;

  @BeforeEach
  void setUp() {
    teamService = plugin.getTeamManager();
  }

  @Test
  void getPlayerInfoDisplaysOnlineLeaderDetails() {
    CommandMap commandMap = server.getCommandMap();

    PlayerMock inspector = server.addPlayer("Inspector");
    inspector.addAttachment(plugin, "mypurpurplugin.teamadmin", true);
    drainMessages(inspector);

    PlayerMock leader = server.addPlayer("LeaderInfo");
    leader.addAttachment(plugin, "mypurpurplugin.team", true);
    drainMessages(leader);

    teamService.createTeam("InfoTeam", "IT", "WHITE", leader);
    drainMessages(leader);

    assertTrue(commandMap.dispatch(inspector, "teamadmin getplinfo LeaderInfo"));

    Component response = inspector.nextComponentMessage();
    assertNotNull(response, "Админ должен получить сообщение с данными игрока");
    String plain = PlainTextComponentSerializer.plainText().serialize(response);

    assertTrue(plain.contains("LeaderInfo"), "Сообщение содержит имя игрока");
    assertTrue(plain.contains("В сети"), "Должен отображаться статус 'В сети'");
    assertTrue(plain.contains("InfoTeam"), "Должно отображаться название команды");
    assertTrue(plain.contains("Лидер"), "Роль лидера должна быть указана");
    assertTrue(
        plain.contains(leader.getUniqueId().toString()), "UUID игрока должен присутствовать");
  }

  @Test
  void getPlayerInfoDisplaysOfflineMemberDetails() {
    CommandMap commandMap = server.getCommandMap();

    PlayerMock inspector = server.addPlayer("Moderator");
    inspector.addAttachment(plugin, "mypurpurplugin.teamadmin", true);
    drainMessages(inspector);

    PlayerMock leader = server.addPlayer("OfflineLeader");
    leader.addAttachment(plugin, "mypurpurplugin.team", true);
    drainMessages(leader);

    teamService.createTeam("OfflineTeam", "OT", "WHITE", leader);
    drainMessages(leader);

    PlayerMock member = server.addPlayer("OfflineMember");
    member.addAttachment(plugin, "mypurpurplugin.team", true);
    drainMessages(member);

    teamService.addPlayerToTeam("OfflineTeam", member);
    drainMessages(member);

    member.disconnect();

    assertTrue(commandMap.dispatch(inspector, "teamadmin getplinfo OfflineMember"));

    Component response = inspector.nextComponentMessage();
    assertNotNull(response, "Админ должен получить сообщение с данными игрока");
    String plain = PlainTextComponentSerializer.plainText().serialize(response);

    assertTrue(plain.contains("OfflineMember"), "Имя оффлайн игрока отображается");
    assertTrue(plain.contains("Оффлайн"), "Должен отображаться статус 'Оффлайн'");
    assertTrue(plain.contains("OfflineTeam"), "Команда оффлайн игрока указана");
    assertTrue(plain.contains("Участник"), "Роль участника должна быть указана");
    assertTrue(
        plain.contains(member.getUniqueId().toString()),
        "UUID оффлайн игрока должен присутствовать");
  }

  private void drainMessages(PlayerMock player) {
    while (player.nextComponentMessage() != null) {
      // discard
    }
  }
}

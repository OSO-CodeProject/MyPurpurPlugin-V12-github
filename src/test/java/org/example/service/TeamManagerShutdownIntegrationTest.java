package org.example.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import be.seeseemelk.mockbukkit.entity.PlayerMock;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.example.MockBukkitTestBase;
import org.example.listener.TeamChatListener;
import org.junit.jupiter.api.Test;

class TeamManagerShutdownIntegrationTest extends MockBukkitTestBase {

  @Test
  void shutdownResetsPlayerListNames() {
    PlayerMock alice = server.addPlayer("Alice");
    PlayerMock bob = server.addPlayer("Bob");

    Component prefix = Component.text("[A] ", NamedTextColor.RED);
    server.getPluginManager().callEvent(new TeamChatListener.PlayerPrefixUpdateEvent(alice, prefix));
    server.getPluginManager().callEvent(new TeamChatListener.PlayerPrefixUpdateEvent(bob, prefix));

    assertEquals(prefix.append(Component.text("Alice")), alice.playerListName());
    assertEquals(prefix.append(Component.text("Bob")), bob.playerListName());

    plugin.getTeamManager().shutdown();

    assertEquals(Component.text("Alice"), alice.playerListName());
    assertEquals(Component.text("Bob"), bob.playerListName());
  }
}

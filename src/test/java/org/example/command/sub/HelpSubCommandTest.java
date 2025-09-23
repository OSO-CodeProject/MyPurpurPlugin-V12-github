package org.example.command.sub;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HelpSubCommandTest {

  private ServerMock server;
  private PlayerMock player;
  private HelpSubCommand command;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    player = server.addPlayer();
    command = new HelpSubCommand();
  }

  @AfterEach
  void tearDown() {
    MockBukkit.unmock();
  }

  @Test
  void executeSendsFormattedHelpMessage() {
    boolean result = command.execute(player, new String[0]);

    assertTrue(result, "Help command should return true");

    Component message = player.nextComponentMessage();
    assertNotNull(message, "Help message should be sent to the player");

    String plainText = PlainTextComponentSerializer.plainText().serialize(message);

    assertTrue(
        plainText.contains("/team create"),
        "Help text should still mention the /team create command");
    assertTrue(
        plainText.contains("/team help"), "Help text should still mention the /team help command");
  }
}

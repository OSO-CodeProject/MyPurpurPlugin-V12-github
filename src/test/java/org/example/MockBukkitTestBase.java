package org.example;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import org.example.MyPurpurPlugin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

/** Base test class that bootstraps MockBukkit once per test class. */
public abstract class MockBukkitTestBase {

  protected static ServerMock server;
  protected static MyPurpurPlugin plugin;
  private static boolean initialized;

  @BeforeAll
  static void setUpMockBukkit() {
    if (!initialized) {
      server = MockBukkit.mock();
      plugin = MockBukkit.load(MyPurpurPlugin.class);
      initialized = true;
    }
  }

  @AfterAll
  static void tearDownMockBukkit() {
    if (initialized) {
      MockBukkit.unmock();
      server = null;
      plugin = null;
      initialized = false;
    }
  }
}

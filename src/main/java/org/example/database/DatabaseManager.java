package org.example.database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

/** Manages the SQLite database connection and initialization. */
public class DatabaseManager {

  private final JavaPlugin plugin;
  private final String url;
  private Connection connection;

  public DatabaseManager(JavaPlugin plugin) {
    this.plugin = plugin;
    File dataFolder = plugin.getDataFolder();
    if (!dataFolder.exists()) {
      dataFolder.mkdirs();
    }
    this.url = "jdbc:sqlite:" + new File(dataFolder, "database.db").getAbsolutePath();
  }

  /** Connects to the SQLite database and initializes tables. */
  public void connect() {
    try {
      connection = DriverManager.getConnection(url);
      plugin.getLogger().info("Successfully connected to SQLite database.");
      initializeTables();
    } catch (SQLException e) {
      plugin.getLogger().severe("Could not connect to SQLite database: " + e.getMessage());
    }
  }

  /** Gets the active connection. */
  @Nullable
  public Connection getConnection() {
    try {
      if (connection != null && !connection.isClosed()) {
        return connection;
      }
      connection = DriverManager.getConnection(url);
      return connection;
    } catch (SQLException e) {
      plugin.getLogger().severe("Lost connection to SQLite database: " + e.getMessage());
      return null;
    }
  }

  /** Closes the connection. */
  public void disconnect() {
    try {
      if (connection != null && !connection.isClosed()) {
        connection.close();
        plugin.getLogger().info("Disconnected from SQLite database.");
      }
    } catch (SQLException e) {
      plugin.getLogger().severe("Error closing database connection: " + e.getMessage());
    }
  }

  /** Creates the required tables if they don't exist. */
  private void initializeTables() {
    String createTeamsTable =
        "CREATE TABLE IF NOT EXISTS teams ("
            + "id VARCHAR(36) PRIMARY KEY,"
            + "name VARCHAR(64) NOT NULL UNIQUE,"
            + "leader_id VARCHAR(36) NOT NULL,"
            + "prefix VARCHAR(64) NOT NULL,"
            + "color VARCHAR(32) NOT NULL,"
            + "deadline BIGINT DEFAULT 0"
            + ");";

    String createTeamMembersTable =
        "CREATE TABLE IF NOT EXISTS team_members ("
            + "team_id VARCHAR(36) NOT NULL,"
            + "player_uuid VARCHAR(36) NOT NULL,"
            + "PRIMARY KEY (team_id, player_uuid),"
            + "FOREIGN KEY(team_id) REFERENCES teams(id) ON DELETE CASCADE"
            + ");";

    try (Connection conn = getConnection()) {
      if (conn != null) {
        try (Statement stmt = conn.createStatement()) {
          stmt.execute(
              "PRAGMA foreign_keys = ON;"); // SQLite requires this to enforce ON DELETE CASCADE
          stmt.execute(createTeamsTable);
          stmt.execute(createTeamMembersTable);
        }
      }
    } catch (SQLException e) {
      plugin.getLogger().severe("Error creating database tables: " + e.getMessage());
    }
  }
}

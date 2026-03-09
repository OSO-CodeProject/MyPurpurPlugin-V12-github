package org.example.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.plugin.java.JavaPlugin;
import org.example.model.Team;
import org.jetbrains.annotations.NotNull;

/** Handles all database operations for Teams. */
public class TeamRepository {

  private final DatabaseManager dbManager;
  private final JavaPlugin plugin;

  public TeamRepository(DatabaseManager dbManager, JavaPlugin plugin) {
    this.dbManager = dbManager;
    this.plugin = plugin;
  }

  /** Loads all teams from the database into memory objects. */
  public List<Team> loadAllTeams(java.util.Map<UUID, Long> deadlines) {
    List<Team> teams = new ArrayList<>();
    String query = "SELECT * FROM teams";

    try (Connection conn = dbManager.getConnection();
        PreparedStatement stmt = conn != null ? conn.prepareStatement(query) : null) {

      if (stmt != null) {
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
          UUID id = UUID.fromString(rs.getString("id"));
          String name = rs.getString("name");
          UUID leaderId = UUID.fromString(rs.getString("leader_id"));
          String prefix = rs.getString("prefix");
          String color = rs.getString("color");
          long deadline = rs.getLong("deadline");
          if (deadline > 0) {
            deadlines.put(id, deadline);
          }

          Team team = new Team(id, name, leaderId, prefix, color);
          team.addMember(leaderId); // Ensure leader is member
          // Deadlines will be managed temporarily by TeamStorage, but they are stored here safely.
          loadMembersForTeam(team, conn);
          teams.add(team);
        }
      }
    } catch (SQLException e) {
      plugin.getLogger().severe("Error loading teams from database: " + e.getMessage());
    }
    return teams;
  }

  private void loadMembersForTeam(Team team, Connection conn) throws SQLException {
    String query = "SELECT player_uuid FROM team_members WHERE team_id = ?";
    try (PreparedStatement stmt = conn.prepareStatement(query)) {
      stmt.setString(1, team.getId().toString());
      ResultSet rs = stmt.executeQuery();
      while (rs.next()) {
        UUID memberId = UUID.fromString(rs.getString("player_uuid"));
        team.addMember(memberId);
      }
    }
  }

  /** Saves a new team or updates an existing one perfectly. */
  public void saveTeam(@NotNull Team team, long deadline) {
    String query =
        "INSERT INTO teams (id, name, leader_id, prefix, color, deadline) VALUES (?, ?, ?, ?, ?, ?) "
            + "ON CONFLICT(id) DO UPDATE SET name=excluded.name, leader_id=excluded.leader_id, "
            + "prefix=excluded.prefix, color=excluded.color, deadline=excluded.deadline";

    try (Connection conn = dbManager.getConnection();
        PreparedStatement stmt = conn != null ? conn.prepareStatement(query) : null) {

      if (stmt != null) {
        stmt.setString(1, team.getId().toString());
        stmt.setString(2, team.getName());
        stmt.setString(3, team.getLeaderId() != null ? team.getLeaderId().toString() : "");
        stmt.setString(4, team.getPrefix());
        stmt.setString(5, team.getColor().toString());
        stmt.setLong(6, deadline);
        stmt.executeUpdate();

        // Clear and re-insert members (simplest approach to sync)
        clearTeamMembers(team.getId(), conn);
        insertTeamMembers(team, conn);
      }
    } catch (SQLException e) {
      plugin.getLogger().severe("Error saving team to database: " + e.getMessage());
    }
  }

  private void clearTeamMembers(UUID teamId, Connection conn) throws SQLException {
    String query = "DELETE FROM team_members WHERE team_id = ?";
    try (PreparedStatement stmt = conn.prepareStatement(query)) {
      stmt.setString(1, teamId.toString());
      stmt.executeUpdate();
    }
  }

  private void insertTeamMembers(Team team, Connection conn) throws SQLException {
    String query = "INSERT INTO team_members (team_id, player_uuid) VALUES (?, ?)";
    try (PreparedStatement stmt = conn.prepareStatement(query)) {
      for (UUID memberId : team.getMembers()) {
        stmt.setString(1, team.getId().toString());
        stmt.setString(2, memberId.toString());
        stmt.addBatch();
      }
      stmt.executeBatch();
    }
  }

  public void deleteTeam(UUID teamId) {
    String query = "DELETE FROM teams WHERE id = ?";
    // Because of ON DELETE CASCADE, team_members will be deleted automatically.
    try (Connection conn = dbManager.getConnection();
        PreparedStatement stmt = conn != null ? conn.prepareStatement(query) : null) {

      if (stmt != null) {
        stmt.setString(1, teamId.toString());
        stmt.executeUpdate();
      }
    } catch (SQLException e) {
      plugin.getLogger().severe("Error deleting team from database: " + e.getMessage());
    }
  }
}

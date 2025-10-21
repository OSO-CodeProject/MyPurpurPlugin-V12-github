package org.example.model;

import java.time.Instant;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Describes a join request submitted by a player for a specific team. */
public final class PendingRequest {

  private final UUID teamId;
  private final UUID playerId;
  private final String teamName;
  private final String playerName;
  private final long createdAt;
  @Nullable private final Long expiresAt;

  public PendingRequest(
      @NotNull UUID teamId,
      @NotNull UUID playerId,
      @NotNull String teamName,
      @NotNull String playerName,
      @Nullable Long expiresAt) {
    this.teamId = teamId;
    this.playerId = playerId;
    this.teamName = teamName;
    this.playerName = playerName;
    this.createdAt = Instant.now().toEpochMilli();
    this.expiresAt = expiresAt;
  }

  public @NotNull UUID getTeamId() {
    return teamId;
  }

  public @NotNull UUID getPlayerId() {
    return playerId;
  }

  public @NotNull String getTeamName() {
    return teamName;
  }

  public @NotNull String getPlayerName() {
    return playerName;
  }

  public long getCreatedAt() {
    return createdAt;
  }

  public @Nullable Long getExpiresAt() {
    return expiresAt;
  }

  public boolean isExpired() {
    return expiresAt != null && expiresAt <= System.currentTimeMillis();
  }
}

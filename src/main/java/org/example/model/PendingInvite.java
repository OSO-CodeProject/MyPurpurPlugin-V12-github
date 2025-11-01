package org.example.model;

import java.time.Duration;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Represents a pending invitation for a player to join a team. */
public final class PendingInvite {

  private final UUID teamId;
  private final UUID targetPlayerId;
  private final UUID inviterId;
  private final String teamName;
  private final String inviterName;
  private final String targetName;
  private final long createdAt;
  private final Long expiresAt;

  public PendingInvite(
      @NotNull UUID teamId,
      @NotNull UUID targetPlayerId,
      @NotNull UUID inviterId,
      @NotNull String teamName,
      @NotNull String inviterName,
      @NotNull String targetName,
      @Nullable Duration ttl) {
    this.teamId = teamId;
    this.targetPlayerId = targetPlayerId;
    this.inviterId = inviterId;
    this.teamName = teamName;
    this.inviterName = inviterName;
    this.targetName = targetName;
    this.createdAt = System.currentTimeMillis();
    if (ttl == null) {
      this.expiresAt = null;
    } else {
      long ttlMillis = ttl.toMillis();
      long now = this.createdAt;
      if (ttlMillis <= 0L) {
        this.expiresAt = now;
      } else {
        this.expiresAt = now + ttlMillis;
      }
    }
  }

  private PendingInvite(
      @NotNull UUID teamId,
      @NotNull UUID targetPlayerId,
      @NotNull UUID inviterId,
      @NotNull String teamName,
      @NotNull String inviterName,
      @NotNull String targetName,
      long createdAt,
      @Nullable Long expiresAt) {
    this.teamId = teamId;
    this.targetPlayerId = targetPlayerId;
    this.inviterId = inviterId;
    this.teamName = teamName;
    this.inviterName = inviterName;
    this.targetName = targetName;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
  }

  public @NotNull UUID getTeamId() {
    return teamId;
  }

  public @NotNull UUID getTargetPlayerId() {
    return targetPlayerId;
  }

  public @NotNull UUID getInviterId() {
    return inviterId;
  }

  public @NotNull String getTeamName() {
    return teamName;
  }

  public @NotNull String getInviterName() {
    return inviterName;
  }

  public @NotNull String getTargetName() {
    return targetName;
  }

  public long getCreatedAt() {
    return createdAt;
  }

  public @Nullable Long getExpiresAt() {
    return expiresAt;
  }

  public boolean isExpired() {
    return expiresAt != null && System.currentTimeMillis() >= expiresAt;
  }

  public PendingInvite withUpdatedNames(String updatedTeamName, String updatedTargetName) {
    return new PendingInvite(
        teamId,
        targetPlayerId,
        inviterId,
        updatedTeamName,
        inviterName,
        updatedTargetName,
        createdAt,
        expiresAt);
  }

  public static PendingInvite restored(
      @NotNull UUID teamId,
      @NotNull UUID targetPlayerId,
      @NotNull UUID inviterId,
      @NotNull String teamName,
      @NotNull String inviterName,
      @NotNull String targetName,
      long createdAt,
      @Nullable Long expiresAt) {
    return new PendingInvite(
        teamId,
        targetPlayerId,
        inviterId,
        teamName,
        inviterName,
        targetName,
        createdAt,
        expiresAt);
  }
}


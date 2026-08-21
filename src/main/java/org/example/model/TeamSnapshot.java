package org.example.model;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Неизменяемый снимок состояния команды для безопасного использования в кэшах.
 * Предоставляет моментальный снимок данных команды без риска изменения из других потоков.
 */
public final class TeamSnapshot {
  private final UUID id;
  private final String name;
  private final UUID leaderId;
  private final List<UUID> members;
  private final String prefix;
  private final NamedTextColor color;

  public TeamSnapshot(@NotNull Team team) {
    this.id = team.getId();
    this.name = team.getName();
    this.leaderId = team.getLeaderId();
    this.members = Collections.unmodifiableList(team.getMembers());
    this.prefix = team.getPrefix();
    this.color = team.getColor();
  }

  // Для восстановления из БД
  public TeamSnapshot(
      @NotNull UUID id,
      @NotNull String name,
      @Nullable UUID leaderId,
      @NotNull List<UUID> members,
      @NotNull String prefix,
      @NotNull NamedTextColor color) {
    this.id = id;
    this.name = name;
    this.leaderId = leaderId;
    this.members = Collections.unmodifiableList(members);
    this.prefix = prefix;
    this.color = color;
  }

  public @NotNull UUID getId() {
    return id;
  }

  public @NotNull String getName() {
    return name;
  }

  public @Nullable UUID getLeaderId() {
    return leaderId;
  }

  public @NotNull List<UUID> getMembers() {
    return members;
  }

  public @NotNull String getPrefix() {
    return prefix;
  }

  public @NotNull NamedTextColor getColor() {
    return color;
  }

  public boolean hasMember(@NotNull UUID playerId) {
    return members.contains(playerId);
  }

  public boolean isLeader(@NotNull UUID playerId) {
    return leaderId != null && leaderId.equals(playerId);
  }

  public int getMemberCount() {
    return members.size();
  }
}

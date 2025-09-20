package org.example.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.example.util.TeamUtils;
import org.jetbrains.annotations.Nullable;

/** Представляет команду с уникальным идентификатором и изменяемыми данными. */
public class Team {
  private final UUID id; // Уникальный неизменяемый идентификатор
  private String name; // Название команды, теперь изменяемое

  private UUID leader;
  private final List<UUID> members;
  private final Set<UUID> memberLookup;

  private String prefix;
  private NamedTextColor color;

  public Team(String name, UUID leader, String prefix, String color) {
    this(UUID.randomUUID(), name, leader, prefix, color);
  }

  public Team(UUID id, String name, UUID leader, String prefix, String color) {
    this.id = id;
    this.name = name;
    this.leader = leader;
    this.members = new ArrayList<>();
    this.memberLookup = new HashSet<>();
    addMember(leader);
    this.prefix = prefix;
    this.color =
        NamedTextColor.NAMES.valueOr(color.toLowerCase(Locale.ROOT), NamedTextColor.WHITE);
  }

  // Геттеры
  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public UUID getLeaderId() {
    return leader;
  }

  public List<UUID> getMembers() {
    return new ArrayList<>(members);
  }

  public UUID getFirstMember() {
    return members.isEmpty() ? null : members.get(0);
  }

  public String getPrefix() {
    return prefix;
  }

  public NamedTextColor getColor() {
    return color;
  }

  // Сеттеры
  public void setName(String name) {
    this.name = name;
  }

  public void setLeader(UUID leader) {
    this.leader = leader;
    addMember(leader);
  }

  public void setPrefix(String prefix) {
    this.prefix = prefix;
  }

  public void setColor(String color) {
    this.color =
        NamedTextColor.NAMES.valueOr(color.toLowerCase(Locale.ROOT), NamedTextColor.WHITE);
  }

  // Методы управления участниками

  public void addMember(UUID member) {
    if (member == null) {
      return;
    }
    if (memberLookup.add(member)) {
      members.add(member);
    }
  }

  public void removeMember(UUID member) {
    if (member == null) {
      return;
    }
    if (memberLookup.remove(member)) {
      members.removeIf(existing -> existing.equals(member));
    }
  }

  public void setMembers(List<UUID> newMembers) {
    members.clear();
    memberLookup.clear();
    if (newMembers == null) {
      return;
    }
    for (UUID newMember : newMembers) {
      if (newMember == null) {
        continue;
      }
      if (memberLookup.add(newMember)) {
        members.add(newMember);
      }
    }
  }

  // Утилитный метод для префикса
  public Component getPrefixComponent() {
    return TeamUtils.createPrefixComponent(prefix, color);
  }

  // Проверка, является ли игрок участником

  public boolean hasMember(UUID playerId) {
    return playerId != null && memberLookup.contains(playerId);
  }

  // Проверка, является ли игрок лидером
  public boolean isLeader(UUID playerId) {
    return playerId != null && playerId.equals(leader);
  }

  @Nullable
  public UUID findMember(UUID playerId) {
    if (playerId == null) {
      return null;
    }
    for (UUID member : members) {
      if (member.equals(playerId)) {
        return member;
      }
    }
    return null;
  }
}

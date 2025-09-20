package org.example.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.example.util.TeamUtils;

/** Представляет команду с уникальным идентификатором и изменяемыми данными. */
public class Team {
  private final UUID id; // Уникальный неизменяемый идентификатор
  private String name; // Название команды, теперь изменяемое
  private UUID leader;
  private final List<UUID> members;
  private String prefix;
  private NamedTextColor color;

  public Team(String name, UUID leader, String prefix, String color) {
    this(UUID.randomUUID(), name, leader, prefix, color);
  }

  public Team(UUID id, String name, UUID leader, String prefix, String color) {
    this.id = id;
    this.name = name;
    this.leader = leader;
    this.members = new ArrayList<>(List.of(leader));
    this.prefix = prefix;
    this.color = NamedTextColor.NAMES.valueOr(color.toLowerCase(Locale.ROOT), NamedTextColor.WHITE);
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
  }

  public void setPrefix(String prefix) {
    this.prefix = prefix;
  }

  public void setColor(String color) {
    this.color = NamedTextColor.NAMES.valueOr(color.toLowerCase(Locale.ROOT), NamedTextColor.WHITE);
  }

  // Методы управления участниками
  public void addMember(UUID member) {
    if (!members.contains(member)) {
      members.add(member);
    }
  }

  public void removeMember(UUID member) {
    members.remove(member);
  }

  public void setMembers(List<UUID> newMembers) {
    members.clear();
    members.addAll(newMembers);
  }

  // Утилитный метод для префикса
  public Component getPrefixComponent() {
    return TeamUtils.createPrefixComponent(prefix, color);
  }

  // Проверка, является ли игрок участником
  public boolean hasMember(UUID playerId) {
    return members.contains(playerId);
  }

  // Проверка, является ли игрок лидером
  public boolean isLeader(UUID playerId) {
    return leader.equals(playerId);
  }
}

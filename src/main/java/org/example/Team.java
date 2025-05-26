package org.example;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Представляет команду с уникальным идентификатором и изменяемыми данными.
 */
public class Team {
    private final UUID id; // Уникальный неизменяемый идентификатор
    private String name;   // Название команды, теперь изменяемое
    private String leader;
    private final List<String> members;
    private String prefix;
    private NamedTextColor color;

    public Team(String name, String leader, String prefix, String color) {
        this.id = UUID.randomUUID(); // Генерируем новый UUID при создании
        this.name = name;
        this.leader = leader;
        this.members = new ArrayList<>(List.of(leader));
        this.prefix = prefix;
        this.color = NamedTextColor.NAMES.valueOr(color.toLowerCase(), NamedTextColor.WHITE);
    }

    // Геттеры
    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getLeader() {
        return leader;
    }

    public List<String> getMembers() {
        return new ArrayList<>(members);
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

    public void setLeader(String leader) {
        this.leader = leader;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public void setColor(String color) {
        this.color = NamedTextColor.NAMES.valueOr(color.toLowerCase(), NamedTextColor.WHITE);
    }

    // Методы управления участниками
    public void addMember(String member) {
        if (!members.contains(member)) {
            members.add(member);
        }
    }

    public void removeMember(String member) {
        members.remove(member);
    }

    // Утилитный метод для префикса
    public Component getPrefixComponent() {
        return TeamUtils.createPrefixComponent(prefix, color);
    }

    // Проверка, является ли игрок участником
    public boolean hasMember(String playerName) {
        return members.contains(playerName);
    }

    // Проверка, является ли игрок лидером
    public boolean isLeader(String playerName) {
        return leader.equals(playerName);
    }
}
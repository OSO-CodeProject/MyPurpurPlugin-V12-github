package org.example.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.example.MyPurpurPlugin;
import org.example.service.TeamService;
import org.example.util.TeamMessageUtils;
import org.example.util.TeamUtils;
import org.jetbrains.annotations.NotNull;

/** Слушатель событий, связанных с командами и чатом игроков. */
public class TeamChatListener implements Listener {

  private final TeamService teamManager;
  private final Map<UUID, Component> lastPlayerPrefixes = new ConcurrentHashMap<>();

  public TeamChatListener(@NotNull TeamService teamManager) {
    this.teamManager = teamManager;
  }

  @EventHandler
  public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
    Player player = event.getPlayer();
    updatePlayerPrefix(player);
    String teamName = teamManager.getPlayerTeam(player);
    if (teamName != null && teamManager.getTeamIdByName(teamName) != null) {
      String leaderName = teamManager.getTeamLeader(teamName);
      if (player.getName().equals(leaderName)) {
        Long deadline = teamManager.getTeamDeadline(teamName);
        if (deadline != null) {
          long remaining = deadline - System.currentTimeMillis();
          if (remaining > 0) {
            int minutes = (int) Math.ceil(remaining / 60000.0);
            int max = teamManager.getPluginConfig().getMaxMembers();
            int excess = teamManager.getTeamMembers(teamName).size() - max;
            TeamMessageUtils.sendTeamMessage(
                player, TeamMessageUtils.deadlineWarningMessage(max, minutes, excess));
          }
        }
      }
    }
    ((MyPurpurPlugin) teamManager.getPlugin())
        .debugTeamAction("Обновлён префикс для игрока при входе", player.getName(), null);
  }

  @EventHandler
  public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
    Player player = event.getPlayer();
    lastPlayerPrefixes.remove(player.getUniqueId());
    ((MyPurpurPlugin) teamManager.getPlugin())
        .debugTeamAction("Игрок вышел", player.getName(), null);
  }

  @EventHandler
  public void onPlayerChat(@NotNull AsyncChatEvent event) {
    Player player = event.getPlayer();
    String teamName = teamManager.getPlayerTeam(player);
    ((MyPurpurPlugin) teamManager.getPlugin())
        .debugTeamAction("Обработка сообщения от игрока", player.getName(), teamName);

    boolean forceWhiteChat = teamManager.getPluginConfig().isForceWhiteChat();

    if (teamName != null) {
      String prefix = teamManager.getTeamPrefix(teamName);
      NamedTextColor teamColor = teamManager.getTeamColor(teamName);
      Component prefixComponent = TeamUtils.createPrefixComponent(prefix, teamColor);

      event.renderer(
          (source, sourceDisplayName, message, viewer) ->
              prefixComponent
                  .append(sourceDisplayName)
                  .append(Component.text(": "))
                  .append(forceWhiteChat ? message.color(NamedTextColor.WHITE) : message));
    } else {
      event.renderer(
          (source, sourceDisplayName, message, viewer) ->
              sourceDisplayName
                  .append(Component.text(": "))
                  .append(forceWhiteChat ? message.color(NamedTextColor.WHITE) : message));
    }
  }

  @EventHandler
  public void onPlayerPrefixUpdate(@NotNull PlayerPrefixUpdateEvent event) {
    Player player = event.getPlayer();
    Component prefix = event.getPrefix();
    UUID playerId = player.getUniqueId();
    ((MyPurpurPlugin) teamManager.getPlugin())
        .debugTeamAction("Обновление префикса для игрока", player.getName(), null);

    if (prefix != null) {
      Component cachedPrefix = lastPlayerPrefixes.get(playerId);
      if (Objects.equals(cachedPrefix, prefix)) {
        return;
      }
      ((MyPurpurPlugin) teamManager.getPlugin())
          .debug("Устанавливаем префикс для игрока " + player.getName() + ": " + prefix);
      lastPlayerPrefixes.put(playerId, prefix);
      player.playerListName(prefix.append(Component.text(player.getName(), NamedTextColor.WHITE)));
    } else {
      ((MyPurpurPlugin) teamManager.getPlugin())
          .debug("Сбрасываем префикс для игрока " + player.getName());
      lastPlayerPrefixes.remove(playerId);
      player.playerListName(Component.text(player.getName(), NamedTextColor.WHITE));
    }
  }

  private void updatePlayerPrefix(@NotNull Player player) {
    String teamName = teamManager.getPlayerTeam(player);
    UUID playerId = player.getUniqueId();
    ((MyPurpurPlugin) teamManager.getPlugin())
        .debugTeamAction("Обновление префикса для игрока", player.getName(), teamName);

    if (teamName != null) {
      String prefix = teamManager.getTeamPrefix(teamName);
      NamedTextColor teamColor = teamManager.getTeamColor(teamName);
      Component prefixComponent = TeamUtils.createPrefixComponent(prefix, teamColor);
      lastPlayerPrefixes.put(playerId, prefixComponent);
      player.playerListName(
          prefixComponent.append(Component.text(player.getName(), NamedTextColor.WHITE)));
    } else {
      lastPlayerPrefixes.remove(playerId);
      player.playerListName(Component.text(player.getName(), NamedTextColor.WHITE));
    }
  }

  /** Внутренний класс для события обновления префикса игрока. */
  public static class PlayerPrefixUpdateEvent extends org.bukkit.event.Event {
    private static final org.bukkit.event.HandlerList HANDLERS = new org.bukkit.event.HandlerList();

    private final Player player;
    private final Component prefix;

    public PlayerPrefixUpdateEvent(@NotNull Player player, Component prefix) {
      this.player = player;
      this.prefix = prefix;
    }

    public @NotNull Player getPlayer() {
      return player;
    }

    @SuppressWarnings("unused")
    public Component getPrefix() {
      return prefix;
    }

    @Override
    public @NotNull org.bukkit.event.HandlerList getHandlers() {
      return HANDLERS;
    }

    @SuppressWarnings("unused")
    public static @NotNull org.bukkit.event.HandlerList getHandlerList() {
      return HANDLERS;
    }
  }
}

package org.example.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
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
  private final Map<UUID, Component> originalPlayerListNames = new ConcurrentHashMap<>();

  public TeamChatListener(@NotNull TeamService teamManager) {
    this.teamManager = teamManager;
    Bukkit.getOnlinePlayers().forEach(this::updatePlayerPrefix);
  }

  @EventHandler
  public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
    Player player = event.getPlayer();
    updatePlayerPrefix(player);
    String teamName = teamManager.getPlayerTeam(player);
    if (teamName != null && teamManager.getTeamIdByName(teamName) != null) {
      UUID leaderId = teamManager.getTeamLeaderId(teamName);
      if (leaderId != null && player.getUniqueId().equals(leaderId)) {
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
    originalPlayerListNames.remove(player.getUniqueId());
    ((MyPurpurPlugin) teamManager.getPlugin())
        .debugTeamAction("Игрок вышел", player.getName(), null);
  }

  @EventHandler
  public void onPlayerChat(@NotNull AsyncChatEvent event) {
    Player player = event.getPlayer();
    ((MyPurpurPlugin) teamManager.getPlugin())
        .debugTeamAction("Обработка сообщения от игрока", player.getName(), null);

    boolean forceWhiteChat = teamManager.getPluginConfig().isForceWhiteChat();
    UUID playerId = player.getUniqueId();
    Component prefixComponent = lastPlayerPrefixes.get(playerId);

    if (prefixComponent == null) {
      Bukkit.getScheduler().runTask(teamManager.getPlugin(), () -> updatePlayerPrefix(player));
    }

    event.renderer(
        (source, sourceDisplayName, message, viewer) -> {
          Component latestPrefixComponent = lastPlayerPrefixes.get(playerId);
          Component base = Component.empty();
          if (latestPrefixComponent != null) {
            base = base.append(latestPrefixComponent);
          }
          Component formattedMessage = forceWhiteChat ? message.color(NamedTextColor.WHITE) : message;
          return base.append(sourceDisplayName).append(Component.text(": ")).append(formattedMessage);
        });
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
      Component originalName = getOrStoreOriginalPlayerListName(player, cachedPrefix, prefix);
      lastPlayerPrefixes.put(playerId, prefix);
      player.playerListName(prefix.append(originalName));
    } else {
      ((MyPurpurPlugin) teamManager.getPlugin())
          .debug("Сбрасываем префикс для игрока " + player.getName());
      lastPlayerPrefixes.remove(playerId);
      restoreOriginalPlayerListName(player);
    }
  }

  /** Clears cached prefix information for all tracked players. */
  public void clearCachedPrefixes() {
    lastPlayerPrefixes.clear();
    originalPlayerListNames.clear();
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
      Component cachedPrefix = lastPlayerPrefixes.get(playerId);
      Component originalName =
          getOrStoreOriginalPlayerListName(player, cachedPrefix, prefixComponent);
      lastPlayerPrefixes.put(playerId, prefixComponent);
      player.playerListName(prefixComponent.append(originalName));
    } else {
      lastPlayerPrefixes.remove(playerId);
      restoreOriginalPlayerListName(player);
    }
  }

  private @NotNull Component getOrStoreOriginalPlayerListName(
      @NotNull Player player, Component activePrefix, Component applyingPrefix) {
    UUID playerId = player.getUniqueId();
    Component current = getCurrentPlayerListName(player);
    Component existing = originalPlayerListNames.get(playerId);
    if (existing != null) {
      if (activePrefix == null) {
        Component sanitized = stripPrefixIfPresent(current, applyingPrefix);
        if (sanitized != null) {
          current = sanitized;
        }
        if (!Objects.equals(existing, current)) {
          originalPlayerListNames.put(playerId, current);
          existing = current;
        }
      }
      return existing;
    }

    Component sanitized = stripKnownPrefix(current, activePrefix, applyingPrefix);
    Component toStore = sanitized != null ? sanitized : current;
    originalPlayerListNames.put(playerId, toStore);
    return toStore;
  }

  private @NotNull Component getCurrentPlayerListName(@NotNull Player player) {
    Component current = player.playerListName();
    if (current == null) {
      current = Component.text(player.getName(), NamedTextColor.WHITE);
    }
    return current;
  }

  private Component stripKnownPrefix(
      @NotNull Component current, Component activePrefix, Component applyingPrefix) {
    Component prefixToStrip = activePrefix != null ? activePrefix : applyingPrefix;
    if (prefixToStrip == null) {
      return null;
    }
    return stripPrefixIfPresent(current, prefixToStrip);
  }

  private Component stripPrefixIfPresent(@NotNull Component current, @NotNull Component prefix) {
    if (!(current instanceof TextComponent currentText) || !(prefix instanceof TextComponent prefixText)) {
      return null;
    }
    if (!Objects.equals(currentText.content(), prefixText.content())
        || !Objects.equals(currentText.style(), prefixText.style())) {
      return null;
    }
    if (current.children().isEmpty()) {
      return Component.empty();
    }
    if (current.children().size() == 1) {
      return current.children().get(0);
    }
    Component remainder = Component.empty();
    for (Component child : current.children()) {
      remainder = remainder.append(child);
    }
    return remainder;
  }

  private void restoreOriginalPlayerListName(@NotNull Player player) {
    UUID playerId = player.getUniqueId();
    Component original = originalPlayerListNames.remove(playerId);
    if (original != null) {
      player.playerListName(original);
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

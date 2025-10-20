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
import org.jetbrains.annotations.Nullable;

/** Слушатель событий, связанных с командами и чатом игроков. */
public class TeamChatListener implements Listener {

  private final TeamService teamManager;
  private final Map<UUID, Component> lastPlayerPrefixes = new ConcurrentHashMap<>();
  private final Map<UUID, Component> originalPlayerListNames = new ConcurrentHashMap<>();
  private final Map<UUID, Component> originalPlayerDisplayNames = new ConcurrentHashMap<>();
  private final Map<UUID, Component> cachedTeamPrefixes = new ConcurrentHashMap<>();
  private final Map<UUID, UUID> playerTeamIds = new ConcurrentHashMap<>();

  public TeamChatListener(@NotNull TeamService teamManager) {
    this.teamManager = teamManager;
    Bukkit.getOnlinePlayers()
        .forEach(
            player -> {
              cacheOriginalPlayerListName(player);
              getOrStoreOriginalPlayerDisplayName(player, null);
              updatePlayerPrefix(player);
            });
  }

  @EventHandler
  public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
    Player player = event.getPlayer();
    cacheOriginalPlayerListName(player);
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
    UUID playerId = player.getUniqueId();
    lastPlayerPrefixes.remove(playerId);
    originalPlayerListNames.remove(playerId);
    originalPlayerDisplayNames.remove(playerId);
    removeCachedTeamFor(playerId);
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
      Component resolvedPrefix = computePrefixForAsyncContext(player);
      if (resolvedPrefix != null) {
        lastPlayerPrefixes.put(playerId, resolvedPrefix);
      }
      Bukkit.getScheduler().runTask(teamManager.getPlugin(), () -> updatePlayerPrefix(player));
    }

    event.renderer(
        (source, sourceDisplayName, message, viewer) -> {
          Component latestPrefixComponent = lastPlayerPrefixes.get(playerId);
          Component baseDisplay = originalPlayerDisplayNames.get(playerId);
          if (baseDisplay == null) {
            Component stripped = stripKnownPrefix(sourceDisplayName, latestPrefixComponent, null);
            baseDisplay = stripped != null ? stripped : sourceDisplayName;
            originalPlayerDisplayNames.put(playerId, baseDisplay);
          }

          Component formattedMessage =
              forceWhiteChat ? message.color(NamedTextColor.WHITE) : message;
          Component nameComponent = baseDisplay;
          if (latestPrefixComponent != null) {
            nameComponent =
                latestPrefixComponent.append(baseDisplay.colorIfAbsent(NamedTextColor.WHITE));
          }
          return nameComponent
              .append(Component.text(": ", NamedTextColor.GRAY))
              .append(formattedMessage);
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
      trackTeamPrefix(player, prefix);
      ((MyPurpurPlugin) teamManager.getPlugin())
          .debug("Устанавливаем префикс для игрока " + player.getName() + ": " + prefix);
      Component originalName = cacheOriginalPlayerListName(player, prefix);
      Component originalDisplayName = getOrStoreOriginalPlayerDisplayName(player, prefix);
      lastPlayerPrefixes.put(playerId, prefix);
      player.playerListName(prefix.append(originalName.colorIfAbsent(NamedTextColor.WHITE)));
      player.displayName(prefix.append(originalDisplayName.colorIfAbsent(NamedTextColor.WHITE)));
    } else {
      ((MyPurpurPlugin) teamManager.getPlugin())
          .debug("Сбрасываем префикс для игрока " + player.getName());
      lastPlayerPrefixes.remove(playerId);
      removeCachedTeamFor(playerId);
      restoreOriginalPlayerListName(player);
      restoreOriginalPlayerDisplayName(player);
    }
  }

  /** Clears cached prefix information for all tracked players and restores their original names. */
  public void clearCachedPrefixes() {
    originalPlayerListNames.forEach(
        (playerId, originalName) -> {
          Player player = Bukkit.getPlayer(playerId);
          if (player != null) {
            player.playerListName(originalName);
          }
        });
    originalPlayerDisplayNames.forEach(
        (playerId, originalDisplay) -> {
          Player player = Bukkit.getPlayer(playerId);
          if (player != null) {
            player.displayName(originalDisplay);
          }
        });
    lastPlayerPrefixes.clear();
    originalPlayerListNames.clear();
    originalPlayerDisplayNames.clear();
    cachedTeamPrefixes.clear();
    playerTeamIds.clear();
  }

  private void updatePlayerPrefix(@NotNull Player player) {
    String teamName = teamManager.getPlayerTeam(player);
    UUID playerId = player.getUniqueId();
    ((MyPurpurPlugin) teamManager.getPlugin())
        .debugTeamAction("Обновление префикса для игрока", player.getName(), teamName);

    if (teamName != null) {
      UUID teamId = teamManager.getTeamIdByName(teamName);
      if (teamId != null) {
        playerTeamIds.put(playerId, teamId);
      }
      Component prefixComponent = resolveTeamPrefix(teamName, teamId);
      Component cachedPrefix = lastPlayerPrefixes.get(playerId);
      Component originalName = cacheOriginalPlayerListName(player, prefixComponent);
      Component originalDisplayName = getOrStoreOriginalPlayerDisplayName(player, prefixComponent);
      lastPlayerPrefixes.put(playerId, prefixComponent);
      player.playerListName(
          prefixComponent.append(originalName.colorIfAbsent(NamedTextColor.WHITE)));
      player.displayName(
          prefixComponent.append(originalDisplayName.colorIfAbsent(NamedTextColor.WHITE)));
    } else {
      lastPlayerPrefixes.remove(playerId);
      removeCachedTeamFor(playerId);
      restoreOriginalPlayerListName(player);
      restoreOriginalPlayerDisplayName(player);
    }
  }

  private Component computePrefixForAsyncContext(@NotNull Player player) {
    String teamName = teamManager.getPlayerTeam(player);
    if (teamName == null) {
      return null;
    }
    UUID teamId = teamManager.getTeamIdByName(teamName);
    if (teamId != null) {
      playerTeamIds.put(player.getUniqueId(), teamId);
    }
    return resolveTeamPrefix(teamName, teamId);
  }

  private void trackTeamPrefix(@NotNull Player player, @NotNull Component prefix) {
    UUID playerId = player.getUniqueId();
    UUID teamId = playerTeamIds.get(playerId);
    if (teamId == null) {
      String teamName = teamManager.getPlayerTeam(player);
      if (teamName != null) {
        teamId = teamManager.getTeamIdByName(teamName);
        if (teamId != null) {
          playerTeamIds.put(playerId, teamId);
        }
      }
    }
    if (teamId != null) {
      cachedTeamPrefixes.put(teamId, prefix);
    }
  }

  private void removeCachedTeamFor(@NotNull UUID playerId) {
    UUID teamId = playerTeamIds.remove(playerId);
    if (teamId != null) {
      cachedTeamPrefixes.remove(teamId);
    }
  }

  private Component resolveTeamPrefix(String teamName, UUID teamId) {
    if (teamName == null) {
      return Component.empty();
    }
    if (teamId != null) {
      Component cached = cachedTeamPrefixes.get(teamId);
      if (cached != null) {
        return cached;
      }
    }
    String prefix = teamManager.getTeamPrefix(teamName);
    NamedTextColor teamColor = teamManager.getTeamColor(teamName);
    Component resolved = TeamUtils.createPrefixComponent(prefix, teamColor);
    if (teamId != null) {
      cachedTeamPrefixes.put(teamId, resolved);
    }
    return resolved;
  }

  private @NotNull Component cacheOriginalPlayerListName(
      @NotNull Player player, @Nullable Component applyingPrefix) {
    UUID playerId = player.getUniqueId();
    Component existing = originalPlayerListNames.get(playerId);
    if (existing != null) {
      return existing;
    }

    Component current = getCurrentPlayerListName(player);
    Component sanitized =
        stripKnownPrefix(current, lastPlayerPrefixes.get(playerId), applyingPrefix);
    Component toStore = sanitized != null ? sanitized : current;
    originalPlayerListNames.put(playerId, toStore);
    return toStore;
  }

  private @NotNull Component cacheOriginalPlayerListName(@NotNull Player player) {
    return cacheOriginalPlayerListName(player, null);
  }

  private @NotNull Component getCurrentPlayerListName(@NotNull Player player) {
    Component current = player.playerListName();
    if (current != null) {
      return current;
    }

    Component displayName = player.displayName();
    if (displayName != null) {
      return displayName;
    }

    return Component.text(player.getName());
  }

  private @NotNull Component getOrStoreOriginalPlayerDisplayName(
      @NotNull Player player, @Nullable Component applyingPrefix) {
    UUID playerId = player.getUniqueId();
    Component existing = originalPlayerDisplayNames.get(playerId);
    if (existing != null) {
      return existing;
    }

    Component current = getCurrentPlayerDisplayName(player);
    Component sanitized =
        stripKnownPrefix(current, lastPlayerPrefixes.get(playerId), applyingPrefix);
    Component toStore = sanitized != null ? sanitized : current;
    originalPlayerDisplayNames.put(playerId, toStore);
    return toStore;
  }

  private @NotNull Component getCurrentPlayerDisplayName(@NotNull Player player) {
    Component displayName = player.displayName();
    if (displayName != null && !isSimplePlayerName(displayName, player)) {
      return displayName;
    }

    Component currentListName = player.playerListName();
    if (currentListName != null) {
      return currentListName;
    }

    if (displayName != null) {
      return displayName;
    }

    return Component.text(player.getName());
  }

  private @Nullable Component stripKnownPrefix(
      @NotNull Component current,
      @Nullable Component activePrefix,
      @Nullable Component applyingPrefix) {
    Component prefixToStrip = activePrefix != null ? activePrefix : applyingPrefix;
    if (prefixToStrip == null) {
      return null;
    }
    return stripPrefixIfPresent(current, prefixToStrip);
  }

  private @Nullable Component stripPrefixIfPresent(
      @NotNull Component current, @NotNull Component prefix) {
    if (!(current instanceof TextComponent currentText)
        || !(prefix instanceof TextComponent prefixText)) {
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
      return current.children().getFirst();
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

  private void restoreOriginalPlayerDisplayName(@NotNull Player player) {
    UUID playerId = player.getUniqueId();
    Component original = originalPlayerDisplayNames.remove(playerId);
    if (original != null) {
      player.displayName(original);
    }
  }

  private boolean isSimplePlayerName(@NotNull Component component, @NotNull Player player) {
    Component plainName = Component.text(player.getName());
    Component whiteName = Component.text(player.getName(), NamedTextColor.WHITE);
    return component.equals(plainName) || component.equals(whiteName);
  }

  /** Внутренний класс для события обновления префикса игрока. */
  public static class PlayerPrefixUpdateEvent extends org.bukkit.event.Event {
    private static final org.bukkit.event.HandlerList HANDLERS = new org.bukkit.event.HandlerList();

    private final Player player;
    private final Component prefix;

    public PlayerPrefixUpdateEvent(@NotNull Player player, @Nullable Component prefix) {
      this.player = player;
      this.prefix = prefix;
    }

    public @NotNull Player getPlayer() {
      return player;
    }

    public @Nullable Component getPrefix() {
      return prefix;
    }

    @Override
    public @NotNull org.bukkit.event.HandlerList getHandlers() {
      return HANDLERS;
    }

    public static @NotNull org.bukkit.event.HandlerList getHandlerList() {
      return HANDLERS;
    }
  }
}

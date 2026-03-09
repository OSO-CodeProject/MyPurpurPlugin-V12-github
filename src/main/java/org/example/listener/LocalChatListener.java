package org.example.listener;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Iterator;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.example.chat.ObfuscationUtils;
import org.example.config.PluginConfig;
import org.jetbrains.annotations.NotNull;

/** Слушатель для реализации локального чата. */
public class LocalChatListener implements Listener {

  private final PluginConfig pluginConfig;

  public LocalChatListener(@NotNull PluginConfig pluginConfig) {
    this.pluginConfig = pluginConfig;
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void onPlayerChat(@NotNull AsyncChatEvent event) {
    if (!pluginConfig.isLocalChatEnabled()) {
      return;
    }

    Player sender = event.getPlayer();
    String plainMessage = PlainTextComponentSerializer.plainText().serialize(event.message());

    double radius = pluginConfig.getTalkRadius();
    double falloff = pluginConfig.getTalkFalloff();
    String formatPrefix = pluginConfig.getTalkFormat();
    String usedPrefix = "";

    String screamPrefix = pluginConfig.getScreamPrefix();
    String yellPrefix = pluginConfig.getYellPrefix();
    String whisperPrefix = pluginConfig.getWhisperPrefix();

    if (screamPrefix != null && !screamPrefix.isEmpty() && plainMessage.startsWith(screamPrefix)) {
      radius = pluginConfig.getScreamRadius();
      falloff = pluginConfig.getScreamFalloff();
      formatPrefix = pluginConfig.getScreamFormat();
      usedPrefix = screamPrefix;
    } else if (yellPrefix != null && !yellPrefix.isEmpty() && plainMessage.startsWith(yellPrefix)) {
      radius = pluginConfig.getYellRadius();
      falloff = pluginConfig.getYellFalloff();
      formatPrefix = pluginConfig.getYellFormat();
      usedPrefix = yellPrefix;
    } else if (whisperPrefix != null
        && !whisperPrefix.isEmpty()
        && plainMessage.startsWith(whisperPrefix)) {
      radius = pluginConfig.getWhisperRadius();
      falloff = pluginConfig.getWhisperFalloff();
      formatPrefix = pluginConfig.getWhisperFormat();
      usedPrefix = whisperPrefix;
    }

    if (!usedPrefix.isEmpty()) {
      plainMessage = plainMessage.substring(usedPrefix.length()).trim();
      event.message(Component.text(plainMessage));
    }

    final double finalRadius = radius;
    final double finalFalloff = falloff;
    final String finalFormatPrefix = formatPrefix;
    final String finalPlainMessage = plainMessage;

    // Фильтруем получателей сообщения
    Iterator<Audience> iterator = event.viewers().iterator();
    while (iterator.hasNext()) {
      Audience audience = iterator.next();
      if (audience instanceof Player viewer) {
        if (!viewer.getWorld().equals(sender.getWorld())) {
          iterator.remove();
          continue;
        }
        double distance = sender.getLocation().distance(viewer.getLocation());
        if (distance >= finalRadius + finalFalloff) {
          iterator.remove();
        }
      }
    }

    ChatRenderer existingRenderer = event.renderer();

    event.renderer(
        (source, sourceDisplayName, message, viewer) -> {
          Component finalMessageComp = message;

          if (viewer instanceof Player playerViewer
              && pluginConfig.isLocalChatObfuscationEnabled()) {
            double distance = sender.getLocation().distance(playerViewer.getLocation());
            if (distance > finalRadius) {
              String obf =
                  ObfuscationUtils.obfuscate(
                      finalPlainMessage,
                      distance,
                      finalRadius,
                      finalFalloff,
                      pluginConfig.getLocalChatObfuscationChars());
              if (obf == null) {
                obf = ""; // Теоретически сюда не дойдет из-за фильтрации viewers
              }
              finalMessageComp = Component.text(obf);
            }
          }

          Component rendered =
              existingRenderer.render(source, sourceDisplayName, finalMessageComp, viewer);

          if (finalFormatPrefix != null && !finalFormatPrefix.isEmpty()) {
            return Component.text(finalFormatPrefix + " ", NamedTextColor.GRAY).append(rendered);
          }
          return rendered;
        });
  }
}

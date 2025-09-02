package org.example.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.example.config.PluginConfig;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Обработчик команды /menu для открытия меню игрокам.
 */
public class MenuCommand implements org.bukkit.command.CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final PluginConfig pluginConfig;

    public MenuCommand(@NotNull JavaPlugin plugin, @NotNull PluginConfig pluginConfig) {
        this.plugin = plugin;
        this.pluginConfig = pluginConfig;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("❌ Эту команду может использовать только игрок!", NamedTextColor.RED));
            return true;
        }

        // Проверяем, есть ли лишние аргументы
        if (args.length > 0) {
            player.sendMessage(Component.text("❌ Команда /menu не принимает аргументов! Использование: /menu", NamedTextColor.RED));
            return false; // Возвращаем false, чтобы показать правильное использование
        }

        openMenu(player);
        return true;
    }

    @SuppressWarnings("deprecation")
    private void openMenu(@NotNull Player player) {
        Inventory menu = plugin.getServer().createInventory(null, 9, Component.text("Меню"));

        ItemStack exampleItem = new ItemStack(Material.DIAMOND);
        ItemMeta meta = exampleItem.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Пример предмета"));
            exampleItem.setItemMeta(meta);
        }

        menu.setItem(4, exampleItem);

        player.openInventory(menu);

        // Получаем звук из конфига с обработкой ошибок
        Sound sound;
        try {
            sound = Sound.valueOf(pluginConfig.getMenuOpenSound().toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Некорректный звук в конфиге: " + pluginConfig.getMenuOpenSound() + ". Используется BLOCK_NOTE_BLOCK_PLING по умолчанию.");
            sound = Sound.BLOCK_NOTE_BLOCK_PLING;
        }

        player.playSound(
                player.getLocation(),
                sound,
                SoundCategory.MASTER,
                (float) pluginConfig.getMenuSoundVolume(),
                (float) pluginConfig.getMenuSoundPitch()
        );

        // Получаем частицу из конфига с обработкой ошибок
        Particle particle;
        try {
            particle = Particle.valueOf(pluginConfig.getMenuParticleEffect().toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Некорректная частица в конфиге: " + pluginConfig.getMenuParticleEffect() + ". Используется FIREWORK по умолчанию.");
            particle = Particle.FIREWORK;
        }

        player.getWorld().spawnParticle(
                particle,
                player.getLocation().add(0.5, 1.0, 0.5),
                30, 0.5, 0.5, 0.5, 0.1
        );
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        // Отключаем автодополнение для команды /menu
        return Collections.emptyList();
    }
}
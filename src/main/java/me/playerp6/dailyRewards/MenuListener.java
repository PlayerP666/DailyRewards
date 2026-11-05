package me.playerp6.dailyRewards;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

public record MenuListener(DailyRewards plugin) implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof DailyMenu menu)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) return;

        if (menu.isDonateMenu()) {
            if (slot == menu.getDonateSlot()) claimDonate(player, menu);
            return;
        }

        int day = menu.getDayBySlot(slot);
        if (day > 0) claimDay(player, menu, day);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof DailyMenu) {
            event.setCancelled(true);
        }
    }

    private void claimDay(Player player, DailyMenu menu, int day) {
        UUID id = player.getUniqueId();
        PlayerDataManager data = plugin.getDataManager();
        Messages msg = plugin.getMessages();
        FileConfiguration cfg = plugin.getConfig();
        String dayText = String.valueOf(day);

        if (plugin.checkReset(player)) {
            menu.render();
            return;
        }

        String time = msg.formatTime(plugin.getTimeLeft(id));

        switch (plugin.getState(id, day)) {
            case CLAIMED -> error(player, "chat.already", dayText, time);
            case LOCKED -> error(player, "chat.locked", dayText, time);
            case WAITING -> error(player, "chat.wait", dayText, time);
            case AVAILABLE -> {
                if (!cfg.getBoolean("settings.full-inventory-drop", true)
                        && player.getInventory().firstEmpty() == -1) {
                    error(player, "chat.inventory-full", dayText, time);
                    return;
                }

                giveItems(player, day);
                runCommands(player, cfg.getStringList("days." + day + ".commands"), dayText);

                data.setNextDay(id, day + 1);
                data.setLastClaim(id, System.currentTimeMillis());
                data.save();

                msg.send(player, "chat.claimed", "%day%", dayText);
                plugin.playSound(player, "claim");
                if (cfg.getBoolean("titles.claim-enabled", true)) {
                    plugin.showTitle(player, "titles.claim", "%day%", dayText);
                }

                if (day == plugin.getTotalDays()) {
                    // Последний день — сразу открываем меню доната
                    Bukkit.getScheduler().runTask(plugin, () ->
                            player.openInventory(new DailyMenu(plugin, player, true).getInventory()));
                } else if (cfg.getBoolean("settings.close-menu-on-claim", false)) {
                    Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
                } else {
                    menu.render();
                }
            }
        }
    }

    private void claimDonate(Player player, DailyMenu menu) {
        UUID id = player.getUniqueId();
        PlayerDataManager data = plugin.getDataManager();
        FileConfiguration cfg = plugin.getConfig();

        if (data.isFinished(id)) return;

        boolean restart = cfg.getBoolean("settings.restart-after-finish", false);

        // Сначала сохраняем, чтобы двойной клик не выдал донат дважды
        if (restart) {
            data.setNextDay(id, 1);
            data.setLastClaim(id, System.currentTimeMillis());
        } else {
            data.setFinished(id, true);
        }
        data.save();

        runCommands(player, cfg.getStringList("completion.commands"), "");

        plugin.getMessages().send(player, "chat.complete");
        plugin.playSound(player, "complete");
        if (cfg.getBoolean("titles.complete-enabled", true)) {
            plugin.showTitle(player, "titles.complete");
        }

        if (restart) {
            Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
        } else {
            menu.render(); // кнопка "тухнет"
        }
    }

    private void giveItems(Player player, int day) {
        for (String reward : plugin.getConfig().getStringList("days." + day + ".items")) {
            String[] parts = reward.split(":");

            Material material = Material.matchMaterial(parts[0]);
            if (material == null) {
                plugin.getLogger().warning("Неизвестный предмет в конфиге: " + parts[0]);
                continue;
            }

            int amount = 1;
            if (parts.length > 1) {
                try {
                    amount = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Неверное количество в конфиге: " + reward);
                }
            }

            player.getInventory().addItem(new ItemStack(material, amount)).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
    }

    private void runCommands(Player player, List<String> commands, String day) {
        for (String cmd : commands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    cmd.replace("%player%", player.getName()).replace("%day%", day));
        }
    }

    private void error(Player player, String path, String day, String time) {
        plugin.getMessages().send(player, path, "%day%", day, "%time%", time);
        plugin.playSound(player, "error");
    }
}
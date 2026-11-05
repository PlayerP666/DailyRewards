package me.playerp6.dailyRewards;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DailyMenu implements InventoryHolder {

    private final DailyRewards plugin;
    private final Messages messages;
    private final Player player;
    private final boolean donateMenu;
    private final Inventory inventory;
    private final List<Integer> daySlots;
    private final int donateSlot;

    public DailyMenu(DailyRewards plugin, Player player, boolean donateMenu) {
        this.plugin = plugin;
        this.messages = plugin.getMessages();
        this.player = player;
        this.donateMenu = donateMenu;

        FileConfiguration cfg = plugin.getConfig();
        int rows = Math.max(1, Math.min(6, cfg.getInt("menu.rows", 3)));
        int size = rows * 9;

        this.daySlots = cfg.getIntegerList("menu.day-slots");
        int slot = cfg.getInt("menu.donate-slot", 13);
        this.donateSlot = (slot >= 0 && slot < size) ? slot : size / 2;

        String title = messages.get(donateMenu ? "menu.donate-title" : "menu.title");
        this.inventory = Bukkit.createInventory(this, size, Text.color(title));
        render();
    }

    // Полная отрисовка: стекло + дни (или кнопка доната)
    public void render() {
        inventory.clear();
        FileConfiguration cfg = plugin.getConfig();

        if (cfg.getBoolean("menu.filler.enabled", true)) {
            Material filler = material(cfg.getString("menu.filler.material"), Material.GRAY_STAINED_GLASS_PANE);
            ItemStack item = createItem(filler, 1, messages.get("menu.filler-name"), List.of(), false);
            for (int i = 0; i < inventory.getSize(); i++) {
                inventory.setItem(i, item);
            }
        }

        if (donateMenu) renderDonate();
        else renderDays();
    }

    // Обновляет только дни, без перерисовки стекла (чтобы меню не мерцало)
    public void refresh() {
        if (!donateMenu) {
            renderDays();
        }
    }

    private void renderDays() {
        FileConfiguration cfg = plugin.getConfig();
        UUID id = player.getUniqueId();
        String time = messages.formatTime(plugin.getTimeLeft(id));
        boolean dayAsAmount = cfg.getBoolean("menu.day-number-as-amount", true);

        for (int day = 1; day <= plugin.getTotalDays(); day++) {
            int slot = daySlots.get(day - 1);
            if (slot < 0 || slot >= inventory.getSize()) continue;

            DailyRewards.DayState state = plugin.getState(id, day);
            String key = state.name().toLowerCase(); // available, claimed, waiting, locked

            Material material = material(cfg.getString("states." + key + ".material"), Material.STONE);
            boolean glow = cfg.getBoolean("states." + key + ".glow", false);

            String dayText = String.valueOf(day);
            String name = Messages.apply(messages.get("items." + key + ".name"),
                    "%day%", dayText, "%time%", time);

            List<String> lore = new ArrayList<>();
            List<String> rewards = cfg.getStringList("days." + day + ".display");
            for (String line : messages.getList("items." + key + ".lore")) {
                if (line.contains("%rewards%")) {
                    for (String reward : rewards) {
                        lore.add(line.replace("%rewards%", reward));
                    }
                } else {
                    lore.add(Messages.apply(line, "%day%", dayText, "%time%", time));
                }
            }

            int amount = dayAsAmount ? Math.min(day, 64) : 1;
            inventory.setItem(slot, createItem(material, amount, name, lore, glow));
        }
    }

    private void renderDonate() {
        FileConfiguration cfg = plugin.getConfig();
        boolean finished = plugin.getDataManager().isFinished(player.getUniqueId());

        String cfgKey = finished ? "completion.button-used" : "completion.button";
        String msgKey = finished ? "items.donate-used" : "items.donate-button";

        Material material = material(cfg.getString(cfgKey + ".material"),
                finished ? Material.GRAY_CONCRETE : Material.NETHER_STAR);
        boolean glow = cfg.getBoolean(cfgKey + ".glow", false);

        inventory.setItem(donateSlot, createItem(material, 1,
                messages.get(msgKey + ".name"), messages.getList(msgKey + ".lore"), glow));
    }

    // Какой день стоит в этом слоте (-1, если никакой)
    public int getDayBySlot(int slot) {
        int index = daySlots.indexOf(slot);
        if (index < 0 || index >= plugin.getTotalDays()) return -1;
        return index + 1;
    }

    private Material material(String name, Material fallback) {
        if (name == null) return fallback;
        Material material = Material.matchMaterial(name);
        if (material == null) {
            plugin.getLogger().warning("Неизвестный материал в конфиге: " + name);
            return fallback;
        }
        return material;
    }

    private ItemStack createItem(Material material, int amount, String name, List<String> lore, boolean glow) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Text.color(name));

        List<Component> lines = new ArrayList<>();
        for (String line : lore) {
            lines.add(Text.color(line));
        }
        meta.lore(lines);

        if (glow) meta.setEnchantmentGlintOverride(true);

        item.setItemMeta(meta);
        return item;
    }

    public boolean isDonateMenu() { return donateMenu; }
    public int getDonateSlot() { return donateSlot; }

    @Override
    public @NotNull Inventory getInventory() { return inventory; }
}
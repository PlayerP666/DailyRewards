package me.playerp6.dailyRewards;

import net.kyori.adventure.title.Title;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.UUID;

public final class DailyRewards extends JavaPlugin {

    public enum DayState { AVAILABLE, CLAIMED, WAITING, LOCKED }

    private PlayerDataManager dataManager;
    private Messages messages;
    private BukkitTask updateTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new Messages(this);
        dataManager = new PlayerDataManager(this);

        DailyCommand dailyCommand = new DailyCommand(this);
        PluginCommand command = getCommand("daily");
        if (command != null) {
            command.setExecutor(dailyCommand);
            command.setTabCompleter(dailyCommand);
        }

        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        getServer().getPluginManager().registerEvents(new JoinListener(this), this);

        startUpdateTask();

        getLogger().info("DailyRewards включён! Дней: " + getTotalDays());
    }

    @Override
    public void onDisable() {
        if (updateTask != null) updateTask.cancel();
        if (dataManager != null) dataManager.save();
    }

    public void reloadAll() {
        reloadConfig();
        messages.reload();
        startUpdateTask();
    }

    // Каждые N тиков обновляет меню у всех, кто его открыл
    private void startUpdateTask() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }

        if (!getConfig().getBoolean("menu.auto-update.enabled", true)) return;

        long interval = Math.max(1, getConfig().getLong("menu.auto-update.interval-ticks", 20));

        updateTask = getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player player : getServer().getOnlinePlayers()) {
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof DailyMenu menu
                        && !menu.isDonateMenu()) {
                    checkReset(player); // если серия сгорела прямо в открытом меню
                    menu.refresh();
                }
            }
        }, interval, interval);
    }

    public PlayerDataManager getDataManager() { return dataManager; }
    public Messages getMessages() { return messages; }

    // Сколько всего дней (по конфигу, но не больше количества слотов)
    public int getTotalDays() {
        ConfigurationSection section = getConfig().getConfigurationSection("days");
        int days = section == null ? 0 : section.getKeys(false).size();
        int slots = getConfig().getIntegerList("menu.day-slots").size();
        return Math.min(days, slots);
    }

    public long getCooldownMillis() {
        return (long) (getConfig().getDouble("settings.cooldown-hours", 24) * 3_600_000);
    }

    public long getResetMillis() {
        return (long) (getConfig().getDouble("settings.reset-after-hours", 24) * 3_600_000);
    }

    public long getTimeLeft(UUID id) {
        long passed = System.currentTimeMillis() - dataManager.getLastClaim(id);
        return getCooldownMillis() - passed;
    }

    // Сбрасывает серию за пропуск. true — если сброс был
    public boolean checkReset(Player player) {
        if (!getConfig().getBoolean("settings.reset-enabled", true)) return false;

        UUID id = player.getUniqueId();
        int next = dataManager.getNextDay(id);
        long last = dataManager.getLastClaim(id);

        if (next < 2 || next > getTotalDays() || last == 0) return false;

        long passed = System.currentTimeMillis() - last;
        if (passed <= getCooldownMillis() + getResetMillis()) return false;

        dataManager.setNextDay(id, 1);
        dataManager.setLastClaim(id, 0);
        dataManager.save();
        messages.send(player, "chat.reset");
        playSound(player, "error");
        return true;
    }

    public DayState getState(UUID id, int day) {
        int next = dataManager.getNextDay(id);
        if (day < next) return DayState.CLAIMED;
        if (day > next) return DayState.LOCKED;
        if (getTimeLeft(id) <= 0) return DayState.AVAILABLE;
        return DayState.WAITING;
    }

    public void playSound(Player player, String key) {
        if (!getConfig().getBoolean("sounds.enabled", true)) return;

        String sound = getConfig().getString("sounds." + key + ".sound", "");
        if (sound.isEmpty()) return;

        float volume = (float) getConfig().getDouble("sounds." + key + ".volume", 1.0);
        float pitch = (float) getConfig().getDouble("sounds." + key + ".pitch", 1.0);
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    // path — например "titles.claim" в messages.yml
    public void showTitle(Player player, String path, String... replacements) {
        String title = Messages.apply(messages.get(path + ".title"), replacements);
        String subtitle = Messages.apply(messages.get(path + ".subtitle"), replacements);

        Title.Times times = Title.Times.times(
                ticks("titles.fade-in", 10),
                ticks("titles.stay", 60),
                ticks("titles.fade-out", 20));

        player.showTitle(Title.title(Text.color(title), Text.color(subtitle), times));
    }

    private Duration ticks(String path, int def) {
        return Duration.ofMillis(getConfig().getLong(path, def) * 50);
    }
}
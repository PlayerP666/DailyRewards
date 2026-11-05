package me.playerp6.dailyRewards;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;

public record JoinListener(DailyRewards plugin) implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("join-notify.enabled", true)) return;

        Player player = event.getPlayer();
        long delay = plugin.getConfig().getLong("join-notify.delay-seconds", 3) * 20;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || !player.hasPermission("daily.use")) return;

            UUID id = player.getUniqueId();
            PlayerDataManager data = plugin.getDataManager();
            if (data.isFinished(id)) return;

            plugin.checkReset(player);

            int next = data.getNextDay(id);
            boolean donateReady = next > plugin.getTotalDays();
            boolean dayReady = !donateReady
                    && plugin.getState(id, next) == DailyRewards.DayState.AVAILABLE;

            if (donateReady || dayReady) {
                plugin.getMessages().send(player, "chat.join-available");
            }
        }, delay);
    }
}
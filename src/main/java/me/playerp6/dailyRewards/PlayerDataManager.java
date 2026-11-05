package me.playerp6.dailyRewards;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class PlayerDataManager {

    private final DailyRewards plugin;
    private final File file;
    private final YamlConfiguration data;

    public PlayerDataManager(DailyRewards plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public int getNextDay(UUID id) { return data.getInt(id + ".day", 1); }
    public void setNextDay(UUID id, int day) { data.set(id + ".day", day); }

    public long getLastClaim(UUID id) { return data.getLong(id + ".last-claim", 0); }
    public void setLastClaim(UUID id, long time) { data.set(id + ".last-claim", time); }

    public boolean isFinished(UUID id) { return data.getBoolean(id + ".finished", false); }
    public void setFinished(UUID id, boolean finished) { data.set(id + ".finished", finished); }

    // Полностью удаляет прогресс игрока
    public void reset(UUID id) { data.set(id.toString(), null); }

    public void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Не удалось сохранить data.yml: " + e.getMessage());
        }
    }
}
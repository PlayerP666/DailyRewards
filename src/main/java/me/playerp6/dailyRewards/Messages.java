package me.playerp6.dailyRewards;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Messages {

    private final DailyRewards plugin;
    private final File file;
    private YamlConfiguration config;

    public Messages(DailyRewards plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "messages.yml");
        reload();
    }

    public void reload() {
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    private String prefix() {
        return config.getString("prefix", "");
    }

    public String get(String path) {
        return config.getString(path, "").replace("%prefix%", prefix());
    }

    public List<String> getList(String path) {
        List<String> result = new ArrayList<>();
        for (String line : config.getStringList(path)) {
            result.add(line.replace("%prefix%", prefix()));
        }
        return result;
    }

    // send(player, "chat.claimed", "%day%", "3")
    public void send(CommandSender to, String path, String... replacements) {
        String text = get(path);
        if (text.isEmpty()) return; // пустое сообщение = отключено
        to.sendMessage(Text.color(apply(text, replacements)));
    }

    public void sendList(CommandSender to, String path) {
        for (String line : getList(path)) {
            to.sendMessage(Text.color(line));
        }
    }

    // Заменяет пары: "%day%", "3", "%time%", "5 мин"
    public static String apply(String text, String... replacements) {
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace(replacements[i], replacements[i + 1]);
        }
        return text;
    }

    public String formatTime(long millis) {
        long totalSeconds = Math.max(0, millis / 1000);
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;

        String format;
        if (h > 0) format = get("time.hours");
        else if (m > 0) format = get("time.minutes");
        else format = get("time.seconds");

        return apply(format,
                "%h%", String.valueOf(h),
                "%m%", String.valueOf(m),
                "%s%", String.valueOf(s));
    }
}
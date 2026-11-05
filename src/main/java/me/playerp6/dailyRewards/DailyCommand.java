package me.playerp6.dailyRewards;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record DailyCommand(DailyRewards plugin) implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        Messages msg = plugin.getMessages();

        if (args.length == 0) {
            openMenu(sender);
            return true;
        }

        if (!sender.hasPermission("daily.admin")) {
            msg.send(sender, "chat.no-permission");
            return true;
        }

        PlayerDataManager data = plugin.getDataManager();

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadAll();
                msg.send(sender, "chat.reload");
            }
            case "reset" -> {
                if (args.length < 2) {
                    msg.sendList(sender, "chat.usage");
                    return true;
                }
                OfflinePlayer target = findPlayer(args[1]);
                if (target == null) {
                    msg.send(sender, "chat.player-not-found", "%player%", args[1]);
                    return true;
                }
                data.reset(target.getUniqueId());
                data.save();
                msg.send(sender, "chat.reset-player", "%player%", args[1]);
            }
            case "setday" -> {
                if (args.length < 3) {
                    msg.sendList(sender, "chat.usage");
                    return true;
                }
                OfflinePlayer target = findPlayer(args[1]);
                if (target == null) {
                    msg.send(sender, "chat.player-not-found", "%player%", args[1]);
                    return true;
                }

                int max = plugin.getTotalDays() + 1; // +1 = сразу кнопка доната
                int day;
                try {
                    day = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    day = -1;
                }
                if (day < 1 || day > max) {
                    msg.send(sender, "chat.invalid-day", "%max%", String.valueOf(max));
                    return true;
                }

                UUID id = target.getUniqueId();
                data.setNextDay(id, day);
                data.setLastClaim(id, 0); // день доступен сразу
                data.setFinished(id, false);
                data.save();
                msg.send(sender, "chat.setday-player", "%player%", args[1], "%day%", String.valueOf(day));
            }
            default -> msg.sendList(sender, "chat.usage");
        }
        return true;
    }

    private void openMenu(CommandSender sender) {
        Messages msg = plugin.getMessages();

        if (!(sender instanceof Player player)) {
            msg.send(sender, "chat.only-players");
            return;
        }
        if (!player.hasPermission("daily.use")) {
            msg.send(player, "chat.no-permission");
            return;
        }

        UUID id = player.getUniqueId();
        PlayerDataManager data = plugin.getDataManager();

        if (data.isFinished(id)) {
            msg.send(player, "chat.finished");
            plugin.playSound(player, "error");
            return;
        }

        plugin.checkReset(player);

        boolean donateMenu = data.getNextDay(id) > plugin.getTotalDays();
        player.openInventory(new DailyMenu(plugin, player, donateMenu).getInventory());
        plugin.playSound(player, "open");
    }

    private OfflinePlayer findPlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        return Bukkit.getOfflinePlayerIfCached(name);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (!sender.hasPermission("daily.admin")) return List.of();

        if (args.length == 1) {
            List<String> result = new ArrayList<>();
            for (String sub : List.of("reload", "reset", "setday")) {
                if (sub.startsWith(args[0].toLowerCase())) result.add(sub);
            }
            return result;
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("reset") || args[0].equalsIgnoreCase("setday"))) {
            return null; // null = подсказка ников онлайн-игроков
        }

        return List.of();
    }
}
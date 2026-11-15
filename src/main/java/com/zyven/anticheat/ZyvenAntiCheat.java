package com.zyven.anticheat;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ZyvenAntiCheat extends JavaPlugin implements Listener {
    private final Map<UUID, PlayerData> data = new ConcurrentHashMap<>();
    private final Set<UUID> alertsOff = new HashSet<>();
    private AntiEsp esp;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(new MovementCheck(this), this);
        Bukkit.getPluginManager().registerEvents(new MiningCheck(this), this);
        esp = new AntiEsp(this);
        if (getConfig().getBoolean("anti-esp.enabled", true)) {
            esp.runTaskTimer(this, 20L, Math.max(1, getConfig().getLong("anti-esp.interval-ticks", 5)));
        }
        Bukkit.getScheduler().runTaskTimer(this, () -> data.values().forEach(d ->
                d.vl.replaceAll((k, v) -> Math.max(0, v - 0.5))), 200L, 200L);
        getLogger().info("ZyvenAntiCheat enabled.");
    }

    @Override
    public void onDisable() {
        if (esp != null) esp.cleanup();
    }

    public PlayerData data(Player p) {
        return data.computeIfAbsent(p.getUniqueId(), k -> new PlayerData());
    }

    public boolean enabled(String check) {
        return getConfig().getBoolean("checks." + check + ".enabled", true);
    }

    public boolean exempt(Player p) {
        GameMode gm = p.getGameMode();
        return p.hasPermission("zyven.bypass") || gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR
                || p.getAllowFlight() || p.isFlying() || p.isDead();
    }

    public void flag(Player p, String check, double add, String info) {
        PlayerData d = data(p);
        double vl = d.vl.merge(check, add, Double::sum);
        String msg = ChatColor.translateAlternateColorCodes('&', getConfig().getString("alert-format", "%player% %check% %vl%")
                .replace("%player%", p.getName()).replace("%check%", check)
                .replace("%vl%", String.format("%.1f", vl)).replace("%info%", info));
        for (Player s : Bukkit.getOnlinePlayers()) {
            if (s.hasPermission("zyven.alerts") && !alertsOff.contains(s.getUniqueId())) s.sendMessage(msg);
        }
        getLogger().info(ChatColor.stripColor(msg));
        double limit = getConfig().getDouble("checks." + check + ".kick-vl", 15);
        if (vl >= limit) {
            d.vl.put(check, 0.0);
            String cmd = getConfig().getString("punish-command", "kick %player%")
                    .replace("%player%", p.getName()).replace("%check%", check);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        PlayerData d = data.remove(e.getPlayer().getUniqueId());
        if (d != null && esp != null) esp.removeDecoy(d);
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 0) { s.sendMessage("§b/zac <alerts|vl <player>|reload>"); return true; }
        switch (a[0].toLowerCase()) {
            case "alerts" -> {
                if (!(s instanceof Player p)) return true;
                if (!alertsOff.remove(p.getUniqueId())) alertsOff.add(p.getUniqueId());
                s.sendMessage("§bAlerts " + (alertsOff.contains(p.getUniqueId()) ? "§coff" : "§aon"));
            }
            case "vl" -> {
                Player t = a.length > 1 ? Bukkit.getPlayer(a[1]) : null;
                if (t == null) { s.sendMessage("§cPlayer not found."); return true; }
                s.sendMessage("§b" + t.getName() + " §7VL: §f" + data(t).vl);
            }
            case "reload" -> { reloadConfig(); s.sendMessage("§aConfig reloaded."); }
            default -> s.sendMessage("§cUnknown subcommand.");
        }
        return true;
    }
                                     }

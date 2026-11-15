package com.zyven.anticheat;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Anti base-finder / block ESP / storage ESP / chunk scanner bait.
 * Fake valuable blocks are sent ONLY to one player's client, buried inside solid rock (never reachable
 * by legit exploring). Cheat clients that scan blocks light them up. Mining toward one = flagged.
 */
public class BaitBlocks extends BukkitRunnable implements Listener {
    private static final Material[] TYPES = {
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE, Material.ANCIENT_DEBRIS,
            Material.SPAWNER, Material.CHEST, Material.ENDER_CHEST
    };
    private final ZyvenAntiCheat ac;

    public BaitBlocks(ZyvenAntiCheat ac) { this.ac = ac; }

    @Override
    public void run() {
        if (!ac.getConfig().getBoolean("bait.enabled", true) || !ac.enabled("baitesp")) return;
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("zyven.bypass") || p.getGameMode() != org.bukkit.GameMode.SURVIVAL) continue;
            PlayerData d = ac.data(p);
            if (d.bait != null && (now - d.baitTime > 90000 || !d.bait.getWorld().equals(p.getWorld())
                    || d.bait.distanceSquared(p.getLocation()) > 40 * 40)) clear(p, d);
            if (d.bait == null && p.getLocation().getY() < 50) place(p, d, now);
        }
    }

    private static boolean loaded(World w, int x, int z) { return w.isChunkLoaded(x >> 4, z >> 4); }

    private static boolean buried(World w, int x, int y, int z) {
        int[][] n = {{0, 0, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] o : n) {
            if (!loaded(w, x + o[0], z + o[2])) return false;
            Material m = w.getBlockAt(x + o[0], y + o[1], z + o[2]).getType();
            if (!m.isSolid() || !m.isOccluding()) return false;
        }
        return true;
    }

    private void place(Player p, PlayerData d, long now) {
        World w = p.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location pl = p.getLocation();
        for (int i = 0; i < 20; i++) {
            double ang = r.nextDouble(Math.PI * 2), dist = 7 + r.nextDouble() * 12;
            int x = (int) Math.floor(pl.getX() + Math.cos(ang) * dist);
            int z = (int) Math.floor(pl.getZ() + Math.sin(ang) * dist);
            int y = (int) pl.getY() + r.nextInt(-8, 9);
            if (y < w.getMinHeight() + 2 || y > w.getMaxHeight() - 2) continue;
            if (!buried(w, x, y, z)) continue;
            Location loc = new Location(w, x, y, z);
            p.sendBlockChange(loc, TYPES[r.nextInt(TYPES.length)].createBlockData());
            d.bait = loc;
            d.baitTime = now;
            return;
        }
    }

    private void clear(Player p, PlayerData d) {
        Location b = d.bait;
        d.bait = null;
        if (b != null && b.getWorld() != null && loaded(b.getWorld(), b.getBlockX(), b.getBlockZ())) {
            p.sendBlockChange(b, b.getBlock().getBlockData());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(BlockDamageEvent e) {
        Player p = e.getPlayer();
        PlayerData d = ac.data(p);
        Block b = e.getBlock();
        if (d.bait != null && d.bait.getBlockX() == b.getX() && d.bait.getBlockY() == b.getY()
                && d.bait.getBlockZ() == b.getZ() && b.getWorld().equals(d.bait.getWorld())) {
            e.setCancelled(true);
            ac.flag(p, "baitesp", 3.0, "mined hidden bait block (base finder / ESP)");
            clear(p, d);
        }
    }
}

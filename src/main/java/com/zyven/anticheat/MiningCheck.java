package com.zyven.anticheat;

import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;

public class MiningCheck implements Listener {
    private final ZyvenAntiCheat ac;

    public MiningCheck(ZyvenAntiCheat ac) { this.ac = ac; }

    private boolean skip(Player p) {
        return p.getGameMode() != GameMode.SURVIVAL || p.hasPermission("zyven.bypass");
    }

    /** NoDelay: gap between breaking a block and starting the next one. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(BlockDamageEvent e) {
        Player p = e.getPlayer();
        PlayerData d = ac.data(p);
        long now = System.currentTimeMillis();

        if (!skip(p) && ac.enabled("nodelay") && d.lastBreak > 0) {
            long gap = now - d.lastBreak;
            long min = ac.getConfig().getLong("checks.nodelay.min-gap-ms", 150);
            if (gap < min) {
                d.noDelayBuf++;
                if (d.noDelayBuf >= ac.getConfig().getInt("checks.nodelay.buffer", 4)) {
                    ac.flag(p, "nodelay", 1.0, "gap=" + gap + "ms (vanilla ~250ms)");
                    d.lastNoDelayFlag = now;
                    d.digLoc = null;
                    e.setCancelled(true);
                    return;
                }
            } else {
                d.noDelayBuf = Math.max(0, d.noDelayBuf - 1);
            }
        }
        d.digLoc = e.getBlock().getLocation();
        d.digStart = now;
    }

    /** Speed mine: total time to break, based on the player's real break speed. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (skip(p) || !ac.enabled("speedmine")) return;
        Block b = e.getBlock();
        PlayerData d = ac.data(p);
        long now = System.currentTimeMillis();

        boolean tracked = d.digLoc != null && d.digLoc.equals(b.getLocation());
        long elapsed = tracked ? now - d.digStart : 0;
        d.digLoc = null;

        // Break after a cancelled (NoDelay) dig: just block it, already flagged.
        if (!tracked && now - d.lastNoDelayFlag < 500) { e.setCancelled(true); return; }

        // getBreakSpeed already includes Haste, Mining Fatigue, Efficiency, tool, Aqua Affinity, air penalty.
        float speed = b.getBreakSpeed(p);
        if (speed >= 1f || speed <= 0f || b.getType().getHardness() == 0) return; // instant-break
        double ticks = Math.ceil(1.0 / speed);
        if (ticks <= 3) return; // very fast legit setups (Haste + Efficiency): timing is not reliable, NoDelay check covers them

        double tol = ac.getConfig().getDouble("checks.speedmine.tolerance", 0.7);
        double required = (ticks - 1) * 50.0 * tol - Math.min(p.getPing(), 300);
        if (elapsed < required) {
            d.mineBuf++;
            if (d.mineBuf >= 2) { // never punish a single odd break
                e.setCancelled(true);
                ac.flag(p, "speedmine", 1.5, "took " + elapsed + "ms, needs ~" + (long) required + "ms");
            }
        } else {
            d.mineBuf = Math.max(0, d.mineBuf - 1);
        }
    }

    /** Record the break time only if the break actually went through. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreakDone(BlockBreakEvent e) {
        ac.data(e.getPlayer()).lastBreak = System.currentTimeMillis();
    }
}

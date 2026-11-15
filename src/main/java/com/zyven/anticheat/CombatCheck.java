package com.zyven.anticheat;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

public class CombatCheck implements Listener {
    private final ZyvenAntiCheat ac;

    public CombatCheck(ZyvenAntiCheat ac) { this.ac = ac; }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        GameMode gm = p.getGameMode();
        if (p.hasPermission("zyven.bypass") || gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return;

        Entity t = e.getEntity();
        PlayerData d = ac.data(p);
        long now = System.currentTimeMillis();
        Location eye = p.getEyeLocation();
        Vector ev = eye.toVector();
        BoundingBox bb = t.getBoundingBox();
        Vector closest = new Vector(clamp(ev.getX(), bb.getMinX(), bb.getMaxX()),
                clamp(ev.getY(), bb.getMinY(), bb.getMaxY()), clamp(ev.getZ(), bb.getMinZ(), bb.getMaxZ()));
        double dist = closest.distance(ev);
        Vector toCenter = bb.getCenter().subtract(ev);
        double angle = (dist > 1.0 && toCenter.lengthSquared() > 0)
                ? Math.toDegrees(eye.getDirection().angle(toCenter.clone().normalize())) : 0;
        boolean cancel = false;

        // Reach
        if (ac.enabled("reach")) {
            double max = ac.getConfig().getDouble("checks.reach.max", 3.4);
            if (dist > max) {
                if (++d.reachBuf >= 2) { ac.flag(p, "reach", 1.0, "dist=" + String.format("%.2f", dist)); cancel = true; }
            } else d.reachBuf = Math.max(0, d.reachBuf - 1);
        }

        if (ac.enabled("killaura")) {
            // hitting something you are not looking at
            if (angle > 55) {
                if (++d.angleBuf >= 3) { ac.flag(p, "killaura", 1.0, "angle=" + (int) angle); cancel = true; }
            } else d.angleBuf = Math.max(0, d.angleBuf - 1);

            // hitting through walls
            if (dist > 1.5 && !Los.visible(eye, bb)) {
                if (++d.wallBuf >= 2) { ac.flag(p, "killaura", 1.0, "hit through wall"); cancel = true; }
            } else d.wallBuf = Math.max(0, d.wallBuf - 1);

            // multi-aura
            if (t instanceof LivingEntity) {
                d.recentHits.values().removeIf(t0 -> now - t0 > 350);
                d.recentHits.put(t.getUniqueId(), now);
                if (d.recentHits.size() >= 3) {
                    ac.flag(p, "killaura", 1.0, "multi-target " + d.recentHits.size() + " in 350ms");
                    d.recentHits.clear();
                    cancel = true;
                }
            }
        }

        // aim assist: sudden big snap then perfectly on target
        if (ac.enabled("aimassist")) {
            if (angle < 3 && now - d.yawDeltaAt < 120 && d.lastYawDelta > 45) {
                if (++d.aimBuf >= 6) { ac.flag(p, "aimassist", 0.5, "snap " + (int) d.lastYawDelta + " deg"); d.aimBuf = 0; }
            } else d.aimBuf = Math.max(0, d.aimBuf - 1);
        }

        // crystal aura: breaking a crystal almost instantly after placing, or too many per second
        if (t instanceof EnderCrystal && ac.enabled("crystalaura")) {
            long gap = now - d.lastCrystalPlace;
            if (now - d.crystalWin > 1000) { d.crystalWin = now; d.crystalCount = 0; }
            d.crystalCount++;
            if (gap < 60 || d.crystalCount > 10) {
                if (++d.crystalBuf >= 3) {
                    ac.flag(p, "crystalaura", 1.0, "place->hit " + gap + "ms, " + d.crystalCount + "/s");
                    cancel = true;
                }
            } else d.crystalBuf = Math.max(0, d.crystalBuf - 1);
        }

        if (cancel) e.setCancelled(true);
    }

    /** Autoclicker: arm swings per second. */
    @EventHandler(ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent e) {
        Player p = e.getPlayer();
        if (p.hasPermission("zyven.bypass") || !ac.enabled("autoclick")) return;
        PlayerData d = ac.data(p);
        long now = System.currentTimeMillis();
        d.cps++;
        if (now - d.cpsWindow >= 1000) {
            if (d.cps > 22) {
                if (++d.cpsBuf >= 2) ac.flag(p, "autoclick", 1.0, d.cps + " clicks/s");
            } else d.cpsBuf = Math.max(0, d.cpsBuf - 1);
            d.cps = 0;
            d.cpsWindow = now;
        }
    }

    /** Crystal placement timing + respawn anchor spam. */
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        PlayerData d = ac.data(p);
        long now = System.currentTimeMillis();
        if (e.getItem() != null && e.getItem().getType() == Material.END_CRYSTAL) d.lastCrystalPlace = now;

        if (e.getClickedBlock() != null && e.getClickedBlock().getType() == Material.RESPAWN_ANCHOR
                && ac.enabled("anchoraura") && !p.hasPermission("zyven.bypass")) {
            if (now - d.anchorWin > 1000) { d.anchorWin = now; d.anchorCount = 0; }
            if (++d.anchorCount > 8) {
                ac.flag(p, "anchoraura", 1.0, d.anchorCount + " anchor clicks/s");
                e.setCancelled(true);
            }
        }
    }
}

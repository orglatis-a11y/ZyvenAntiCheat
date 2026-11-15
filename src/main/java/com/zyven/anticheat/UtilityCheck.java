package com.zyven.anticheat;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownExpBottle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

public class UtilityCheck implements Listener {
    private final ZyvenAntiCheat ac;

    public UtilityCheck(ZyvenAntiCheat ac) {
        this.ac = ac;
        startAirStuckTask();
    }

    private static boolean consumable(Material m) {
        return m.isEdible() || m == Material.POTION || m == Material.MILK_BUCKET || m == Material.HONEY_BOTTLE;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        ItemStack it = e.getItem();
        if (it == null) return;
        PlayerData d = ac.data(e.getPlayer());
        long now = System.currentTimeMillis();
        if (it.getType() == Material.FIREWORK_ROCKET) d.lastElytraBoost = now;
        if (consumable(it.getType()) && d.useStart == 0) d.useStart = now;
    }

    /** Fast eat / fast drink: items take 0.85s to 1.6s to consume in vanilla. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        PlayerData d = ac.data(p);
        long start = d.useStart;
        d.useStart = 0;
        if (start == 0 || !ac.enabled("fastuse") || p.hasPermission("zyven.bypass")) return;
        long elapsed = System.currentTimeMillis() - start;
        long min = ac.getConfig().getLong("checks.fastuse.min-ms", 650);
        long required = min - Math.min(p.getPing(), 250);
        if (elapsed < required) {
            if (++d.fastUseBuf >= 2) {
                e.setCancelled(true);
                ac.flag(p, "fastuse", 1.0, "consumed in " + elapsed + "ms");
            }
        } else d.fastUseBuf = Math.max(0, d.fastUseBuf - 1);
    }

    /** Fast XP bottle throwing. */
    @EventHandler(ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent e) {
        if (!(e.getEntity() instanceof ThrownExpBottle) || !(e.getEntity().getShooter() instanceof Player p)) return;
        if (!ac.enabled("fastxp") || p.hasPermission("zyven.bypass")) return;
        PlayerData d = ac.data(p);
        long now = System.currentTimeMillis();
        if (d.lastXp > 0 && now - d.lastXp < 120) {
            if (++d.xpBuf >= 3) { e.setCancelled(true); ac.flag(p, "fastxp", 1.0, "bottle gap " + (now - d.lastXp) + "ms"); }
        } else d.xpBuf = Math.max(0, d.xpBuf - 1);
        d.lastXp = now;
    }

    /** Auto totem / hover totem / inventory totem: a new totem in the offhand within 2 ticks of a pop. */
    @EventHandler(ignoreCancelled = true)
    public void onResurrect(EntityResurrectEvent e) {
        if (!(e.getEntity() instanceof Player p) || !ac.enabled("autototem") || p.hasPermission("zyven.bypass")) return;
        boolean main = p.getInventory().getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING;
        boolean off = p.getInventory().getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING;
        if (main || !off) return; // mainhand totem gets used first, so offhand is not a re-equip
        Bukkit.getScheduler().runTaskLater(ac, () -> {
            if (!p.isOnline()) return;
            if (p.getInventory().getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING) {
                ac.flag(p, "autototem", 1.0, "offhand refilled within 2 ticks of pop");
            }
        }, 2L);
    }

    /** Air stuck: no movement packets while hanging in the air. */
    private void startAirStuckTask() {
        Bukkit.getScheduler().runTaskTimer(ac, () -> {
            if (!ac.enabled("airstuck")) return;
            long now = System.currentTimeMillis();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (ac.exempt(p) || p.isInsideVehicle() || p.isGliding() || p.isSleeping() || p.getPing() > 400) continue;
                PlayerData d = ac.data(p);
                if (d.lastMove == 0 || now - d.lastTeleport < 3000 || now - d.lastSpecial < 3000
                        || now - d.lastVelocity < 3000) { d.airStuckBuf = 0; continue; }
                Location l = p.getLocation();
                boolean air = !MovementCheck.groundBelow(l, 0.1) && !MovementCheck.groundBelow(l, 0.9)
                        && !l.getBlock().isLiquid() && !MovementCheck.special(l) && !MovementCheck.standingOnEntity(p);
                if (air && now - d.lastMove > 1500) {
                    if (++d.airStuckBuf >= 3) {
                        ac.flag(p, "airstuck", 1.0, "frozen in air " + (now - d.lastMove) + "ms");
                        p.teleport(d.lastSafe != null ? d.lastSafe : l);
                        d.lastTeleport = now;
                        d.airStuckBuf = 0;
                    }
                } else d.airStuckBuf = 0;
            }
        }, 40L, 10L);
    }
}

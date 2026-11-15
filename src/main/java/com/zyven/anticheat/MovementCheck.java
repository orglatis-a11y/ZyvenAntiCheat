package com.zyven.anticheat;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class MovementCheck implements Listener {
    private final ZyvenAntiCheat ac;

    public MovementCheck(ZyvenAntiCheat ac) { this.ac = ac; }

    @EventHandler
    public void onVelocity(PlayerVelocityEvent e) { ac.data(e.getPlayer()).lastVelocity = System.currentTimeMillis(); }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        PlayerData d = ac.data(e.getPlayer());
        d.lastTeleport = System.currentTimeMillis();
        d.reset(e.getTo());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        PlayerData d = ac.data(e.getPlayer());
        d.lastTeleport = System.currentTimeMillis();
        d.reset(e.getPlayer().getLocation());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        Location from = e.getFrom(), to = e.getTo();
        if (to == null || from.getWorld() != to.getWorld()) return;
        PlayerData d = ac.data(p);
        long now = System.currentTimeMillis();

        double dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ(), dy = to.getY() - from.getY();
        double h = Math.hypot(dx, dz);

        if (p.isGliding() || p.isRiptiding() || special(to)) d.lastSpecial = now;
        boolean exempt = ac.exempt(p) || p.isInsideVehicle()
                || now - d.lastTeleport < 1000 || now - d.lastVelocity < 1500 || now - d.lastSpecial < 1500
                || p.hasPotionEffect(PotionEffectType.LEVITATION) || p.hasPotionEffect(PotionEffectType.SLOW_FALLING);
        if (exempt) { d.reset(to); d.lastGround = true; return; }
        if (d.lastSafe == null) d.reset(from);

        boolean ground = groundBelow(to) || standingOnEntity(p);
        Block feet = to.getBlock();
        boolean liquid = feet.isLiquid() || to.clone().add(0, 1, 0).getBlock().isLiquid();
        boolean climb = Tag.CLIMBABLE.isTagged(feet.getType());
        Material below = to.clone().subtract(0, 0.3, 0).getBlock().getType();
        if (below.name().contains("ICE")) d.lastIce = now;

        String flagged = null, info = "";

        if (ground || liquid || climb) {
            d.groundY = to.getY();
            d.airTicks = 0;
            d.flyBuf = 0;
        } else {
            d.airTicks++;
            double expected = (d.lastDy - 0.08) * 0.98;
            int jb = amp(p, PotionEffectType.JUMP_BOOST);

            if (d.lastGround && dy > 0.42 + 0.1 * jb + 0.06) { flagged = "highjump"; info = "jump dy=" + r(dy); }

            if (flagged == null && d.airTicks >= 3) {
                if (dy > expected + 0.06) {
                    if (++d.flyBuf >= 3) {
                        flagged = (touchingWall(to) && dy > 0) ? "spider" : "fly";
                        info = "dy=" + r(dy) + " exp=" + r(expected);
                    }
                } else d.flyBuf = Math.max(0, d.flyBuf - 1);
            }
            if (flagged == null && d.airTicks > 12 && Math.abs(dy) < 0.02) { flagged = "fly"; info = "hover"; }

            double maxRise = 1.30 + 0.6 * jb + 0.15;
            if (flagged == null && to.getY() - d.groundY > maxRise) {
                flagged = "highjump"; info = "rise=" + r(to.getY() - d.groundY);
            }
        }

        if (flagged == null && h > 0) {
            double limit = ground ? 0.37 : 0.42;
            limit *= p.getWalkSpeed() / 0.2;
            int sp = amp(p, PotionEffectType.SPEED);
            if (sp > 0) limit *= 1 + 0.2 * sp;
            if (now - d.lastIce < 1500) limit *= 1.8;
            if (below.name().contains("SOUL")) limit *= 1.8;
            if (h > limit) {
                d.speedBuf += 1;
                if (d.speedBuf >= 4) { flagged = "speed"; info = "h=" + r(h) + " max=" + r(limit); }
            } else d.speedBuf = Math.max(0, d.speedBuf - 0.5);
        }

        if (flagged == null) {
            Block under = to.clone().subtract(0, 0.2, 0).getBlock();
            boolean walkingOnWater = under.isLiquid() && !feet.isLiquid() && !p.isSwimming()
                    && feet.getType() != Material.LILY_PAD && Math.abs(dy) < 0.05 && h > 0.05;
            if (walkingOnWater) d.waterTicks++; else d.waterTicks = 0;
            if (d.waterTicks >= 6) { flagged = "jesus"; info = "walking on liquid"; }
        }

        if (flagged != null && ac.enabled(flagged)) {
            ac.flag(p, flagged, 1.0, info);
            if (ac.getConfig().getBoolean("setback", true)) {
                Location back = d.lastSafe.clone();
                back.setYaw(to.getYaw());
                back.setPitch(to.getPitch());
                e.setTo(back);
                d.airTicks = 0; d.flyBuf = 0; d.waterTicks = 0; d.speedBuf = 0; d.lastDy = 0;
                d.lastGround = true;
                return;
            }
        } else if (ground) {
            d.lastSafe = to.clone();
        }

        d.lastDy = dy;
        d.lastGround = ground;
    }

    private static String r(double v) { return String.format("%.3f", v); }

    private static int amp(Player p, PotionEffectType t) {
        PotionEffect pe = p.getPotionEffect(t);
        return pe == null ? 0 : pe.getAmplifier() + 1;
    }

    private static boolean groundBelow(Location l) {
        for (double ox = -0.3; ox <= 0.3; ox += 0.3)
            for (double oz = -0.3; oz <= 0.3; oz += 0.3) {
                int x = (int) Math.floor(l.getX() + ox), z = (int) Math.floor(l.getZ() + oz);
                Block b = l.getWorld().getBlockAt(x, (int) Math.floor(l.getY() - 0.1), z);
                if (!b.isPassable() && !b.isLiquid()) return true;
                Block f = l.getWorld().getBlockAt(x, (int) Math.floor(l.getY() - 0.6), z);
                Material m = f.getType();
                if (Tag.FENCES.isTagged(m) || Tag.WALLS.isTagged(m) || Tag.FENCE_GATES.isTagged(m)) return true;
            }
        return false;
    }

    private static boolean standingOnEntity(Player p) {
        for (Entity en : p.getNearbyEntities(1.5, 1.5, 1.5))
            if (en instanceof Boat || en instanceof Shulker) return true;
        return false;
    }

    private static boolean touchingWall(Location l) {
        double[][] offs = {{0.35, 0}, {-0.35, 0}, {0, 0.35}, {0, -0.35}};
        for (double[] o : offs)
            for (double y : new double[]{0.5, 1.4})
                if (!l.clone().add(o[0], y, o[1]).getBlock().isPassable()) return true;
        return false;
    }

    private static boolean special(Location l) {
        for (double y : new double[]{0, -0.5, -1.2}) {
            Material m = l.clone().add(0, y, 0).getBlock().getType();
            if (m == Material.SLIME_BLOCK || m == Material.HONEY_BLOCK || m == Material.COBWEB
                    || m == Material.POWDER_SNOW || m == Material.BUBBLE_COLUMN
                    || m == Material.SWEET_BERRY_BUSH || m == Material.SCAFFOLDING
                    || m == Material.BIG_DRIPLEAF || Tag.BEDS.isTagged(m) || Tag.CLIMBABLE.isTagged(m)) return true;
        }
        return false;
    }
}

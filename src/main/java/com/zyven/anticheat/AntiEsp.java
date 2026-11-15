package com.zyven.anticheat;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class AntiEsp extends BukkitRunnable {
    private final ZyvenAntiCheat ac;
    private final Set<UUID> decoyIds = new HashSet<>();

    public AntiEsp(ZyvenAntiCheat ac) { this.ac = ac; }

    @Override
    public void run() {
        FileConfiguration cfg = ac.getConfig();
        double range = cfg.getDouble("anti-esp.range", 64);
        double close = cfg.getDouble("anti-esp.close-distance", 6);
        boolean mobs = cfg.getBoolean("anti-esp.hide-mobs", true);
        boolean decoyOn = cfg.getBoolean("anti-esp.decoy.enabled", true) && ac.enabled("esp");
        int stareMax = cfg.getInt("anti-esp.decoy.stare-samples", 14);
        long now = System.currentTimeMillis();

        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = ac.data(p);
            if (p.hasPermission("zyven.bypass") || p.getGameMode() == GameMode.SPECTATOR) {
                showAll(p, d);
                removeDecoy(d);
                continue;
            }
            Location eye = p.getEyeLocation();
            Set<UUID> seen = new HashSet<>();

            for (Entity t : p.getNearbyEntities(range, range, range)) {
                if (!(t instanceof LivingEntity) || t instanceof ArmorStand || decoyIds.contains(t.getUniqueId())) continue;
                if (!(t instanceof Player) && !mobs) continue;
                if (p.getPassengers().contains(t) || t.equals(p.getVehicle())) continue;
                UUID id = t.getUniqueId();
                seen.add(id);
                boolean vis = t.getLocation().distanceSquared(p.getLocation()) <= close * close || visible(eye, t.getBoundingBox());
                boolean hid = d.hidden.contains(id);
                if (!vis && !hid) { p.hideEntity(ac, t); d.hidden.add(id); }
                else if (vis && hid) { p.showEntity(ac, t); d.hidden.remove(id); }
            }
            Iterator<UUID> it = d.hidden.iterator();
            while (it.hasNext()) {
                UUID id = it.next();
                if (seen.contains(id)) continue;
                Entity en = Bukkit.getEntity(id);
                if (en != null) p.showEntity(ac, en);
                it.remove();
            }

            if (decoyOn && p.getGameMode() == GameMode.SURVIVAL) {
                if (d.decoy == null || !d.decoy.isValid() || now - d.decoyMoved > 10000) {
                    spawnDecoy(p, d, eye);
                }
                if (d.decoy != null && d.decoy.isValid()) {
                    BoundingBox bb = d.decoy.getBoundingBox();
                    Vector to = bb.getCenter().subtract(eye.toVector());
                    double ang = Math.toDegrees(eye.getDirection().angle(to.clone().normalize()));
                    boolean hiddenNow = !visible(eye, bb);
                    if (hiddenNow && ang < 4) d.stare++; else d.stare = Math.max(0, d.stare - 1);
                    if (d.stare >= stareMax) {
                        d.stare = 0;
                        ac.flag(p, "esp", 4, "tracking hidden decoy through walls");
                        d.punishUntil = now + cfg.getLong("anti-esp.punish.seconds", 30) * 1000L;
                        removeDecoy(d);
                    }
                }
            }

            if (now < d.punishUntil) {
                int n = Math.min(cfg.getInt("anti-esp.punish.particles", 300), 800);
                Location l = p.getLocation();
                for (Particle pt : new Particle[]{Particle.CLOUD, Particle.END_ROD, Particle.FLAME}) {
                    p.spawnParticle(pt, l, n, 8, 4, 8, 0.05);
                }
            }
        }
    }

    private void spawnDecoy(Player p, PlayerData d, Location eye) {
        removeDecoy(d);
        World w = p.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 12; i++) {
            double ang = r.nextDouble(Math.PI * 2), dist = 8 + r.nextDouble() * 8;
            Location loc = p.getLocation().add(Math.cos(ang) * dist, r.nextDouble(-1, 2), Math.sin(ang) * dist);
            if (!w.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) continue;
            BoundingBox probe = BoundingBox.of(loc.toVector(), 0.3, 0.9, 0.3);
            if (visible(eye, probe)) continue;
            Villager v = w.spawn(loc, Villager.class, e -> {
                e.setVisibleByDefault(false);
                e.setInvisible(true);
                e.setAI(false);
                e.setSilent(true);
                e.setInvulnerable(true);
                e.setCollidable(false);
                e.setGravity(false);
                e.setPersistent(false);
                e.setRemoveWhenFarAway(true);
            });
            p.showEntity(ac, v);
            d.decoy = v;
            d.decoyMoved = System.currentTimeMillis();
            d.stare = 0;
            decoyIds.add(v.getUniqueId());
            return;
        }
    }

    public void removeDecoy(PlayerData d) {
        if (d.decoy != null) {
            decoyIds.remove(d.decoy.getUniqueId());
            d.decoy.remove();
            d.decoy = null;
        }
    }

    private void showAll(Player p, PlayerData d) {
        for (UUID id : d.hidden) {
            Entity en = Bukkit.getEntity(id);
            if (en != null) p.showEntity(ac, en);
        }
        d.hidden.clear();
    }

    public void cleanup() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = ac.data(p);
            showAll(p, d);
            removeDecoy(d);
        }
    }

    private boolean visible(Location eye, BoundingBox b) {
        double cx = b.getCenterX(), cy = b.getCenterY(), cz = b.getCenterZ();
        Vector[] pts = {
                new Vector(cx, cy, cz), new Vector(cx, b.getMinY() + 0.1, cz), new Vector(cx, b.getMaxY() - 0.1, cz),
                new Vector(b.getMinX(), cy, b.getMinZ()), new Vector(b.getMaxX(), cy, b.getMinZ()),
                new Vector(b.getMinX(), cy, b.getMaxZ()), new Vector(b.getMaxX(), cy, b.getMaxZ())
        };
        for (Vector v : pts) if (clear(eye, v)) return true;
        return false;
    }

    private boolean clear(Location eye, Vector target) {
        World w = eye.getWorld();
        Vector from = eye.toVector();
        for (int i = 0; i < 8; i++) {
            Vector dir = target.clone().subtract(from);
            double dist = dir.length();
            if (dist < 0.1) return true;
            Vector unit = dir.clone().normalize();
            RayTraceResult res = w.rayTraceBlocks(from.toLocation(w), unit, dist, FluidCollisionMode.NEVER, true);
            if (res == null || res.getHitBlock() == null) return true;
            Block hb = res.getHitBlock();
            if (hb.getType().isOccluding()) return false;
            from = res.getHitPosition().clone().add(unit.multiply(1.0));
        }
        return false;
    }
}

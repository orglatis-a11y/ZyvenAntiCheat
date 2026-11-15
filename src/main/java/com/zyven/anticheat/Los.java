package com.zyven.anticheat;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

final class Los {
    private Los() {}

    static boolean visible(Location eye, BoundingBox b) {
        double cx = b.getCenterX(), cy = b.getCenterY(), cz = b.getCenterZ();
        Vector[] pts = {
                new Vector(cx, cy, cz), new Vector(cx, b.getMinY() + 0.1, cz), new Vector(cx, b.getMaxY() - 0.1, cz),
                new Vector(b.getMinX(), cy, b.getMinZ()), new Vector(b.getMaxX(), cy, b.getMinZ()),
                new Vector(b.getMinX(), cy, b.getMaxZ()), new Vector(b.getMaxX(), cy, b.getMaxZ())
        };
        for (Vector v : pts) if (clear(eye, v)) return true;
        return false;
    }

    static boolean clear(Location eye, Vector target) {
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

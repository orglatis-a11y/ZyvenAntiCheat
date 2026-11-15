package com.zyven.anticheat;

import org.bukkit.Location;
import org.bukkit.entity.Villager;
import java.util.*;

public class PlayerData {
    public final Map<String, Double> vl = new HashMap<>();
    public final Set<UUID> hidden = new HashSet<>();
    public final Map<UUID, Long> recentHits = new HashMap<>();
    public Location lastSafe, digLoc, bait;
    public long lastTeleport = System.currentTimeMillis(), lastVelocity, lastSpecial, digStart, punishUntil, decoyMoved,
            lastIce, lastBreak, lastNoDelayFlag, lastMove, lastJump, lastElytraBoost, useStart, lastCrystalPlace,
            lastXp, yawDeltaAt, cpsWindow, anchorWin, crystalWin, baitTime;
    public double lastDy, groundY, speedBuf, glideStartY, lastYawDelta;
    public int noDelayBuf, mineBuf, airTicks, flyBuf, waterTicks, stare, jumpBuf, nofallBuf, airStuckBuf, reachBuf,
            angleBuf, wallBuf, aimBuf, crystalBuf, crystalCount, fastUseBuf, cps, cpsBuf, anchorCount, glideTicks,
            hoverGlide, xpBuf, boatBuf, vehSpeedBuf, vehControlBuf;
    public boolean lastGround;
    public Villager decoy;

    public void reset(Location l) {
        lastSafe = l.clone();
        airTicks = 0; flyBuf = 0; waterTicks = 0; speedBuf = 0;
        lastDy = 0; groundY = l.getY();
    }
}

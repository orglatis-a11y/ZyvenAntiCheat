package com.zyven.anticheat;

import org.bukkit.Location;
import org.bukkit.entity.Villager;
import java.util.*;

public class PlayerData {
    public final Map<String, Double> vl = new HashMap<>();
    public final Set<UUID> hidden = new HashSet<>();
    public Location lastSafe, digLoc;
    public long lastTeleport = System.currentTimeMillis(), lastVelocity, lastSpecial, digStart, punishUntil, decoyMoved, lastIce, lastBreak, lastNoDelayFlag;
    public double lastDy, groundY, speedBuf;
    public int noDelayBuf, mineBuf, airTicks, flyBuf, waterTicks, stare;
    public boolean lastGround;
    public Villager decoy;

    public void reset(Location l) {
        lastSafe = l.clone();
        airTicks = 0; flyBuf = 0; waterTicks = 0; speedBuf = 0;
        lastDy = 0; groundY = l.getY();
    }
}

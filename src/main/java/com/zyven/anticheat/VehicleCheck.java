package com.zyven.anticheat;

import org.bukkit.Location;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Llama;
import org.bukkit.entity.Player;
import org.bukkit.entity.Steerable;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleMoveEvent;

public class VehicleCheck implements Listener {
    private final ZyvenAntiCheat ac;

    public VehicleCheck(ZyvenAntiCheat ac) { this.ac = ac; }

    @EventHandler
    public void onVehicleMove(VehicleMoveEvent e) {
        Vehicle v = e.getVehicle();
        if (v.getPassengers().isEmpty() || !(v.getPassengers().get(0) instanceof Player p)) return;
        if (ac.exempt(p)) return;
        PlayerData d = ac.data(p);
        long now = System.currentTimeMillis();
        if (now - d.lastVelocity < 1500 || now - d.lastTeleport < 1000) return;

        Location f = e.getFrom(), t = e.getTo();
        double h = Math.hypot(t.getX() - f.getX(), t.getZ() - f.getZ());
        double dy = t.getY() - f.getY();

        if (v instanceof Boat) {
            if (!ac.enabled("boatfly")) return;
            boolean water = t.getBlock().isLiquid() || t.clone().subtract(0, 0.4, 0).getBlock().isLiquid();
            boolean solid = !t.clone().subtract(0, 0.3, 0).getBlock().isPassable();
            boolean ice = false;
            for (double y = 0; y <= 1.5; y += 0.5)
                if (t.clone().subtract(0, y, 0).getBlock().getType().name().contains("ICE")) ice = true;
            if (MovementCheck.special(t)) return;

            double limit = ice ? 3.8 : 0.75;
            if (h > limit) {
                if (++d.vehSpeedBuf >= 3) { ac.flag(p, "boatfly", 1.0, "boat speed " + String.format("%.2f", h)); d.vehSpeedBuf = 0; }
            } else d.vehSpeedBuf = Math.max(0, d.vehSpeedBuf - 1);

            if (!water && !solid) {
                if (dy > 0.04 || (Math.abs(dy) < 0.01 && h > 0.05)) {
                    if (++d.boatBuf >= 8) { ac.flag(p, "boatfly", 1.0, "boat flying dy=" + String.format("%.3f", dy)); d.boatBuf = 0; }
                }
            } else d.boatBuf = Math.max(0, d.boatBuf - 1);
            return;
        }

        // ridden animals: EntitySpeed / EntityControl
        if (v instanceof AbstractHorse || v instanceof Steerable) {
            if (ac.enabled("entityspeed")) {
                if (h > 1.0) {
                    if (++d.vehSpeedBuf >= 4) { ac.flag(p, "entityspeed", 1.0, "mount speed " + String.format("%.2f", h)); d.vehSpeedBuf = 0; }
                } else d.vehSpeedBuf = Math.max(0, d.vehSpeedBuf - 1);
            }
            if (ac.enabled("entitycontrol") && !(v instanceof Llama) && h > 0.35) {
                boolean saddled = true;
                if (v instanceof AbstractHorse ah) saddled = ah.getInventory().getSaddle() != null;
                else if (v instanceof Steerable st) saddled = st.hasSaddle();
                if (!saddled) {
                    if (++d.vehControlBuf >= 5) { ac.flag(p, "entitycontrol", 1.0, "steering mount without saddle"); d.vehControlBuf = 0; }
                } else d.vehControlBuf = Math.max(0, d.vehControlBuf - 1);
            }
        }
    }
}

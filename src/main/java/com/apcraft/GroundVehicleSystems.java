package com.apcraft;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Persistent, entity-tag-based fuel and condition for ground vehicle prototypes.
 * Fuel and condition are intentionally game mechanics, not real-world vehicle specifications.
 */
public final class GroundVehicleSystems {
    private GroundVehicleSystems() {}
    private static final Map<UUID, double[]> LAST_POSITION = new HashMap<>();
    private static int ticks;
    private static final Map<UUID, Double> DISTANCE_BUFFER = new HashMap<>();

    private static boolean ground(BoatEntity boat) {
        for (String tag : boat.getCommandTags()) {
            if (tag.startsWith("apcraft_ground_class_") || tag.equals("apcraft_model_1") || tag.equals("apcraft_model_2")) return true;
        }
        return false;
    }
    private static String vehicleClass(BoatEntity boat) {
        for (String tag : boat.getCommandTags())
            if (tag.startsWith("apcraft_ground_class_")) return tag.substring("apcraft_ground_class_".length());
        return boat.getCommandTags().contains("apcraft_model_2") ? "light_truck" : "utility";
    }
    private static int fuelInterval(String type) {
        return switch (type) {
            case "motorcycle", "economy" -> 36;
            case "sedan", "wagon", "utility" -> 28;
            case "performance", "offroad", "light_truck" -> 20;
            case "heavy_truck", "commercial" -> 12;
            default -> 20;
        };
    }
    private static int wearInterval(String type) {
        return switch (type) {
            case "offroad", "heavy_truck", "commercial" -> 150;
            case "motorcycle", "performance" -> 75;
            default -> 100;
        };
    }
    private static int stat(BoatEntity boat, String prefix, int fallback) {
        for (String tag : boat.getCommandTags()) if (tag.startsWith(prefix)) {
            try { return Integer.parseInt(tag.substring(prefix.length())); } catch (NumberFormatException ignored) { return fallback; }
        }
        return fallback;
    }
    private static void setStat(BoatEntity boat, String prefix, int value) {
        for (String tag : boat.getCommandTags()) if (tag.startsWith(prefix)) boat.removeCommandTag(tag);
        boat.addCommandTag(prefix + Math.max(0, Math.min(prefix.equals("apcraft_trip_") || prefix.equals("apcraft_wear_") ? 10000 : 100, value)));
    }
    private static void message(ServerPlayerEntity p, String msg) { p.sendMessage(Text.literal("[A&P GARAGE] " + msg), false); }
    public static void initialize() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (hand != Hand.MAIN_HAND || !(entity instanceof BoatEntity boat) || !ground(boat) || !player.isSneaking()) return ActionResult.PASS;
            if (!boat.getCommandTags().contains("apcraft_owner_" + player.getUuid())) return ActionResult.PASS;
            if (world.isClient) return ActionResult.SUCCESS;
            ServerPlayerEntity p = (ServerPlayerEntity) player;
            int fuel = stat(boat, "apcraft_fuel_", 100);
            int condition = stat(boat, "apcraft_condition_", 100);
            ItemStack held = p.getMainHandStack();
            if (held.isOf(Items.COAL) || held.isOf(Items.CHARCOAL)) {
                if (fuel == 100) { message(p, "Tank is already full."); return ActionResult.SUCCESS; }
                setStat(boat, "apcraft_fuel_", Math.min(100, fuel + 25));
                if (!p.getAbilities().creativeMode) held.decrement(1);
                message(p, "Refueled +25. Fuel: " + Math.min(100, fuel + 25) + "/100.");
            } else if (held.isOf(Items.IRON_INGOT)) {
                if (condition == 100) { message(p, "Vehicle is already in excellent condition."); return ActionResult.SUCCESS; }
                setStat(boat, "apcraft_condition_", Math.min(100, condition + 20));
                if (!p.getAbilities().creativeMode) held.decrement(1);
                message(p, "Serviced +20. Condition: " + Math.min(100, condition + 20) + "/100.");
            } else if (held.isOf(Items.REDSTONE)) {
                if (boat.getCommandTags().contains("apcraft_upgrade_tuned")) {
                    message(p, "Engine tuning already installed."); return ActionResult.SUCCESS;
                }
                boat.addCommandTag("apcraft_upgrade_tuned");
                if (!p.getAbilities().creativeMode) held.decrement(1);
                message(p, "Engine tuning installed: modest acceleration boost at the cost of higher fuel consumption.");
            } else if (held.isOf(Items.LEATHER)) {
                if (boat.getCommandTags().contains("apcraft_upgrade_rugged")) {
                    message(p, "Rugged protection already installed."); return ActionResult.SUCCESS;
                }
                boat.addCommandTag("apcraft_upgrade_rugged");
                if (!p.getAbilities().creativeMode) held.decrement(1);
                message(p, "Rugged protection installed: slower condition wear.");
            } else {
                String type = vehicleClass(boat);
                message(p, "Class " + type + " | Fuel " + fuel + "/100 | Condition " + condition + "/100 | Tuned "
                    + boat.getCommandTags().contains("apcraft_upgrade_tuned") + " | Rugged "
                    + boat.getCommandTags().contains("apcraft_upgrade_rugged"));
                message(p, "Coal: fuel | Iron: repair | Redstone: engine tuning | Leather: rugged protection.");
            }
            return ActionResult.SUCCESS;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++ticks % 20 != 0) return;
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                Entity ridden = p.getVehicle();
                if (!(ridden instanceof BoatEntity boat) || !ground(boat)) continue;
                if (!boat.getCommandTags().contains("apcraft_owner_" + p.getUuid())) continue;
                double[] last = LAST_POSITION.put(boat.getUuid(), new double[]{boat.getX(), boat.getY(), boat.getZ()});
                if (last == null) continue;
                double dx = boat.getX()-last[0], dz = boat.getZ()-last[2];
                double traveled = Math.sqrt(dx*dx+dz*dz);
                if (traveled < 0.01) continue; // accumulate short movements rather than dropping them
                int fuel = stat(boat, "apcraft_fuel_", 100);
                int condition = stat(boat, "apcraft_condition_", 100);
                String type = vehicleClass(boat);
                double accumulated = DISTANCE_BUFFER.getOrDefault(boat.getUuid(), 0.0) + Math.min(20, traveled);
                int distance = stat(boat, "apcraft_trip_", 0) + (int) accumulated;
                DISTANCE_BUFFER.put(boat.getUuid(), accumulated - (int) accumulated);
                int fuelStep = fuelInterval(type);
                if (boat.getCommandTags().contains("apcraft_upgrade_tuned")) fuelStep = Math.max(5, fuelStep * 4 / 5);
                while (distance >= fuelStep) { fuel = Math.max(0, fuel - 1); setStat(boat, "apcraft_fuel_", fuel); distance -= fuelStep; }
                setStat(boat, "apcraft_trip_", distance);
                int wear = stat(boat, "apcraft_wear_", 0) + (int)Math.min(20, traveled);
                int wearStep = wearInterval(type);
                if (boat.getCommandTags().contains("apcraft_upgrade_rugged")) wearStep *= 2;
                while (wear >= wearStep) { condition = Math.max(0, condition - 1); setStat(boat, "apcraft_condition_", condition); wear -= wearStep; }
                setStat(boat, "apcraft_wear_", wear);
                if (boat.getCommandTags().contains("apcraft_upgrade_tuned") && fuel > 0 && condition > 0)
                    boat.setVelocity(boat.getVelocity().multiply(1.06, 1.0, 1.06));
                if (fuel <= 1) { boat.setVelocity(boat.getVelocity().multiply(0.2)); if (ticks % 200 == 0) message(p, "Out of fuel! Sneak-right-click with coal to refuel."); }
                if (condition <= 1) { boat.setVelocity(boat.getVelocity().multiply(0.5)); if (ticks % 200 == 0) message(p, "Vehicle needs service! Sneak-right-click with an iron ingot."); }
            }
        });
    }
}

package com.apcraft;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Fictional, arcade-only Apache gameplay systems. No real-world targeting or weapon modeling. */
public final class ApacheSystems {
    private ApacheSystems() {}
    private static final Set<UUID> NVG = new HashSet<>();
    private static final String APACHE_TAG = "apcraft_model_8";

    public static void initialize() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            Entity vehicle = player.getVehicle();
            if (!(vehicle instanceof BoatEntity boat) || !boat.getCommandTags().contains(APACHE_TAG))
                return TypedActionResult.pass(player.getStackInHand(hand));
            ItemStack held = player.getStackInHand(hand);
            if (held.isOf(Items.SPYGLASS)) {
                if (!world.isClient && player instanceof ServerPlayerEntity pilot) {
                    if (NVG.remove(pilot.getUuid())) {
                        pilot.removeStatusEffect(StatusEffects.NIGHT_VISION);
                        pilot.sendMessage(Text.literal("[APACHE] Night-vision goggles OFF"), true);
                    } else {
                        NVG.add(pilot.getUuid());
                        pilot.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 260, 0, true, false, true));
                        pilot.sendMessage(Text.literal("[APACHE] Night-vision goggles ON"), true);
                    }
                }
                return TypedActionResult.success(held, world.isClient);
            }
            if (held.isOf(Items.SNOWBALL)) {
                if (!world.isClient && player instanceof ServerPlayerEntity pilot) {
                    if (pilot.getItemCooldownManager().isCoolingDown(Items.SNOWBALL))
                        return TypedActionResult.fail(held);
                    SnowballEntity projectile = new SnowballEntity(world, pilot);
                    projectile.setPosition(boat.getX(), boat.getY() + 1.6, boat.getZ());
                    projectile.setVelocity(pilot, pilot.getPitch(), pilot.getYaw(), 0.0f, 2.0f, 0.0f);
                    world.spawnEntity(projectile);
                    pilot.getItemCooldownManager().set(Items.SNOWBALL, 8);
                    if (!pilot.getAbilities().creativeMode) held.decrement(1);
                    pilot.sendMessage(Text.literal("[APACHE] Training round fired"), true);
                }
                return TypedActionResult.success(held, world.isClient);
            }
            return TypedActionResult.pass(held);
        });
        // Keep the effect refreshed only while aboard the Apache; clear it when dismounted.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity pilot : server.getPlayerManager().getPlayerList()) {
                if (!NVG.contains(pilot.getUuid())) continue;
                Entity vehicle = pilot.getVehicle();
                if (!(vehicle instanceof BoatEntity boat) || !boat.getCommandTags().contains(APACHE_TAG)) {
                    NVG.remove(pilot.getUuid());
                    pilot.removeStatusEffect(StatusEffects.NIGHT_VISION);
                } else if (!pilot.hasStatusEffect(StatusEffects.NIGHT_VISION)
                        || pilot.getStatusEffect(StatusEffects.NIGHT_VISION).getDuration() < 60) {
                    pilot.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 260, 0, true, false, true));
                }
            }
        });
    }
}

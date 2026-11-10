package com.apcraft;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.WorldSavePath;
import java.nio.file.Files;
import java.nio.file.Path;

/** Player-facing navigation for the existing generated starter airport.
 * No cheats, OP permissions, or player-entered commands are required.
 */
public final class AirportGuide {
    private static final String KEY = "apcraft_airport_guide";
    private AirportGuide() {}

    public static void initialize() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
            ServerPlayerEntity player = handler.player;
            boolean found = false;
            for (int i = 0; i < player.getInventory().size(); i++) {
                ItemStack stack = player.getInventory().getStack(i);
                if (stack.hasNbt() && stack.getNbt().getBoolean(KEY)) { found = true; break; }
            }
            if (!found) {
                ItemStack guide = new ItemStack(Items.COMPASS);
                NbtCompound nbt = guide.getOrCreateNbt();
                nbt.putBoolean(KEY, true);
                guide.setCustomName(Text.literal("A&P Craft | Airport Guide"));
                if (!player.getInventory().insertStack(guide)) player.dropItem(guide, false);
            }
            player.sendMessage(Text.literal("[A&P CRAFT] Right-click your Airport Guide compass to locate the aviation hub. Press M for the mechanic menu."), false);
        }));

        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!stack.isOf(Items.COMPASS) || !stack.hasNbt() || !stack.getNbt().getBoolean(KEY))
                return TypedActionResult.pass(stack);
            if (!world.isClient() && player instanceof ServerPlayerEntity sp) {
                Path marker = sp.getServer().getSavePath(WorldSavePath.ROOT).resolve("apcraft-airfield-generated.txt");
                try {
                    if (!Files.exists(marker)) {
                        sp.sendMessage(Text.literal("[AIRPORT GUIDE] The airport is being generated. Explore nearby and check again shortly."), false);
                    } else {
                        String[] parts = Files.readString(marker).trim().split("\\s+");
                        if (parts.length < 6) throw new IllegalStateException("Invalid airfield location");
                        int x = Integer.parseInt(parts[3]);
                        int y = Integer.parseInt(parts[4]);
                        int z = Integer.parseInt(parts[5]);
                        sp.sendMessage(Text.literal("[AIRPORT GUIDE] Starter aviation hub: X=" + x + " Y=" + y + " Z=" + z + ". Visit the hangar, tool truck, aircraft dealer, and business offices."), false);
                        sp.sendMessage(Text.literal("[AIRPORT GUIDE] Use F3 to view your position; travel toward the coordinates. This compass is a guide, not a magnetic waypoint."), false);
                    }
                } catch (Exception ex) {
                    sp.sendMessage(Text.literal("[AIRPORT GUIDE] Airport coordinates unavailable. Check the world log for generation errors."), false);
                }
            }
            return TypedActionResult.success(stack, world.isClient());
        });
    }
}

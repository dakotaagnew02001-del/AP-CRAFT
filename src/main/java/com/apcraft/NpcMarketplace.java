package com.apcraft;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.WorldSavePath;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Villager-driven vehicle and property shopping; commands are only internal button actions. */
public final class NpcMarketplace {
    private NpcMarketplace() {}
    private static final String AIR = "APCraftAircraftDealer", CAR = "APCraftCarDealer", LAND = "APCraftRealtor";
    private static final String[] PROPERTIES = {"Starter Apartment", "Small House", "Country Home", "Private Hangar", "Airfield Property"};
    private static final int[] COST = {800, 3500, 12000, 18000, 55000};

    public static void initialize() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!(entity instanceof VillagerEntity villager)) return ActionResult.PASS;
            String tag = role(villager);
            if (tag == null) return ActionResult.PASS;
            if (!world.isClient && player instanceof ServerPlayerEntity p) menu(p, tag);
            return ActionResult.SUCCESS;
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) -> dispatcher.register(
            CommandManager.literal("marketplace")
                .then(CommandManager.literal("aircraft").executes(c -> { menu(c.getSource().getPlayerOrThrow(), AIR); return 1; }))
                .then(CommandManager.literal("cars").executes(c -> { menu(c.getSource().getPlayerOrThrow(), CAR); return 1; }))
                .then(CommandManager.literal("property").executes(c -> { menu(c.getSource().getPlayerOrThrow(), LAND); return 1; }))
                .then(CommandManager.literal("buyproperty").then(CommandManager.argument("id", IntegerArgumentType.integer(1, PROPERTIES.length))
                    .executes(c -> buyProperty(c.getSource().getPlayerOrThrow(), IntegerArgumentType.getInteger(c,"id")-1))))));
    }
    private static String role(VillagerEntity v) {
        for (String s : new String[]{AIR,CAR,LAND}) if (v.getCommandTags().contains(s)) return s;
        return null;
    }
    private static void line(ServerPlayerEntity p, String message) { p.sendMessage(Text.literal("[A&P MARKET] " + message), false); }
    private static void option(ServerPlayerEntity p, String label, String cmd) {
        p.sendMessage(Text.literal("  " + label + "  [SELECT]").setStyle(Style.EMPTY.withColor(Formatting.AQUA)
            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))), false);
    }
    private static boolean closeTo(ServerPlayerEntity p, String tag) {
        return !p.getServerWorld().getEntitiesByClass(VillagerEntity.class, p.getBoundingBox().expand(8),
            v -> v.getCommandTags().contains(tag)).isEmpty();
    }
    private static void menu(ServerPlayerEntity p, String tag) {
        if (!closeTo(p, tag)) { line(p,"Visit the dealer in person to browse and purchase."); return; }
        if (tag.equals(LAND)) {
            line(p,"REAL ESTATE — Select a property to buy. Each deed is saved to your world.");
            for (int i=0;i<PROPERTIES.length;i++) option(p, PROPERTIES[i] + " — $" + COST[i], "/marketplace buyproperty " + (i+1));
        } else if (tag.equals(CAR)) {
            line(p,"VEHICLE DEALER — Click a vehicle to see the fleet catalog. Purchases currently use prototype models.");
            option(p,"Browse cars and utility vehicles", "/fleet shop");
            for(int id=1;id<=VehicleFleet.catalogSize();id++)
                if(VehicleFleet.isGroundModel(id))
                    option(p,VehicleFleet.modelName(id)+" ["+VehicleFleet.vehicleCategory(id)+"] — $"+VehicleFleet.modelPrice(id),"/fleet buy "+id);
        } else {
            line(p,"AIRCRAFT SALES — Select an aircraft. Models are currently prototypes.");
            line(p,"Aircraft catalog: "+(35)+" models. Select a listing to purchase.");
            for(int id=3;id<=37;id++)
                option(p,VehicleFleet.modelName(id)+" — $"+VehicleFleet.modelPrice(id),"/fleet buy "+id);
        }
    }
    private static int buyProperty(ServerPlayerEntity p, int i) {
        if (!closeTo(p, LAND)) { line(p,"Speak to the realtor to buy property."); return 0; }
        Path file = deedPath(p, i);
        if (Files.exists(file)) { line(p,"You already own this property deed."); return 0; }
        if (p.getInventory().getEmptySlot()<0) { line(p,"Make room for the property deed."); return 0; }
        if (!APCraft.purchaseTrainingTool(p,COST[i])) { line(p,"Insufficient cash: $"+COST[i]); return 0; }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, p.getUuid()+"\n"+PROPERTIES[i]+"\n"+COST[i]);
        } catch (Exception e) {
            APCraft.refundFleet(p,COST[i]);
            line(p,"Could not save ownership. No money was charged."); return 0;
        }
        ItemStack deed = new ItemStack(Items.PAPER);
        deed.setCustomName(Text.literal("Property Deed: "+PROPERTIES[i]));
        p.getInventory().insertStack(deed);
        line(p,"Purchased "+PROPERTIES[i]+". Ownership saved! This is a deed/ownership prototype; land plots and buildings are not yet implemented.");
        return 1;
    }
    private static Path deedPath(ServerPlayerEntity p, int i) {
        return p.getServer().getSavePath(WorldSavePath.ROOT).resolve("apcraft-properties")
            .resolve(p.getUuid().toString()+"-"+(i+1)+".txt");
    }
    public static void spawnAtAirfield(ServerWorld w, int x, int y, int z) {
        spawn(w, x+6,y,z+12,"Aircraft Sales",AIR);
        spawn(w, x+13,y,z+12,"Vehicle Sales",CAR);
        spawn(w, x+18,y,z+12,"Real Estate Agent",LAND);
    }
    private static void spawn(ServerWorld w,double x,double y,double z,String name,String tag) {
        VillagerEntity v=EntityType.VILLAGER.create(w);
        if(v==null) return;
        v.refreshPositionAndAngles(x+0.5,y,z+0.5,0,0);
        v.setCustomName(Text.literal(name)); v.setCustomNameVisible(true);
        v.addCommandTag(tag); v.setPersistent(); w.spawnEntity(v);
    }
}

package com.apcraft;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.entity.vehicle.ChestBoatEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.util.ActionResult;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import java.util.*;

/** Experimental rideable vehicle prototypes; vanilla boats are temporary visual placeholders. */
public final class VehicleFleet {
    private VehicleFleet() {}
    // Append-only catalog: preserve existing IDs so player purchases and NPC links remain compatible.
    private static final String[] NAMES={"Utility Cart","Pickup Truck","Trainer Airplane","Utility Helicopter","Cessna 172 Skyhawk","Piper PA-23 Apache","Cessna 310","AH-64 Apache","Piper PA-28 Cherokee","Beechcraft Baron 58","Cessna 182 Skylane","Bell 206 JetRanger","Cessna 150","Cessna 152","Cessna 206 Stationair","Cessna 208 Caravan","Cessna 421 Golden Eagle","Piper PA-18 Super Cub","Piper PA-32 Saratoga","Piper PA-44 Seminole","Beechcraft King Air 200","Pilatus PC-12","Daher TBM 900","Cirrus SR22","Diamond DA42","Air Tractor AT-502","Robinson R22","Robinson R44","Bell 407","Airbus H125","Sikorsky UH-60 Black Hawk","Boeing CH-47 Chinook","Cessna Citation CJ3","Embraer Phenom 300","Bombardier Challenger 350","Boeing 737-800","Airbus A320","Classic VW Beetle","Compact Hatchback","Family Sedan","Station Wagon","Cruiser Motorcycle","Sport Motorcycle","Dual-Sport Motorcycle","Dirt Bike","Touring Motorcycle","Classic Muscle Car","Sports Coupe","Rally Sports Car","Supercar","Compact Pickup 4x2","Mid-Size 4x4 Pickup","Full-Size Half-Ton Pickup","Three-Quarter-Ton Pickup","One-Ton Dually Pickup","Utility Service Truck","Flatbed Work Truck","Heavy Commercial Truck","4x4 Trail SUV","Expedition 4x4","Rock Crawler"};
    private static final int[] PRICES={350,1800,8500,14500,12000,24000,29000,150000,10500,34000,18000,42000,7500,8500,27000,82000,62000,14500,25000,37000,125000,180000,215000,58000,72000,95000,29000,53000,115000,145000,240000,320000,275000,340000,525000,950000,1000000,950,1350,2100,2650,1600,3900,2850,1250,4800,6500,8800,12000,55000,3200,5800,8500,14500,19500,23000,27500,48000,6500,18500,12500};
    private static final String[] TYPES={"ground","ground","fixed","rotor","fixed","fixed","fixed","rotor","fixed","fixed","fixed","rotor","fixed","fixed","fixed","fixed","fixed","fixed","fixed","fixed","fixed","fixed","fixed","fixed","fixed","fixed","rotor","rotor","rotor","rotor","rotor","rotor","fixed","fixed","fixed","fixed","fixed","economy","economy","sedan","wagon","motorcycle","motorcycle","motorcycle","motorcycle","motorcycle","performance","performance","performance","performance","light_truck","offroad","light_truck","heavy_truck","heavy_truck","heavy_truck","heavy_truck","commercial","offroad","offroad","offroad"};
    public static int catalogSize(){return NAMES.length;}
    public static String modelName(int id){return NAMES[id-1];}
    public static int modelPrice(int id){return PRICES[id-1];}
    public static boolean isAircraftModel(int id){return id>=3 && id<=37;}
    public static boolean isGroundModel(int id){return id==1 || id==2 || id>=38 && id<=NAMES.length;}
    public static String vehicleCategory(int id){return TYPES[id-1];}
    public static int groundModelCount(){return NAMES.length-35;}
    /** Work trucks and family haulers have an accessible 27-slot cargo inventory. */
    private static boolean cargoVehicle(int id) {
        if (id == 2) return true;
        if (id < 38) return false;
        String type = TYPES[id - 1];
        return type.equals("light_truck") || type.equals("heavy_truck")
            || type.equals("commercial") || type.equals("offroad")
            || type.equals("wagon") || type.equals("sedan");
    }
    private static final Map<UUID, Integer> THROTTLE=new HashMap<>();
    private static final Set<UUID> FLYING=new HashSet<>();
    private static void say(ServerPlayerEntity p,String message){p.sendMessage(Text.literal("[A&P FLEET] "+message),false);}
    private static BoatEntity ownedVehicle(ServerPlayerEntity p){
        Entity e=p.getVehicle();
        if (!(e instanceof BoatEntity boat)) return null;
        return boat.getCommandTags().contains("apcraft_owner_"+p.getUuid()) ? boat : null;
    }
    private static boolean aircraft(BoatEntity b){return b.getCommandTags().contains("apcraft_aircraft");}
    /**
     * Conservative one-block curb assist for temporary boat-based ground vehicles.
     * Only climbs when there is a genuine horizontal collision, enough headroom,
     * and a solid landing surface. Never applies to aircraft or unoccupied boats.
     */
    private static void assistGroundStep(BoatEntity boat) {
        if (!boat.horizontalCollision || !boat.isOnGround() || aircraft(boat)) return;
        if (!boat.getCommandTags().contains("apcraft_fleet") || !isGroundBoat(boat)) return;
        Vec3d motion = boat.getVelocity();
        Vec3d horizontal = new Vec3d(motion.x, 0, motion.z);
        if (horizontal.lengthSquared() < 0.0004) return;
        // Check a short distance in the direction of travel, rather than teleporting
        // through a wall. Headroom must be clear at the elevated position.
        Vec3d ahead = horizontal.normalize().multiply(0.32);
        double rise = 1.01;
        var raised = boat.getBoundingBox().offset(ahead.x, rise, ahead.z);
        if (!boat.getWorld().isSpaceEmpty(boat, raised)) return;
        // Require support just beneath the new footprint; avoids floating over gaps.
        if (boat.getWorld().isSpaceEmpty(boat, raised.offset(0, -0.12, 0))) return;
        // Do not force movement when the vehicle has already reached the step height.
        boat.requestTeleport(boat.getX() + ahead.x, boat.getY() + rise, boat.getZ() + ahead.z);
        boat.setVelocity(motion.x, Math.max(0, motion.y), motion.z);
        boat.fallDistance = 0;
    }

    private static boolean isGroundBoat(BoatEntity boat) {
        for (String tag : boat.getCommandTags())
            if (tag.startsWith("apcraft_ground_class_")) return true;
        // Original utility cart and pickup predate the ground-class tags.
        return boat.getCommandTags().contains("apcraft_model_1")
            || boat.getCommandTags().contains("apcraft_model_2");
    }

    public static void initialize(){
        // Cargo-capable ground vehicles are chest boat prototypes: their 27-slot vanilla cargo
        // inventory persists with the entity, including shulker-box contents.
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (hand != net.minecraft.util.Hand.MAIN_HAND || !(entity instanceof ChestBoatEntity truck)
                    || !truck.getCommandTags().contains("apcraft_fleet") || truck.getCommandTags().contains("apcraft_aircraft")) return ActionResult.PASS;
            if (!truck.getCommandTags().contains("apcraft_owner_" + player.getUuid())) {
                if (!world.isClient) player.sendMessage(Text.literal("This pickup belongs to another player."), true);
                return ActionResult.FAIL;
            }
            ItemStack held = player.getStackInHand(hand);
            if (!(held.getItem() instanceof BlockItem blockItem)
                    || !(blockItem.getBlock() instanceof ShulkerBoxBlock)) return ActionResult.PASS;
            if (world.isClient) return ActionResult.SUCCESS;
            ItemStack cargo = held.copy();
            cargo.setCount(1);
            // Preserve the complete BlockEntityTag, including tools in the box.
            int free = -1;
            for (int i=0; i<truck.size(); i++) if (truck.getStack(i).isEmpty()) { free=i; break; }
            if (free < 0) { player.sendMessage(Text.literal("Vehicle cargo is full."), true); return ActionResult.FAIL; }
            truck.setStack(free, cargo);
            if (!player.getAbilities().creativeMode) held.decrement(1);
            player.sendMessage(Text.literal("Toolbox loaded. Open the vehicle inventory to retrieve it."), true);
            return ActionResult.SUCCESS;
        });
        CommandRegistrationCallback.EVENT.register((dispatcher,registryAccess,environment)->dispatcher.register(CommandManager.literal("fleet")
            .then(CommandManager.literal("shop").executes(ctx->{
                ServerPlayerEntity p=ctx.getSource().getPlayerOrThrow();
                for(int i=0;i<NAMES.length;i++) say(p,(i+1)+". "+NAMES[i]+" ["+TYPES[i]+"] - $"+PRICES[i]);
                say(p,"Buy through the aircraft or vehicle dealer. Vehicle appears beside you. Right-click to board."); return 1;
            }))
            .then(CommandManager.literal("buy").then(CommandManager.argument("id",IntegerArgumentType.integer(1,NAMES.length)).executes(ctx->{
                ServerPlayerEntity p=ctx.getSource().getPlayerOrThrow(); int i=IntegerArgumentType.getInteger(ctx,"id")-1;
                if(!APCraft.purchaseTrainingTool(p,PRICES[i])){say(p,"Insufficient funds. Price: $"+PRICES[i]);return 0;}
                BoatEntity b=(cargoVehicle(i+1) ? EntityType.CHEST_BOAT : i==7 ? ApacheAircraft.APACHE : EntityType.BOAT).create(p.getServerWorld());
                if(b==null){APCraft.refundFleet(p,PRICES[i]);return 0;}
                b.refreshPositionAndAngles(p.getX()+2,p.getY(),p.getZ()+2,p.getYaw(),0);
                b.setCustomName(Text.literal(NAMES[i]+" | Owner: "+p.getName().getString()));b.setCustomNameVisible(true);
                b.addCommandTag("apcraft_owner_"+p.getUuid());b.addCommandTag("apcraft_fleet");
                if(i>=2 && i<37)b.addCommandTag("apcraft_aircraft");
                b.addCommandTag("apcraft_type_"+TYPES[i]);
                b.addCommandTag("apcraft_model_"+(i+1));
                if(i>=37){
                    // Distinct handling classes; visual models are still boat placeholders.
                    b.addCommandTag("apcraft_ground_class_"+TYPES[i]);
                    if(TYPES[i].equals("offroad") || TYPES[i].equals("motorcycle")) b.addCommandTag("apcraft_trail_capable");
                    if(TYPES[i].equals("heavy_truck") || TYPES[i].equals("commercial")) b.addCommandTag("apcraft_heavy_hauler");
                }
                b.setInvulnerable(false);
                if(!p.getServerWorld().spawnEntity(b)){APCraft.refundFleet(p,PRICES[i]);return 0;}
                say(p,"Purchased "+NAMES[i]+". Rideable prototype spawned. Ground vehicles use boat physics for now; aircraft use experimental flight controls.");return 1;
            })))
            .then(CommandManager.literal("fly").executes(ctx->{
                ServerPlayerEntity p=ctx.getSource().getPlayerOrThrow(); BoatEntity b=ownedVehicle(p);
                if(b==null||!aircraft(b)){say(p,"Board an aircraft you own first.");return 0;}
                FLYING.add(b.getUuid());THROTTLE.putIfAbsent(b.getUuid(),2);
                say(p,"Flight mode on. Look up to climb, down to descend. Set throttle with /fleet throttle 1-5.");return 1;
            }))
            .then(CommandManager.literal("throttle").then(CommandManager.argument("setting",IntegerArgumentType.integer(1,5)).executes(ctx->{
                ServerPlayerEntity p=ctx.getSource().getPlayerOrThrow(); BoatEntity b=ownedVehicle(p);
                if(b==null||!aircraft(b))return 0;
                int setting=IntegerArgumentType.getInteger(ctx,"setting");THROTTLE.put(b.getUuid(),setting);
                say(p,"Throttle "+setting+"/5");return 1;
            })))
            .then(CommandManager.literal("land").executes(ctx->{
                ServerPlayerEntity p=ctx.getSource().getPlayerOrThrow();BoatEntity b=ownedVehicle(p);
                if(b==null)return 0;FLYING.remove(b.getUuid());THROTTLE.remove(b.getUuid());
                say(p,"Flight mode off. Vanilla boat physics resumed; land near the ground to avoid falling.");return 1;
            }))));
        ServerTickEvents.END_SERVER_TICK.register(server->{
            for(ServerPlayerEntity p:server.getPlayerManager().getPlayerList()){
                BoatEntity b=ownedVehicle(p);
                if (b == null) continue;
                if (!FLYING.contains(b.getUuid())) { assistGroundStep(b); continue; }
                if (ApacheFlightSystems.fly(p,b)) continue;
                Vec3d look=p.getRotationVec(1.0f);
                boolean rotor=b.getCommandTags().contains("apcraft_type_rotor");
                double speed=(rotor?0.07:0.14)+THROTTLE.getOrDefault(b.getUuid(),2)*(rotor?0.045:0.075);
                double climb=Math.max(-0.20,Math.min(0.20,look.y*speed));
                // Arcade physics only. Fixed-wing flight needs forward speed; rotorcraft may hover.
                if(!rotor && THROTTLE.getOrDefault(b.getUuid(),2)<2) climb=Math.min(climb,-0.07);
                Vec3d movement=new Vec3d(look.x*speed,climb,look.z*speed);
                b.setNoGravity(true);b.setVelocity(movement);b.velocityModified=true;
                b.fallDistance=0;
                // Move the vehicle directly: vanilla boat physics is not a flight model.
                b.requestTeleport(b.getX()+movement.x,b.getY()+movement.y,b.getZ()+movement.z);
            }
        });
    }
}

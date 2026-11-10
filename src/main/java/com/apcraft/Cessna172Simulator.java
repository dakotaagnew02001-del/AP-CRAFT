package com.apcraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Interactive educational aircraft systems demonstrator. NOT approved maintenance data. */
public final class Cessna172Simulator {
    private static final String TAG = "APCraftC172Maintenance";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type STATE_TYPE = new TypeToken<Map<String, Aircraft>>(){}.getType();
    private static final Map<String, Aircraft> AIRCRAFT = new HashMap<>();
    private static MinecraftServer server;
    private Cessna172Simulator() {}

    private static class Aircraft {
        boolean cowlingOpen = false;
        boolean batteryOn = false;
        boolean engineRunning = false;
        boolean ignitionGrounded = true;
        boolean compressionTested = false;
        boolean timingChecked = false;
        boolean repaired = false;
        boolean operationalCheck = false;
        boolean signedOff = false;
        int cylinder1 = 58;
        int cylinder2 = 76;
        int cylinder3 = 74;
        int cylinder4 = 75;
        int oilPressure = 0;
        int busVoltage = 0;
        String discrepancy = "Rough engine operation reported during magneto check";
    }

    public static void initialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {server = s; load();});
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> save());
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {server = null; AIRCRAFT.clear();});
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!(entity instanceof VillagerEntity v) || !v.getCommandTags().contains(TAG)) return ActionResult.PASS;
            if (!world.isClient && player instanceof ServerPlayerEntity p) menu(p);
            return ActionResult.SUCCESS;
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
            dispatcher.register(CommandManager.literal("c172")
                .then(CommandManager.literal("inspect").executes(c -> {menu(c.getSource().getPlayerOrThrow()); return 1;}))
                .then(CommandManager.literal("action").then(CommandManager.argument("id", IntegerArgumentType.integer(1,12))
                    .executes(c -> action(c.getSource().getPlayerOrThrow(), IntegerArgumentType.getInteger(c,"id")))))));
    }

    private static Path file() {return server.getSavePath(WorldSavePath.ROOT).resolve("apcraft-c172-aircraft.json");}
    private static void load() {
        AIRCRAFT.clear();
        try {if(Files.exists(file())) {
            Map<String,Aircraft> data = GSON.fromJson(Files.readString(file()), STATE_TYPE);
            if(data != null) AIRCRAFT.putAll(data);
        }} catch(Exception e) {System.err.println("A&P Craft C172 load error: "+e);}
    }
    private static void save() {
        if(server==null)return;
        try {Files.writeString(file(),GSON.toJson(AIRCRAFT));}
        catch(Exception e) {System.err.println("A&P Craft C172 save error: "+e);}
    }
    private static VillagerEntity station(ServerPlayerEntity p) {
        return p.getServerWorld().getEntitiesByClass(VillagerEntity.class,
            p.getBoundingBox().expand(7), v -> v.getCommandTags().contains(TAG))
            .stream().min((a,b) -> Double.compare(a.squaredDistanceTo(p),b.squaredDistanceTo(p))).orElse(null);
    }
    private static String key(VillagerEntity v) {
        return v.getWorld().getRegistryKey().getValue()+":"+v.getBlockX()+":"+v.getBlockY()+":"+v.getBlockZ();
    }
    private static Aircraft aircraft(VillagerEntity v) {return AIRCRAFT.computeIfAbsent(key(v),k->new Aircraft());}
    private static void say(ServerPlayerEntity p,String s) {p.sendMessage(Text.literal("[C172 TRAINER] "+s),false);}
    private static void option(ServerPlayerEntity p,int id,String label) {
        p.sendMessage(Text.literal("  ["+id+"] "+label).setStyle(Style.EMPTY.withColor(Formatting.AQUA)
            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/c172 action "+id))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,Text.literal("Select "+label)))),false);
    }
    private static boolean tool(ServerPlayerEntity p,String id) {
        Identifier target = new Identifier("apcraft",id);
        for(int i=0;i<p.getInventory().size();i++)
            if(Registries.ITEM.getId(p.getInventory().getStack(i).getItem()).equals(target))return true;
        return false;
    }
    private static void menu(ServerPlayerEntity p) {
        VillagerEntity v=station(p);
        if(v==null){say(p,"Walk up to the Cessna 172 maintenance station at an airfield.");return;}
        Aircraft a=aircraft(v);
        say(p,"N172AP | Training aircraft | "+a.discrepancy);
        say(p,"Cowling: "+(a.cowlingOpen?"OPEN":"CLOSED")+" | Battery: "+(a.batteryOn?"ON":"OFF")+
            " | Engine: "+(a.engineRunning?"RUNNING":"STOPPED")+" | Bus: "+a.busVoltage+" V | Oil: "+a.oilPressure+" psi");
        say(p,"Compression test: "+(a.compressionTested?"recorded":"pending")+" | Ignition timing: "+(a.timingChecked?"checked":"pending")+
            " | Repair: "+(a.repaired?"complete":"pending")+" | Sign-off: "+(a.signedOff?"recorded":"pending"));
        option(p,1,"Open/close engine cowling");
        option(p,2,"Battery master ON/OFF");
        option(p,3,"Perform differential compression test");
        option(p,4,"Check magneto timing");
        option(p,5,"Repair cylinder discrepancy (training scenario)");
        option(p,6,"Perform engine run-up / operational check");
        option(p,7,"Stop engine");
        option(p,8,"Read maintenance log / record work");
        option(p,9,"View electrical and engine instrument indications");
        option(p,10,"Ground/secure ignition");
        option(p,11,"Prepare ignition for engine operation");
        option(p,12,"Reset training scenario");
    }
    private static int action(ServerPlayerEntity p,int id) {
        VillagerEntity v=station(p);
        if(v==null){say(p,"You must be within 7 blocks of the aircraft station.");return 0;}
        Aircraft a=aircraft(v);
        switch(id) {
            case 1 -> {
                if(a.engineRunning){say(p,"Stop the engine before opening the cowling.");return 0;}
                a.cowlingOpen=!a.cowlingOpen;
                say(p,a.cowlingOpen?"Cowling opened. Engine components accessible.":"Cowling secured for ground operation.");
            }
            case 2 -> {
                if(a.engineRunning){say(p,"Shut down the engine before changing the master switch in this trainer.");return 0;}
                a.batteryOn=!a.batteryOn;
                a.busVoltage=a.batteryOn?24:0;
                say(p,"Battery master "+(a.batteryOn?"ON; electrical bus energized.":"OFF; electrical bus de-energized."));
            }
            case 3 -> {
                if(!a.cowlingOpen||a.engineRunning||a.batteryOn||!a.ignitionGrounded){say(p,"Safety interlock: cowling open, engine stopped, master OFF, ignition secured required.");return 0;}
                if(!tool(p,"compression_tester")){say(p,"Retrieve a differential compression tester from your toolbox.");return 0;}
                a.compressionTested=true;
                say(p,"SIMULATED readings (80 psi supply): #1 "+a.cylinder1+"/80, #2 "+a.cylinder2+"/80, #3 "+a.cylinder3+"/80, #4 "+a.cylinder4+"/80.");
                say(p,"#1 has a simulated exhaust-valve leak. Interpret findings using the applicable engine instructions; no universal pass/fail threshold is implied.");
            }
            case 4 -> {
                if(!a.cowlingOpen||a.engineRunning||a.batteryOn||!a.ignitionGrounded){say(p,"Secure the engine, electrical power, and ignition before this trainer check.");return 0;}
                if(!tool(p,"timing_light")){say(p,"You need an A&P Craft timing light.");return 0;}
                a.timingChecked=true;
                say(p,"Timing check complete. Magneto-to-engine timing requires verification against the applicable engine/airframe instructions.");
            }
            case 5 -> {
                if(!a.compressionTested||!a.timingChecked){say(p,"Diagnose the compression discrepancy and check ignition timing first.");return 0;}
                if(!a.cowlingOpen||a.engineRunning||a.batteryOn||!a.ignitionGrounded){say(p,"Aircraft must be secured and cowling open.");return 0;}
                if(!tool(p,"torque_wrench")){say(p,"You need a torque wrench for the simulated component replacement.");return 0;}
                a.cylinder1=75; a.repaired=true; a.operationalCheck=false; a.signedOff=false;
                say(p,"TRAINING repair: replacement component installed and fasteners verified against scenario data. Cylinder #1 now 75/80 (simulated). Real maintenance requires approved procedures and additional inspections.");
            }
            case 6 -> {
                if(!a.repaired||a.cowlingOpen||!a.batteryOn||a.ignitionGrounded){say(p,"Before run-up: complete repair, secure cowling, master ON, and prepare ignition.");return 0;}
                a.engineRunning=true; a.busVoltage=28; a.oilPressure=75;
                a.operationalCheck=true;
                say(p,"SIMULATED engine running: 1,700 RPM | 75 psi oil | 28 V bus | magneto drop within scenario limits. Engine operational check recorded. Stop engine before log entry.");
            }
            case 7 -> {a.engineRunning=false;a.oilPressure=0;a.busVoltage=a.batteryOn?24:0;say(p,"Engine stopped; oil pressure 0 psi.");}
            case 8 -> {
                if(a.engineRunning){say(p,"Stop engine before completing the maintenance record.");return 0;}
                if(!a.repaired||!a.operationalCheck){say(p,"Record incomplete: repair and post-maintenance operational check required.");return 0;}
                a.signedOff=true;
                say(p,"TRAINING LOG ENTRY: N172AP — simulated cylinder discrepancy corrected, post-maintenance check satisfactory. Training record only; NOT a legal maintenance release or FAA approval for return to service.");
            }
            case 9 -> say(p,"TRAINER instruments: Bus "+a.busVoltage+" V, oil pressure "+a.oilPressure+" psi, engine "+(a.engineRunning?"RUNNING":"STOPPED")+", #1 compression "+(a.compressionTested?a.cylinder1+"/80":"not tested")+".");
            case 10 -> {
                if(a.engineRunning){say(p,"Stop the engine before securing ignition.");return 0;}
                a.ignitionGrounded=true;say(p,"Ignition secured for training maintenance. Always follow actual aircraft safety procedures.");
            }
            case 11 -> {
                if(a.cowlingOpen){say(p,"Close and secure cowling first.");return 0;}
                a.ignitionGrounded=false;say(p,"Ignition enabled for ground-operation scenario.");
            }
            case 12 -> {
                if(a.engineRunning){say(p,"Shut down engine before resetting.");return 0;}
                AIRCRAFT.put(key(v),new Aircraft());say(p,"Training scenario reset; original discrepancy restored.");
            }
            default -> {return 0;}
        }
        save();
        return 1;
    }
    public static void spawnAtAirfield(ServerWorld w,int x,int y,int z) {
        VillagerEntity npc=EntityType.VILLAGER.create(w);
        if(npc==null)return;
        npc.refreshPositionAndAngles(x+12.5,y,z+10.5,0,0);
        npc.setCustomName(Text.literal("Cessna 172 — Maintenance Station"));
        npc.setCustomNameVisible(true);
        npc.addCommandTag(TAG);
        npc.setPersistent();
        w.spawnEntity(npc);
    }
}

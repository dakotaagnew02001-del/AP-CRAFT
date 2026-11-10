package com.apcraft;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.item.Items;
import net.minecraft.item.Item;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import java.util.*;

/** Arcade Apache crew trainer. Uses vanilla boat seats; no real weapons or targeting data. */
public final class ApacheCrewSystems {
    private ApacheCrewSystems() {}
    private static final String TAG = "apcraft_model_8";
    private static final Map<UUID, Integer> DAMAGE = new HashMap<>();
    private static final Map<UUID, Integer> SENSOR = new HashMap<>();
    private static final Map<UUID, Long> LAST_HIT = new HashMap<>();
    // Persistent, per-aircraft component condition is encoded in entity command tags.
    // These tags are saved with the boat entity and survive server restarts.
    private static final String[] COMPONENTS={"main_rotor", "tail_rotor", "transmission", "hydraulics", "turbine"};
    private static final String[] LABELS={"Main rotor", "Tail rotor", "Transmission", "Hydraulic system", "Turbine engine"};
    private static final Item[] SERVICE_TOOLS={Items.IRON_INGOT,Items.IRON_INGOT,Items.IRON_PICKAXE,Items.BUCKET,Items.BLAZE_ROD};
    private static int condition(BoatEntity b,int i) {
        for(String tag:b.getCommandTags()) {
            String prefix="apcraft_ah64_"+COMPONENTS[i]+"_";
            if(tag.startsWith(prefix)) {
                try{return Math.max(0,Math.min(100,Integer.parseInt(tag.substring(prefix.length()))));}
                catch(NumberFormatException ignored){}
            }
        }
        return 100;
    }
    /** Read-only condition interface used by flight simulation. Saved in entity tags. */
    public static int componentCondition(BoatEntity b, int index) {
        if (index < 0 || index >= COMPONENTS.length) return 100;
        return condition(b,index);
    }
    public static int airframeCondition(BoatEntity b) {
        return Math.max(0,100-DAMAGE.getOrDefault(b.getUuid(),0));
    }
    private static void setCondition(BoatEntity b,int i,int value) {
        String prefix="apcraft_ah64_"+COMPONENTS[i]+"_";
        for(String tag:new HashSet<>(b.getCommandTags())) if(tag.startsWith(prefix))b.removeCommandTag(tag);
        b.addCommandTag(prefix+Math.max(0,Math.min(100,value)));
    }
    private static boolean grounded(BoatEntity b) {return b.isOnGround() && b.getVelocity().lengthSquared()<0.04;}
    private static void inspectComponents(ServerPlayerEntity p,BoatEntity b) {
        message(p,"AH-64 COMPONENT CONDITION (training values):");
        for(int i=0;i<COMPONENTS.length;i++) {
            message(p,LABELS[i]+": "+condition(b,i)+"% | tool: "+SERVICE_TOOLS[i].getName().getString());
            action(p,"Service "+LABELS[i],"/apachecrew service "+(i+1));
        }
    }
    private static int service(ServerPlayerEntity p,int index) {
        BoatEntity b=apache(p);
        if(b==null){message(p,"Board the Apache to access its maintenance panel.");return 0;}
        if(!b.getCommandTags().contains("apcraft_owner_"+p.getUuid())){message(p,"Owner authorization required.");return 0;}
        if(!grounded(b)){message(p,"Stop and secure the aircraft on the ground before servicing.");return 0;}
        int i=index-1;
        if(condition(b,i)>=100){message(p,LABELS[i]+" needs no repair.");return 1;}
        if(!p.getMainHandStack().isOf(SERVICE_TOOLS[i])){message(p,"Hold "+SERVICE_TOOLS[i].getName().getString()+" to service "+LABELS[i]+".");return 0;}
        int price=(100-condition(b,i))*4;
        if(!APCraft.purchaseTrainingTool(p,price)){message(p,"Repair parts cost $"+price+".");return 0;}
        setCondition(b,i,100);
        message(p,LABELS[i]+" serviced. Cost $"+price+". This is a simplified training repair, not a real maintenance procedure.");
        return 1;
    }
    private static final String[] MODES = {"DAY", "NIGHT", "THERMAL (SIMULATED)"};
    private static BoatEntity apache(ServerPlayerEntity p) {
        Entity e = p.getVehicle();
        return e instanceof BoatEntity b && b.getCommandTags().contains(TAG) ? b : null;
    }
    private static void message(ServerPlayerEntity p, String msg) { p.sendMessage(Text.literal("[AH-64] " + msg), false); }
    private static void action(ServerPlayerEntity p, String label, String cmd) {
        p.sendMessage(Text.literal("  ["+label+"]").setStyle(Style.EMPTY.withColor(Formatting.AQUA)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))), false);
    }
    private static void panel(ServerPlayerEntity p, BoatEntity b) {
        int damage = DAMAGE.getOrDefault(b.getUuid(), 0);
        int mode = SENSOR.getOrDefault(b.getUuid(), 0);
        int crew = b.getPassengerList().size();
        message(p, "CREW " + crew + "/2 | AIRFRAME " + (100-damage) + "% | SENSOR " + MODES[mode]);
        message(p, "Pilot and gunner share the two vanilla boat seats. This is a training prototype.");
        action(p, "Flight controls and instruments", "/apacheflight panel");
        action(p, "Inspect components", "/apachecrew components");
        action(p, "Cycle sensor", "/apachecrew sensor");
        action(p, "Check aircraft damage", "/apachecrew inspect");
        action(p, "Service aircraft at hangar", "/apachecrew repair");
    }
    public static void initialize() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!(entity instanceof BoatEntity b) || !b.getCommandTags().contains(TAG)
                    || !player.isSneaking()) return ActionResult.PASS;
            if (!world.isClient && player instanceof ServerPlayerEntity p) panel(p, b);
            return ActionResult.SUCCESS;
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> dispatcher.register(
            CommandManager.literal("apachecrew")
                .then(CommandManager.literal("panel").executes(ctx -> {
                    ServerPlayerEntity p=ctx.getSource().getPlayerOrThrow(); BoatEntity b=apache(p);
                    if(b==null){message(p,"Board an Apache to use the cockpit panel.");return 0;}
                    panel(p,b);return 1;
                }))
                .then(CommandManager.literal("components").executes(ctx -> {
                    ServerPlayerEntity p=ctx.getSource().getPlayerOrThrow();BoatEntity b=apache(p);
                    if(b==null)return 0;inspectComponents(p,b);return 1;
                }))
                .then(CommandManager.literal("service").then(CommandManager.argument("component",IntegerArgumentType.integer(1,5))
                    .executes(ctx -> service(ctx.getSource().getPlayerOrThrow(),IntegerArgumentType.getInteger(ctx,"component")))))
                .then(CommandManager.literal("sensor").executes(ctx -> {
                    ServerPlayerEntity p=ctx.getSource().getPlayerOrThrow();BoatEntity b=apache(p);
                    if(b==null)return 0;
                    int next=(SENSOR.getOrDefault(b.getUuid(),0)+1)%MODES.length;
                    SENSOR.put(b.getUuid(),next);
                    message(p,"Sensor mode: "+MODES[next]+". Thermal is an informational overlay, not thermal rendering.");
                    return 1;
                }))
                .then(CommandManager.literal("inspect").executes(ctx -> {
                    ServerPlayerEntity p=ctx.getSource().getPlayerOrThrow();BoatEntity b=apache(p);
                    if(b==null)return 0;
                    int d=DAMAGE.getOrDefault(b.getUuid(),0);
                    inspectComponents(p,b);
                    message(p,"Airframe condition: "+(100-d)+"%. "+(d>35?"Maintenance required before dispatch.":"No major structural discrepancy flagged."));return 1;
                }))
                .then(CommandManager.literal("repair").executes(ctx -> {
                    ServerPlayerEntity p=ctx.getSource().getPlayerOrThrow();BoatEntity b=apache(p);
                    if(b==null)return 0;
                    if(!b.getCommandTags().contains("apcraft_owner_"+p.getUuid())){
                        message(p,"Only the owner can authorize this training repair.");return 0;
                    }
                    if(!b.isOnGround()) {message(p,"Land the Apache before servicing it.");return 0;}
                    int d=DAMAGE.getOrDefault(b.getUuid(),0);
                    if(d==0){message(p,"No recorded airframe damage.");return 1;}
                    int cost=d*3;
                    if(!APCraft.purchaseTrainingTool(p,cost)) {message(p,"Repair materials cost $"+cost+".");return 0;}
                    DAMAGE.put(b.getUuid(),0);message(p,"Training repair complete. Paid $"+cost+". Airframe condition 100%.");return 1;
                }))
        ));
        // Collision training: record hard impacts, with a cooldown to avoid charging every tick.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long tick=server.getTicks();
            for(ServerPlayerEntity p:server.getPlayerManager().getPlayerList()) {
                BoatEntity b=apache(p); if(b==null)continue;
                UUID id=b.getUuid();
                if(b.horizontalCollision && b.getVelocity().horizontalLengthSquared()>0.02
                        && tick-LAST_HIT.getOrDefault(id,-1000L)>40) {
                    LAST_HIT.put(id,tick);
                    DAMAGE.put(id,Math.min(100,DAMAGE.getOrDefault(id,0)+10));
                    // Impacts can affect rotor and drivetrain condition independently.
                    setCondition(b,0,condition(b,0)-8);
                    setCondition(b,1,condition(b,1)-5);
                    setCondition(b,2,condition(b,2)-3);
                    message(p,"Impact detected. Inspect airframe before next flight.");
                }
                if(condition(b,0)<40 || condition(b,1)<40 || condition(b,2)<35 || condition(b,3)<30 || condition(b,4)<30) {
                    b.setVelocity(b.getVelocity().multiply(0.6));
                }
                if(DAMAGE.getOrDefault(id,0)>=75) {
                    b.setVelocity(b.getVelocity().multiply(0.75));
                }
            }
        });
    }
}

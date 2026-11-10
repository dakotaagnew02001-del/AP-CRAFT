package com.apcraft;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Simplified AH-64 flight-training controls, not flight-manual or engineering data. */
public final class ApacheFlightSystems {
    private ApacheFlightSystems() {}
    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final class State {
        boolean battery, engine1, engine2;
        int collective = 0, pedal = 0;
        double rotorRpm = 0;
        long lastWarningTick = -1000;
        double torqueDemand = 0;
        boolean caution = false;
    }
    private static State state(BoatEntity b) {return STATES.computeIfAbsent(b.getUuid(), ignored -> new State());}
    private static BoatEntity apache(ServerPlayerEntity p) {
        return p.getVehicle() instanceof BoatEntity b && b.getCommandTags().contains("apcraft_model_8") ? b : null;
    }
    private static void say(ServerPlayerEntity p, String msg) {p.sendMessage(Text.literal("[AH-64 FLIGHT] "+msg), false);}
    private static void link(ServerPlayerEntity p, String label, String cmd) {
        p.sendMessage(Text.literal("  ["+label+"]").setStyle(Style.EMPTY.withColor(Formatting.AQUA)
            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,cmd))), false);
    }
    public static void panel(ServerPlayerEntity p) {
        BoatEntity b=apache(p);
        if(b==null){say(p,"Board the Apache first.");return;}
        State s=state(b);
        say(p,"Battery "+(s.battery?"ON":"OFF")+" | Engine 1 "+(s.engine1?"RUN":"OFF")+" | Engine 2 "+(s.engine2?"RUN":"OFF"));
        say(p,"Rotor "+(int)s.rotorRpm+"% | Collective "+s.collective+"/5 | Pedals "+s.pedal);
        say(p,"Power demand "+(int)s.torqueDemand+"% | Flight caution "+(s.caution?"ACTIVE":"CLEAR"));
        link(p,"Component health and maintenance","/apachecrew components");
        link(p,"Toggle battery","/apacheflight battery");
        link(p,"Toggle engine 1","/apacheflight engine 1");
        link(p,"Toggle engine 2","/apacheflight engine 2");
        link(p,"Collective +","/apacheflight collective "+Math.min(5,s.collective+1));
        link(p,"Collective -","/apacheflight collective "+Math.max(0,s.collective-1));
        link(p,"Pedals left","/apacheflight pedal -1");
        link(p,"Pedals neutral","/apacheflight pedal 0");
        link(p,"Pedals right","/apacheflight pedal 1");
        link(p,"Flight instruments","/apacheflight instruments");
    }
    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> dispatcher.register(
            CommandManager.literal("apacheflight")
                .then(CommandManager.literal("panel").executes(ctx->{panel(ctx.getSource().getPlayerOrThrow());return 1;}))
                .then(CommandManager.literal("instruments").executes(ctx->{
                    ServerPlayerEntity p=ctx.getSource().getPlayerOrThrow(); BoatEntity b=apache(p);if(b==null)return 0;
                    State s=state(b);double speed=b.getVelocity().horizontalLength()*20;
                    say(p,String.format(java.util.Locale.US,"Rotor %.0f%% | Airspeed %.1f blocks/s | Altitude %.1f | Collective %d/5 | Yaw %d",s.rotorRpm,speed,b.getY(),s.collective,s.pedal));
                    say(p,"Power demand "+(int)s.torqueDemand+"% | Caution "+(s.caution?"ACTIVE":"CLEAR"));
                    for(int i=0;i<5;i++) say(p,new String[]{"Main rotor","Tail rotor","Transmission","Hydraulics","Turbine"}[i]+": "+ApacheCrewSystems.componentCondition(b,i)+"%");
                    say(p,"SIMULATED values only; not AH-64 performance data.");return 1;
                }))
                .then(CommandManager.literal("battery").executes(ctx->{
                    ServerPlayerEntity p=ctx.getSource().getPlayerOrThrow();BoatEntity b=apache(p);if(b==null)return 0;
                    State s=state(b);if(s.engine1||s.engine2){say(p,"Shut down engines before turning battery off.");return 0;}
                    s.battery=!s.battery;panel(p);return 1;
                }))
                .then(CommandManager.literal("engine").then(CommandManager.argument("number",IntegerArgumentType.integer(1,2)).executes(ctx->{
                    ServerPlayerEntity p=ctx.getSource().getPlayerOrThrow();BoatEntity b=apache(p);if(b==null)return 0;
                    State s=state(b);int n=IntegerArgumentType.getInteger(ctx,"number");
                    if(!s.battery){say(p,"Electrical power required.");return 0;}
                    if(n==1)s.engine1=!s.engine1;else s.engine2=!s.engine2;
                    say(p,"Engine "+n+" "+((n==1?s.engine1:s.engine2)?"started":"stopped")+" (training simulation).");panel(p);return 1;
                })))
                .then(CommandManager.literal("collective").then(CommandManager.argument("level",IntegerArgumentType.integer(0,5)).executes(ctx->{
                    ServerPlayerEntity p=ctx.getSource().getPlayerOrThrow();BoatEntity b=apache(p);if(b==null)return 0;
                    state(b).collective=IntegerArgumentType.getInteger(ctx,"level");panel(p);return 1;
                })))
                .then(CommandManager.literal("pedal").then(CommandManager.argument("direction",IntegerArgumentType.integer(-1,1)).executes(ctx->{
                    ServerPlayerEntity p=ctx.getSource().getPlayerOrThrow();BoatEntity b=apache(p);if(b==null)return 0;
                    state(b).pedal=IntegerArgumentType.getInteger(ctx,"direction");panel(p);return 1;
                })))
        ));
    }
    /** Called by the existing fleet movement loop. Returns true if this is a custom Apache. */
    public static boolean fly(ServerPlayerEntity pilot,BoatEntity b) {
        if(!(b instanceof ApacheEntity))return false;
        State s=state(b);
        int main=ApacheCrewSystems.componentCondition(b,0);
        int tail=ApacheCrewSystems.componentCondition(b,1);
        int gearbox=ApacheCrewSystems.componentCondition(b,2);
        int hydraulics=ApacheCrewSystems.componentCondition(b,3);
        int turbine=ApacheCrewSystems.componentCondition(b,4);
        int airframe=ApacheCrewSystems.airframeCondition(b);
        // Fictional failure envelopes for gameplay; NOT actual Apache limits.
        double enginePower=(s.engine1?0.5:0)+(s.engine2?0.5:0);
        double driveFactor=Math.min(main,Math.min(gearbox,turbine))/100.0;
        double target=100.0*enginePower*driveFactor;
        s.torqueDemand=s.collective*20.0/(Math.max(0.25,enginePower));
        if(s.collective>3 && turbine<65) target*=0.85;
        s.rotorRpm+=Math.max(-1.2,Math.min(1.2,(target-s.rotorRpm)*0.04));
        boolean weakHydraulics=hydraulics<45;
        boolean weakTail=tail<45;
        s.caution=main<65 || tail<65 || gearbox<65 || hydraulics<65 || turbine<65 || airframe<65 || s.rotorRpm<65 && s.collective>0;
        if(s.caution && pilot.getServerWorld().getTime()-s.lastWarningTick>=100) {
            s.lastWarningTick=pilot.getServerWorld().getTime();
            say(pilot,"CAUTION: Degraded systems. Check component health and land for maintenance.");
        }
        Vec3d look=pilot.getRotationVec(1);
        double controlFactor=weakHydraulics?0.30:1.0;
        double rotorFactor=Math.max(0,Math.min(1,(s.rotorRpm-45)/45));
        double forward=0.09*rotorFactor*controlFactor*(airframe<45?0.6:1.0);
        double lift=s.collective*0.052*rotorFactor-0.13;
        lift=Math.max(-0.16,Math.min(0.14,lift));
        Vec3d motion=new Vec3d(look.x*forward,lift,look.z*forward);
        b.setNoGravity(s.rotorRpm>65);
        b.setVelocity(motion);b.velocityModified=true;
        float yawAuthority=(float)(weakTail?0.25:1.8);
        b.setYaw((float)(b.getYaw()+s.pedal*yawAuthority*controlFactor));
        if(lift>0)b.fallDistance=0;
        b.requestTeleport(b.getX()+motion.x,b.getY()+motion.y,b.getZ()+motion.z);
        return true;
    }
}

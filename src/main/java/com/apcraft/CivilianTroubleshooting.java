package com.apcraft;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import java.nio.file.*;
import java.util.*;

/** Fictional civilian-aircraft diagnostic training, not approved maintenance instructions. */
public final class CivilianTroubleshooting {
    private CivilianTroubleshooting() {}
    private static final String[] AIRCRAFT = {"Cessna 172", "Beechcraft King Air 200", "Piper PA-28"};
    private static final String[] FAULTS = {"Ignition-related rough running", "DC electrical charging fault", "Pitot-static indication discrepancy"};
    // Each case requires a diagnostic selection, an interpretation, and a verification step.
    private static final String[][][] CASES = {
        {
            {"During a ground check the engine runs roughly with an abnormal ignition check. Which first action is appropriate?", "Review the aircraft checklist and applicable troubleshooting data", "Advance ignition timing without measuring", "Ignore the indication", "1", "Use the aircraft-specific checklist and maintenance data to establish the discrepancy."},
            {"A prescribed ignition test points to a magneto timing discrepancy. What is the correct response?", "Adjust by ear", "Inspect and correct only using applicable engine instructions", "Replace the propeller", "2", "Ignition adjustment and inspection are engine-specific and require applicable maintenance instructions."},
            {"Before closing the work order, what is necessary?", "Verify with the prescribed checks and document the work", "Assume the repair succeeded", "Erase the discrepancy", "1", "Verification and accurate records are part of the maintenance process."}
        },
        {
            {"The bus voltage remains low with engines operating. Which diagnostic source should you consult first?", "Applicable wiring diagram and troubleshooting instructions", "An unrelated vehicle manual", "A random replacement parts list", "1", "Trace the electrical system using type-specific data."},
            {"Before testing continuity or resistance, what must you do?", "Energize the circuit", "De-energize and isolate the circuit per instructions", "Bridge the circuit breaker", "2", "Never use an ohmmeter on an energized circuit."},
            {"After correcting the identified fault, what verifies the result?", "An unrecorded guess", "A larger circuit breaker", "Prescribed operational checks and maintenance record entry", "3", "Use approved or acceptable procedures and verify the charging system."}
        },
        {
            {"Airspeed and altitude indications disagree with expected behavior. What should be checked?", "The applicable pitot-static troubleshooting and inspection procedures", "Only the engine oil quantity", "Only the fuel caps", "1", "Instrument and line faults require controlled testing using applicable data."},
            {"Which practice is appropriate when working with pitot-static lines?", "Apply unlimited shop air", "Use specified test equipment and pressure limits", "Disconnect lines in flight", "2", "Excess pressure can damage sensitive instruments; use approved limits."},
            {"What is required after disturbing a static system, as applicable?", "Required leak/functional checks and proper records", "No checks if the airplane looks clean", "Only a compass swing", "1", "Required testing and documentation depend on the work and applicable regulations."}
        }
    };
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static class Session { int aircraft, caseId, stage, mistakes; Session(int a,int c){aircraft=a;caseId=c;} }
    private static Path file(ServerPlayerEntity p) {return p.getServer().getSavePath(WorldSavePath.ROOT).resolve("apcraft-civilian-training").resolve(p.getUuidAsString()+".txt");}
    private static void save(ServerPlayerEntity p){
        try { Files.createDirectories(file(p).getParent()); Session s=SESSIONS.get(p.getUuid());
            if(s==null) Files.deleteIfExists(file(p));
            else Files.writeString(file(p),s.aircraft+","+s.caseId+","+s.stage+","+s.mistakes);
        } catch(Exception e){say(p,"Unable to save training session: "+e.getMessage());}
    }
    private static Session session(ServerPlayerEntity p){
        Session s=SESSIONS.get(p.getUuid()); if(s!=null)return s;
        try {String[] a=Files.readString(file(p)).trim().split(",");
            int aircraft=Integer.parseInt(a[0]),caseId=Integer.parseInt(a[1]),stage=Integer.parseInt(a[2]),mistakes=Integer.parseInt(a[3]);
            if(aircraft<0||aircraft>=AIRCRAFT.length||caseId<0||caseId>=CASES.length||stage<0||stage>=3)return null;
            s=new Session(aircraft,caseId);s.stage=stage;s.mistakes=mistakes;SESSIONS.put(p.getUuid(),s);return s;
        } catch(Exception ignored){return null;}
    }
    private static void say(ServerPlayerEntity p,String msg){p.sendMessage(Text.literal("[CIVILIAN A&P] "+msg),false);}
    private static void show(ServerPlayerEntity p){
        Session s=session(p);if(s==null){say(p,"No active job. Use /diagnose aircraft then /diagnose start <aircraft 1-3> <fault 1-3>.");return;}
        String[] q=CASES[s.caseId][s.stage];
        say(p,AIRCRAFT[s.aircraft]+" | "+FAULTS[s.caseId]+" | Step "+(s.stage+1)+"/3 | Incorrect attempts: "+s.mistakes);
        say(p,q[0]);for(int i=1;i<=3;i++)say(p,i+") "+q[i]);say(p,"Answer: /diagnose choose <1-3>");
    }
    private static void choose(ServerPlayerEntity p,int answer){
        Session s=session(p);if(s==null){show(p);return;}
        String[] q=CASES[s.caseId][s.stage];
        if(answer!=Integer.parseInt(q[4])){s.mistakes++;say(p,"Not correct. "+q[5]);save(p);show(p);return;}
        say(p,"Correct. "+q[5]);s.stage++;
        if(s.stage==3){
            say(p,"TRAINING WORK ORDER COMPLETE: "+AIRCRAFT[s.aircraft]+" / "+FAULTS[s.caseId]+". Incorrect attempts: "+s.mistakes+". This is NOT an FAA maintenance sign-off.");
            SESSIONS.remove(p.getUuid());save(p);
        }else{save(p);show(p);}
    }
    public static void registerCommands(CommandDispatcher<ServerCommandSource> d){
        d.register(CommandManager.literal("diagnose")
            .then(CommandManager.literal("aircraft").executes(c->{ServerPlayerEntity p=c.getSource().getPlayerOrThrow();
                for(int i=0;i<AIRCRAFT.length;i++)say(p,(i+1)+") "+AIRCRAFT[i]);
                for(int i=0;i<FAULTS.length;i++)say(p,"Fault "+(i+1)+": "+FAULTS[i]);
                say(p,"/diagnose start <aircraft 1-3> <fault 1-3>");return 1;}))
            .then(CommandManager.literal("start")
                .then(CommandManager.argument("aircraft",IntegerArgumentType.integer(1,3))
                .then(CommandManager.argument("fault",IntegerArgumentType.integer(1,3)).executes(c->{
                    ServerPlayerEntity p=c.getSource().getPlayerOrThrow();
                    SESSIONS.put(p.getUuid(),new Session(IntegerArgumentType.getInteger(c,"aircraft")-1,IntegerArgumentType.getInteger(c,"fault")-1));
                    save(p);show(p);return 1;}))))
            .then(CommandManager.literal("choose").then(CommandManager.argument("answer",IntegerArgumentType.integer(1,3)).executes(c->{choose(c.getSource().getPlayerOrThrow(),IntegerArgumentType.getInteger(c,"answer"));return 1;})))
            .then(CommandManager.literal("status").executes(c->{show(c.getSource().getPlayerOrThrow());return 1;}))
            .then(CommandManager.literal("cancel").executes(c->{ServerPlayerEntity p=c.getSource().getPlayerOrThrow();SESSIONS.remove(p.getUuid());save(p);say(p,"Training session canceled.");return 1;})));
    }
}

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

/** Source-indexed FAA general knowledge and simulated practical exercises.
 *  These exercises do not authorize actual aircraft maintenance or replace type-specific data.
 */
public final class FaaAcademy {
    private FaaAcademy() {}
    private static final String GENERAL = "FAA-H-8083-30B";
    private static final String AIRFRAME = "FAA-H-8083-31B";
    private static final String POWERPLANT = "FAA-H-8083-32B";
    private static final String ACS = "FAA-S-ACS-1";
    // Format: category, title, FAA publication, handbook topic, ACS subject, question,
    // three choices, correct 1-3, explanation, simulated practical step.
    private static final String[][] LESSONS = {
        {"GENERAL", "Ohm's law", GENERAL, "Fundamentals of Electricity and Electronics", "General I.A",
         "In a circuit with constant resistance, what happens to current when applied voltage increases?",
         "Current increases", "Current decreases", "Current remains unchanged", "1",
         "Ohm's law relates voltage, current, and resistance (I = E/R).",
         "PRACTICAL SIMULATION: Identify the power source, isolate it, and select the appropriate meter function before a resistance check."},
        {"GENERAL", "Weight and balance", GENERAL, "Weight and Balance", "General I.C",
         "What is the basic relationship used to calculate an item's moment?",
         "Weight divided by arm", "Weight multiplied by arm", "Arm divided by weight", "2",
         "Moment equals weight multiplied by arm; use the aircraft's applicable datum and loading data.",
         "PRACTICAL SIMULATION: Calculate a 40 lb item at a 50 in arm: moment = 2,000 lb-in; confirm the arithmetic."},
        {"GENERAL", "Safety wiring", GENERAL, "Aircraft Materials, Hardware, and Processes", "General I.E",
         "How should installed safety wire tend to act on a threaded fastener?",
         "In the loosening direction", "Without regard to rotation", "In the tightening direction", "3",
         "Proper safety wiring restrains rotation toward loosening. Actual installation follows applicable maintenance instructions.",
         "PRACTICAL SIMULATION: Identify tightening direction and check wire routing; no universal wire size or torque is assumed."},
        {"GENERAL", "Maintenance records", GENERAL, "Regulations, Maintenance Forms, Records, and Publications", "General I.I",
         "Where should a mechanic look to determine the required content of a maintenance record entry?",
         "14 CFR 43.9 and applicable requirements", "An informal personal note", "A fuel receipt", "1",
         "14 CFR 43.9 specifies maintenance record entry requirements for applicable work; additional rules may apply.",
         "PRACTICAL SIMULATION: Identify description, completion date, and approving person's required information for a sample record."},
        {"AIRFRAME", "Control cable inspection", AIRFRAME, "Flight Controls / Aircraft Assembly and Rigging", "Airframe II.C",
         "Where must control cable tension limits be obtained for a specific aircraft?",
         "A universal value for all aircraft", "Applicable aircraft maintenance instructions", "The cable's color", "2",
         "Aircraft-specific rigging limits and procedures must come from applicable maintenance data.",
         "PRACTICAL SIMULATION: Inspect a depicted cable route for wear and identify where the specified tension value is found."},
        {"AIRFRAME", "Hydraulic systems", AIRFRAME, "Aircraft Hydraulic and Pneumatic Power Systems", "Airframe II.F",
         "Which principle explains transmission of pressure in a confined fluid?",
         "Pascal's law", "Bernoulli's equation alone", "Boyle's law alone", "1",
         "Pascal's law describes pressure applied to a confined fluid being transmitted throughout the fluid.",
         "PRACTICAL SIMULATION: Trace a schematic from reservoir to pump, selector valve and actuator; isolate pressure before service."},
        {"AIRFRAME", "Corrosion", AIRFRAME, "Aircraft Cleaning and Corrosion Control", "General I.G / Airframe II.A",
         "Which action is appropriate when corrosion is found during inspection?",
         "Ignore it if covered by paint", "Remove all nearby structure", "Evaluate and treat using applicable maintenance data", "3",
         "Corrosion identification, allowable removal and protection depend on the structure and approved or acceptable data.",
         "PRACTICAL SIMULATION: Classify a visible corrosion finding and select inspect, evaluate, document, and approved treatment stages."},
        {"POWERPLANT", "Four-stroke engine cycle", POWERPLANT, "Chapter 1: Aircraft Engines", "Powerplant III.A",
         "How many crankshaft revolutions complete one four-stroke engine cycle?",
         "One", "Two", "Four", "2",
         "Intake, compression, power, and exhaust strokes require two crankshaft revolutions.",
         "PRACTICAL SIMULATION: Arrange the four strokes and identify piston travel and valve states."},
        {"POWERPLANT", "Differential compression", POWERPLANT, "Chapter 10: Engine Maintenance and Operation", "Powerplant III.C",
         "Air escaping through the exhaust during a differential compression check can indicate leakage at which component?",
         "Exhaust valve", "Alternator", "Fuel strainer", "1",
         "Leakage audible at the exhaust can indicate exhaust valve sealing problems; follow the applicable engine inspection procedure and limits.",
         "PRACTICAL SIMULATION: Select the approved tester setup, cylinder position and applicable acceptance limits from engine data; no fixed limit is supplied."},
        {"POWERPLANT", "Magneto ignition", POWERPLANT, "Chapter 4: Engine Ignition and Electrical Systems", "Powerplant III.H",
         "What is used to check magneto-to-engine timing during an applicable maintenance procedure?",
         "A fuel hydrometer", "A pitot-static tester", "A magneto timing light", "3",
         "Magneto timing lights indicate breaker-point opening for timing checks; follow the engine's specified procedure.",
         "PRACTICAL SIMULATION: Identify timing reference, safe ignition isolation and verification steps from applicable instructions."},
        {"POWERPLANT", "Constant-speed propeller", POWERPLANT, "Chapter 7: Propellers", "Powerplant III.K",
         "Within its governing range, which blade-angle change tends to reduce an overspeeding propeller's RPM?",
         "Decrease blade angle", "Increase blade angle", "Move blades to reverse", "2",
         "Increasing blade angle increases aerodynamic load and tends to reduce RPM in a conventional constant-speed propeller.",
         "PRACTICAL SIMULATION: Identify governor, speeder spring and oil-flow functions in a simplified diagram."},
        {"POWERPLANT", "Turbine ignition", POWERPLANT, "Chapter 4: Engine Ignition and Electrical Systems", "Powerplant III.H",
         "Why are igniters generally needed during turbine engine starting?",
         "To initiate combustion", "To lubricate bearings", "To pressurize hydraulic fluid", "1",
         "Igniters provide ignition energy to establish combustion during start; operational details depend on engine design.",
         "PRACTICAL SIMULATION: Identify igniter, exciter and ignition lead; recognize stored-energy precautions."}
    };
    private static final String FAA_BOOKS = "https://www.faa.gov/regulations_policies/handbooks_manuals/aviation";
    private static final String FAA_ACS = "https://www.faa.gov/training_testing/testing/acs/Aviation_Mechanic_Certification_Standards.pdf";
    private static final Map<UUID, int[]> ACTIVE = new HashMap<>(); // lesson, phase (0 knowledge, 1 practical), misses
    private static Path file(ServerPlayerEntity p) { return p.getServer().getSavePath(WorldSavePath.ROOT).resolve("apcraft-faa-academy").resolve(p.getUuidAsString()+".txt"); }
    private static void say(ServerPlayerEntity p, String msg) { p.sendMessage(Text.literal("[FAA ACADEMY] " + msg), false); }
    private static void persist(ServerPlayerEntity p) {
        try { Files.createDirectories(file(p).getParent()); int[] s=ACTIVE.get(p.getUuid());
            if(s==null) Files.deleteIfExists(file(p)); else Files.writeString(file(p),s[0]+","+s[1]+","+s[2]);
        } catch(Exception e) { say(p,"Unable to save session: "+e.getMessage()); }
    }
    private static int[] current(ServerPlayerEntity p) {
        int[] s=ACTIVE.get(p.getUuid()); if(s!=null)return s;
        try {String[] parts=Files.readString(file(p)).trim().split(",");
            int a=Integer.parseInt(parts[0]),b=Integer.parseInt(parts[1]),c=Integer.parseInt(parts[2]);
            if(a<0||a>=LESSONS.length||b<0||b>1||c<0)return null;
            s=new int[]{a,b,c};ACTIVE.put(p.getUuid(),s);return s;
        } catch(Exception ignored) {return null;}
    }
    private static void show(ServerPlayerEntity p) {
        int[] s=current(p);if(s==null){say(p,"Use /faa lessons and /faa start <number>.");return;}
        String[] l=LESSONS[s[0]];
        say(p,l[0]+" | "+l[1]+" | "+l[2]+" | "+l[3]+" | ACS "+l[4]);
        if(s[1]==0) {say(p,l[5]);for(int i=0;i<3;i++)say(p,(i+1)+") "+l[6+i]);say(p,"Use /faa answer <1-3> | /faa source");}
        else {say(p,l[11]);say(p,"Review the practical stage, then /faa complete to acknowledge it as a SIMULATION.");}
    }
    private static void answer(ServerPlayerEntity p,int choice) {
        int[] s=current(p);if(s==null){show(p);return;}if(s[1]!=0){show(p);return;}
        String[] l=LESSONS[s[0]];
        if(choice!=Integer.parseInt(l[9])) {s[2]++;say(p,"Not quite. "+l[10]);persist(p);show(p);return;}
        say(p,"Knowledge check passed. "+l[10]);s[1]=1;persist(p);show(p);
    }
    private static void complete(ServerPlayerEntity p) {
        int[] s=current(p);if(s==null){show(p);return;}if(s[1]!=1){say(p,"Pass the knowledge check first.");return;}
        say(p,"SIMULATED PRACTICAL REVIEWED: "+LESSONS[s[0]][1]+". Incorrect answers: "+s[2]+". This is not a practical skill evaluation or maintenance sign-off.");
        ACTIVE.remove(p.getUuid());persist(p);
    }
    public static void registerCommands(CommandDispatcher<ServerCommandSource> d) {
        d.register(CommandManager.literal("faa")
            .then(CommandManager.literal("lessons").executes(c->{ServerPlayerEntity p=c.getSource().getPlayerOrThrow();for(int i=0;i<LESSONS.length;i++)say(p,(i+1)+") "+LESSONS[i][0]+" - "+LESSONS[i][1]);say(p,"/faa start <1-"+LESSONS.length+">");return 1;}))
            .then(CommandManager.literal("start").then(CommandManager.argument("lesson",IntegerArgumentType.integer(1,LESSONS.length)).executes(c->{ServerPlayerEntity p=c.getSource().getPlayerOrThrow();ACTIVE.put(p.getUuid(),new int[]{IntegerArgumentType.getInteger(c,"lesson")-1,0,0});persist(p);show(p);return 1;})))
            .then(CommandManager.literal("answer").then(CommandManager.argument("choice",IntegerArgumentType.integer(1,3)).executes(c->{answer(c.getSource().getPlayerOrThrow(),IntegerArgumentType.getInteger(c,"choice"));return 1;})))
            .then(CommandManager.literal("complete").executes(c->{complete(c.getSource().getPlayerOrThrow());return 1;}))
            .then(CommandManager.literal("status").executes(c->{show(c.getSource().getPlayerOrThrow());return 1;}))
            .then(CommandManager.literal("source").executes(c->{ServerPlayerEntity p=c.getSource().getPlayerOrThrow();int[] s=current(p);if(s!=null){String[] l=LESSONS[s[0]];say(p,"FAA source: "+l[2]+", "+l[3]+"; ACS subject "+l[4]);}say(p,"Official FAA handbooks: "+FAA_BOOKS);say(p,"Official FAA ACS: "+FAA_ACS);return 1;}))
            .then(CommandManager.literal("cancel").executes(c->{ServerPlayerEntity p=c.getSource().getPlayerOrThrow();ACTIVE.remove(p.getUuid());persist(p);say(p,"Session canceled.");return 1;})));
    }
}

package com.apcraft;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.block.Blocks;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.util.ActionResult;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.util.Formatting;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.WorldSavePath;
import java.nio.file.*;
import java.util.*;

/** Interactive, fictional practice stations based on concepts covered in FAA handbooks.
 * No station purports to reproduce an FAA practical test or type-specific maintenance data.
 */
public final class FaaPracticals {
    private FaaPracticals() {}
    private record Step(String prompt, String[] options, int correct, String explanation) {}
    private record Exercise(String title, String source, String topic, Step[] steps) {}
    private static Step s(String p, int correct, String explanation, String... options) { return new Step(p,options,correct,explanation); }
    private static final Exercise[] EXERCISES = {
        new Exercise("Safety-wire inspection", "FAA-H-8083-30B", "Aircraft Materials, Hardware, and Processes", new Step[]{
            s("Inspect the fictional bolt pair. What must be established before selecting a safety-wire route?",1,"Identify fastener tightening direction and the applicable installation instructions.","Fastener tightening direction and maintenance instructions","Paint color of the bolt heads","Number of nearby rivets"),
            s("The wire route would pull the first fastener toward loosening. Choose the inspection result.",2,"A safety-wire arrangement should tend to resist loosening, not promote it.","Accept and sign off","Reject and reroute per applicable data","Add grease to the wire"),
            s("Before returning the assembly to service, what should be verified?",3,"Check security, workmanship, and applicable maintenance instructions; a game check is not an approval for return to service.","Only wire color","Only whether the wire is shiny","Installation against applicable instructions")
        }),
        new Exercise("Multimeter bench", "FAA-H-8083-30B", "Fundamentals of Electricity and Electronics", new Step[]{
            s("You need to measure resistance on a fictional circuit. What is the first safety action?",2,"Remove power and follow equipment-specific precautions before resistance testing.","Set meter to AC amperes","De-energize and isolate the circuit","Bridge the battery terminals"),
            s("Which meter function is appropriate for resistance?",1,"Use the resistance (ohms) function with the circuit de-energized.","Ohms (Ω)","DC voltage (V)","Frequency (Hz)"),
            s("A 12 V source is applied across a 6 Ω resistor. What current follows Ohm's law?",2,"I = V/R = 12/6 = 2 A. This is a mathematical exercise, not a live measurement.","0.5 A","2 A","72 A")
        }),
        new Exercise("Weight-and-balance desk", "FAA-H-8083-30B", "Weight and Balance", new Step[]{
            s("Before calculating aircraft loading, which reference must you obtain?",3,"Use the aircraft's current weight-and-balance records and applicable loading information.","A generic airplane's empty weight","A passenger's estimate","Aircraft-specific current loading data"),
            s("A fictional item weighs 40 lb at an arm of 50 in. What is its moment?",1,"Moment = weight × arm = 40 × 50 = 2,000 lb-in.","2,000 lb-in","90 lb-in","0.8 lb-in"),
            s("Where do acceptable CG limits for a real aircraft come from?",2,"Use the applicable aircraft flight manual, type data and approved loading information.","A universal range for all aircraft","Applicable aircraft loading documentation","The mod's fictional practice values")
        }),
        new Exercise("Magneto-timing trainer", "FAA-H-8083-32B", "Engine Ignition and Electrical Systems", new Step[]{
            s("Before handling a magneto ignition system, which hazard is especially important?",1,"Magnetos may generate ignition energy independently of the aircraft battery; treat the propeller as capable of starting the engine.","Unexpected ignition and propeller movement","Low tire pressure","Cabin static electricity only"),
            s("What instrument is commonly used to indicate magneto breaker-point opening during timing checks?",3,"A magneto timing light is used with the specified engine and magneto procedure.","Hydrometer","Pitot-static tester","Magneto timing light"),
            s("Which source determines the specified timing angle for the engine installed?",2,"Use the applicable engine/aircraft maintenance data; the game intentionally supplies no universal timing angle.","Any other aircraft's handbook","Applicable engine maintenance instructions","A fixed angle for all piston engines")
        })
    };
    private static final Map<String,Integer> BENCHES = new HashMap<>();
    private static String benchKey(ServerWorld w, BlockPos pos) { return w.getRegistryKey().getValue()+"|"+pos.getX()+"|"+pos.getY()+"|"+pos.getZ(); }
    private static Path benchFile(net.minecraft.server.MinecraftServer server) { return server.getSavePath(WorldSavePath.ROOT).resolve("apcraft-practical-benches.txt"); }
    private static void loadBenches(net.minecraft.server.MinecraftServer server) {
        BENCHES.clear();
        try { for(String line:Files.readAllLines(benchFile(server))) {String[] pair=line.split("=",2);if(pair.length==2)BENCHES.put(pair[0],Integer.parseInt(pair[1]));} }catch(Exception ignored){}
    }
    private static void saveBenches(net.minecraft.server.MinecraftServer server) {
        try { List<String> lines=new ArrayList<>(); for(var e:BENCHES.entrySet())lines.add(e.getKey()+"="+e.getValue());Files.write(benchFile(server),lines); }catch(Exception ex){System.err.println("A&P Craft bench save: "+ex);}
    }
    public static void initialize() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(FaaPracticals::loadBenches);
        UseBlockCallback.EVENT.register((player,world,hand,hit)->{
            if(world.isClient() || !(player instanceof ServerPlayerEntity p) || !(world instanceof ServerWorld sw))return ActionResult.PASS;
            Integer index=BENCHES.get(benchKey(sw,hit.getBlockPos()));
            if(index==null)return ActionResult.PASS;
            if(!world.getBlockState(hit.getBlockPos()).isOf(benchBlock(index))) {
                BENCHES.remove(benchKey(sw,hit.getBlockPos()));saveBenches(p.getServer());return ActionResult.PASS;
            }
            int[] current=state(p);
            if(current==null || current[0]!=index) {ACTIVE.put(p.getUuid(),new int[]{index,0,0});save(p);}
            show(p);
            return ActionResult.SUCCESS;
        });
    }
    /** Bind an existing training station to an interactive exercise. */
    public static void registerAirportBench(ServerWorld world, BlockPos pos, int exercise) {
        if (exercise >= 0 && exercise < EXERCISES.length && world.getBlockState(pos).isOf(benchBlock(exercise))) {
            BENCHES.put(benchKey(world, pos), exercise);
            saveBenches(world.getServer());
        }
    }
    private static net.minecraft.block.Block benchBlock(int i) {return i==0?Blocks.SMITHING_TABLE:i==1?Blocks.REDSTONE_LAMP:i==2?Blocks.LECTERN:Blocks.BLAST_FURNACE;}
    private static final Map<UUID, int[]> ACTIVE = new HashMap<>(); // exercise, stage, misses
    private static Path path(ServerPlayerEntity p) { return p.getServer().getSavePath(WorldSavePath.ROOT).resolve("apcraft-faa-practicals").resolve(p.getUuidAsString()+".txt"); }
    private static void say(ServerPlayerEntity p,String message) { p.sendMessage(Text.literal("[FAA PRACTICAL] "+message),false); }
    private static void save(ServerPlayerEntity p) { try { Files.createDirectories(path(p).getParent()); int[] v=ACTIVE.get(p.getUuid()); if(v==null) Files.deleteIfExists(path(p)); else Files.writeString(path(p),v[0]+","+v[1]+","+v[2]); } catch(Exception ex) {say(p,"Save failed: "+ex.getMessage());} }
    private static int[] state(ServerPlayerEntity p) {
        int[] v=ACTIVE.get(p.getUuid()); if(v!=null)return v;
        try {String[] parts=Files.readString(path(p)).trim().split(",");int a=Integer.parseInt(parts[0]),b=Integer.parseInt(parts[1]),c=Integer.parseInt(parts[2]);if(a<0||a>=EXERCISES.length||b<0||b>=EXERCISES[a].steps.length||c<0)return null;v=new int[]{a,b,c};ACTIVE.put(p.getUuid(),v);return v;}catch(Exception ignored){return null;}
    }
    private static void show(ServerPlayerEntity p) {
        int[] v=state(p);if(v==null){say(p,"Choose a station with /practical start <1-4>.");return;}
        Exercise e=EXERCISES[v[0]];Step s=e.steps[v[1]];
        say(p,e.title+" | Step "+(v[1]+1)+"/"+e.steps.length+" | "+e.source+" — "+e.topic);
        say(p,s.prompt);for(int i=0;i<s.options.length;i++){
            final int option=i+1;
            p.sendMessage(Text.literal("  ["+option+"] "+s.options[i])
                .styled(style->style.withColor(Formatting.AQUA)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/practical choose "+option))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,Text.literal("Select option "+option)))),false);
        }
        say(p,"Click an answer above, or use /practical choose <1-3>. Right-click a bench to resume.");
    }
    private static void choose(ServerPlayerEntity p,int answer) {
        int[] v=state(p);if(v==null){show(p);return;}Exercise e=EXERCISES[v[0]];Step s=e.steps[v[1]];
        if(answer!=s.correct){v[2]++;say(p,"Try again. "+s.explanation);save(p);show(p);return;}
        say(p,"Step passed. "+s.explanation);v[1]++;
        if(v[1]>=e.steps.length){say(p,"Simulation complete: "+e.title+" | missed attempts: "+v[2]+". This is NOT an FAA practical exam or aircraft maintenance sign-off.");ACTIVE.remove(p.getUuid());save(p);}else{save(p);show(p);}
    }
    private static void station(ServerPlayerEntity p) {
        // World interaction: place four labeled benches in front of the player. Avoid overwriting non-air blocks.
        BlockPos origin=p.getBlockPos().offset(p.getHorizontalFacing(),4);
        int placed=0;
        for(int i=0;i<EXERCISES.length;i++){
            BlockPos at=origin.add(i*2,0,0);
            if(p.getServerWorld().getBlockState(at).isAir() && p.getServerWorld().getBlockState(at.up()).isAir()){
                p.getServerWorld().setBlockState(at,benchBlock(i).getDefaultState());
                BENCHES.put(benchKey(p.getServerWorld(),at),i);placed++;
            }
        }
        saveBenches(p.getServer());
        say(p,"Placed "+placed+" of 4 interactive benches. Right-click a bench to start; click chat choices to respond. No existing blocks were overwritten.");
    }
    public static void registerCommands(CommandDispatcher<ServerCommandSource> d) {
        d.register(CommandManager.literal("practical")
            .then(CommandManager.literal("list").executes(c->{ServerPlayerEntity p=c.getSource().getPlayerOrThrow();for(int i=0;i<EXERCISES.length;i++)say(p,(i+1)+") "+EXERCISES[i].title);return 1;}))
            .then(CommandManager.literal("start").then(CommandManager.argument("number",IntegerArgumentType.integer(1,EXERCISES.length)).executes(c->{ServerPlayerEntity p=c.getSource().getPlayerOrThrow();ACTIVE.put(p.getUuid(),new int[]{IntegerArgumentType.getInteger(c,"number")-1,0,0});save(p);show(p);return 1;})))
            .then(CommandManager.literal("choose").then(CommandManager.argument("answer",IntegerArgumentType.integer(1,3)).executes(c->{choose(c.getSource().getPlayerOrThrow(),IntegerArgumentType.getInteger(c,"answer"));return 1;})))
            .then(CommandManager.literal("status").executes(c->{show(c.getSource().getPlayerOrThrow());return 1;}))
            .then(CommandManager.literal("source").executes(c->{ServerPlayerEntity p=c.getSource().getPlayerOrThrow();int[] v=state(p);if(v!=null){Exercise e=EXERCISES[v[0]];say(p,e.source+" — "+e.topic);}say(p,"Official FAA handbook index: https://www.faa.gov/regulations_policies/handbooks_manuals/aviation");return 1;}))
            .then(CommandManager.literal("benches").executes(c->{station(c.getSource().getPlayerOrThrow());return 1;}))
            .then(CommandManager.literal("cancel").executes(c->{ServerPlayerEntity p=c.getSource().getPlayerOrThrow();ACTIVE.remove(p.getUuid());save(p);say(p,"Practice canceled.");return 1;})));
    }
}

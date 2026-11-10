package com.apcraft;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.*;

/** Educational abstractions only: no type-specific approved maintenance procedures. */
public final class TrainingExpansion {
    private TrainingExpansion() {}
    private static final String[] BRANDS = {"pittsburgh", "icon", "mac", "snapon"};
    private static final int[] DURABILITY = {300,650,850,950};
    private static final int[] PRICE = {90,260,550,800};
    private static final int[] BONUS_PERCENT = {0,15,25,30};
    private static final String[] TYPES = {"wrench", "multimeter", "torque_wrench", "inspection_kit"};
    private static final Map<String, Item> ITEMS = new HashMap<>();
    private static final String[] TOPICS = {"Engine Run-Up", "100-Hour Inspection", "Generator & Alternator", "Turbine Engine", "Helicopter Rotor System", "Hydraulic & Fuel Systems"};
    private static final int[] PAY = {250,350,280,420,450,300};
    // question, answer 1, answer 2, answer 3, correct (1-based), explanation, required tool category
    private static final String[][][] STAGES = {
        {
            {"Before a piston-engine run-up, what establishes the correct RPM and limits?", "Applicable aircraft checklist and approved data", "The RPM used by any other airplane", "The maximum redline", "1", "Use the specific aircraft/engine checklist and limitations.", "inspection_kit"},
            {"A magneto check produces an RPM drop outside published limits. What next?", "Increase throttle until it clears", "Stop and troubleshoot per approved procedure", "Record it as normal", "2", "Abnormal indications require investigation, not a guessed fix.", "inspection_kit"},
            {"After an abnormal engine indication, what permits release?", "A successful prescribed check and authorized maintenance disposition", "A mechanic's guess", "Waiting five minutes", "1", "Follow the applicable maintenance data and release requirements.", "inspection_kit"}
        },
        {
            {"Which regulation lists the scope of a 100-hour or annual inspection?", "14 CFR Part 43 Appendix D", "14 CFR Part 91 Appendix A", "14 CFR Part 61 Appendix C", "1", "Appendix D to Part 43 specifies inspection scope and detail.", "inspection_kit"},
            {"A defect affecting airworthiness is discovered. What is appropriate?", "Ignore it if the engine runs", "Document and address it before approving return to service", "Reset the tachometer", "2", "Inspection findings must be handled under applicable regulations and data.", "inspection_kit"},
            {"Who may approve return to service after a 100-hour inspection, subject to privileges and limitations?", "Any aircraft owner", "An appropriately certificated mechanic with Airframe and Powerplant ratings", "Any student pilot", "2", "A 100-hour inspection does not generally require inspection authorization; an annual normally does.", "inspection_kit"}
        },
        {
            {"A DC charging system indicates low bus voltage. What is a sound first approach?", "Use the wiring diagram and approved troubleshooting procedure", "Replace the battery immediately", "Short the field terminal", "1", "Trace power, protection, and charging components systematically.", "multimeter"},
            {"Before an ohmmeter resistance check, what is required?", "Leave the system energized", "Isolate and de-energize the circuit", "Increase generator RPM", "2", "Resistance measurements on energized circuits can damage equipment and cause injury.", "multimeter"},
            {"What controls generator or alternator output voltage in a typical regulated system?", "Voltage regulator", "Oil pressure switch", "Pitot heater", "1", "The regulator adjusts field excitation or equivalent control to regulate voltage.", "multimeter"}
        },
        {
            {"A turbine engine start shows rapidly rising EGT beyond the prescribed limit. What should the operator do?", "Follow the published abort/hot-start procedure", "Add more fuel", "Continue until idle", "1", "Hot-start limits and abort procedures are engine-specific.", "inspection_kit"},
            {"What does a hung start generally describe?", "An engine that fails to accelerate normally to stable idle", "An engine that reaches full rated power", "A normal ignition shutdown", "1", "A hung start is inadequate acceleration during starting.", "inspection_kit"},
            {"Where are turbine inspection limits and borescope acceptance criteria found?", "In the applicable engine maintenance data", "On any generic forum", "In a car owner's manual", "1", "Always use the specific manufacturer's limits and instructions.", "inspection_kit"}
        },
        {
            {"What does a helicopter swashplate transmit?", "Pilot cyclic and collective inputs to rotor blade pitch", "Fuel pressure to the engine", "Electrical power to the starter", "1", "The swashplate transfers control inputs to rotating rotor pitch links.", "inspection_kit"},
            {"What is the purpose of a freewheeling unit?", "Allow the rotor to keep turning if engine drive is lost", "Lock the rotor during flight", "Increase turbine inlet temperature", "1", "The overrunning clutch permits rotor rotation independent of a failed engine drive.", "inspection_kit"},
            {"After discovering rotor blade damage, what determines disposition?", "Applicable rotorcraft maintenance limits and inspection instructions", "Visual appearance alone", "The mechanic's preferred brand of wrench", "1", "Rotor damage evaluation is type-specific and must use approved/acceptable maintenance data.", "inspection_kit"}
        },
        {
            {"A hydraulic system loses pressure. What should be checked first?", "The applicable troubleshooting procedure and system indications", "Randomly tighten all fittings", "Increase relief-valve pressure", "1", "Systematic diagnostics and safe depressurization come before repair.", "inspection_kit"},
            {"Before opening a hydraulic line, what is essential?", "Relieve pressure using the applicable procedure", "Heat the fitting", "Run the pump continuously", "1", "Stored hydraulic pressure can cause severe injury.", "wrench"},
            {"After an approved hydraulic repair, what follows?", "Required servicing, leak checks and functional tests", "Immediate release without testing", "Overfill the reservoir", "1", "Verify repair integrity and operation per maintenance data.", "wrench"}
        }
    };
    private static final Map<UUID, int[]> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();
    private static long day(ServerPlayerEntity p) { return p.getServerWorld().getTimeOfDay() / 24000L; }
    private static void say(ServerPlayerEntity p, String message) { p.sendMessage(Text.literal("[A&P TRAINING] " + message), false); }
    public static void registerItems() {
        for (int b=0;b<BRANDS.length;b++) for (String type:TYPES) {
            String id=BRANDS[b]+"_"+type;
            Item item=Registry.register(Registries.ITEM,new Identifier("apcraft",id),new Item(new FabricItemSettings().maxCount(1).maxDamage(DURABILITY[b])));
            ITEMS.put(id,item);
            ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(item));
        }
    }
    private static int[] tool(ServerPlayerEntity p,String type) {
        int bestSlot=-1, bestBrand=-1;
        for (int i=0;i<p.getInventory().size();i++) {
            ItemStack stack=p.getInventory().getStack(i);
            for(int b=0;b<BRANDS.length;b++) {
                if (stack.isOf(ITEMS.get(BRANDS[b]+"_"+type)) && b>bestBrand) {bestSlot=i;bestBrand=b;}
            }
        }
        return new int[]{bestSlot,bestBrand};
    }
    private static void show(ServerPlayerEntity p) {
        int[] state=ACTIVE.get(p.getUuid());
        if(state==null){say(p,"No training job active. /training list");return;}
        String[] s=STAGES[state[0]][state[1]];
        say(p,TOPICS[state[0]]+" | Stage "+(state[1]+1)+"/3 | Requires branded "+s[6]);
        say(p,s[0]);
        for(int i=1;i<=3;i++)say(p,i+") "+s[i]);
        say(p,"Use /training choose 1-3");
    }
    private static void choose(ServerPlayerEntity p,int answer) {
        int[] state=ACTIVE.get(p.getUuid());if(state==null){say(p,"Start with /training start 1-6");return;}
        String[] stage=STAGES[state[0]][state[1]];
        int[] selected=tool(p,stage[6]);
        if(selected[0]<0){say(p,"You need a branded "+stage[6]+". /training tools and /training buy <brand 1-4> <type 1-4>");return;}
        if(answer!=Integer.parseInt(stage[4])){say(p,"Not yet. Review: "+stage[5]);return;}
        ItemStack stack=p.getInventory().getStack(selected[0]);
        stack.setDamage(stack.getDamage()+1);
        if(stack.getDamage()>=stack.getMaxDamage())p.getInventory().setStack(selected[0],ItemStack.EMPTY);
        state[2]=Math.max(state[2],selected[1]);
        say(p,"Completed. "+stage[5]);state[1]++;
        if(state[1]<3){show(p);return;}
        int base=PAY[state[0]];
        int bonus=base*BONUS_PERCENT[state[2]]/100;
        APCraft.rewardTraining(p,base,bonus);
        COOLDOWN.put(p.getUuid(),day(p)+1);
        ACTIVE.remove(p.getUuid());
        say(p,"JOB COMPLETE: $"+base+" wages + $"+bonus+" efficiency bonus, +150 A&P XP, +15 Minecraft XP. Next paid training job in one Minecraft day.");
    }
    public static void registerCommands(CommandDispatcher<ServerCommandSource> d) {
        d.register(CommandManager.literal("training")
            .then(CommandManager.literal("list").executes(c->{ServerPlayerEntity p=c.getSource().getPlayerOrThrow();for(int i=0;i<TOPICS.length;i++)say(p,(i+1)+") "+TOPICS[i]+" - $"+PAY[i]);return 1;}))
            .then(CommandManager.literal("tools").executes(c->{ServerPlayerEntity p=c.getSource().getPlayerOrThrow();for(int b=0;b<BRANDS.length;b++)say(p,(b+1)+") "+BRANDS[b]+" $"+PRICE[b]+" | durability "+DURABILITY[b]+" | bonus "+BONUS_PERCENT[b]+"%");say(p,"Types: 1 wrench, 2 multimeter, 3 torque wrench, 4 inspection kit. /training buy <brand> <type>");return 1;}))
            .then(CommandManager.literal("buy").then(CommandManager.argument("brand",IntegerArgumentType.integer(1,4)).then(CommandManager.argument("type",IntegerArgumentType.integer(1,4)).executes(c->{ServerPlayerEntity p=c.getSource().getPlayerOrThrow();int b=IntegerArgumentType.getInteger(c,"brand")-1;int t=IntegerArgumentType.getInteger(c,"type")-1;ItemStack stack=new ItemStack(ITEMS.get(BRANDS[b]+"_"+TYPES[t]));if(!p.getInventory().insertStack(stack)){say(p,"Inventory full.");return 0;} // Charge through shared economy; rollback on insufficient funds.
                if(!APCraft.purchaseTrainingTool(p,PRICE[b])){p.getInventory().removeOne(stack);say(p,"Insufficient cash; tool not purchased.");return 0;}say(p,"Purchased "+BRANDS[b]+" "+TYPES[t]+" for $"+PRICE[b]);return 1;})))))
            .then(CommandManager.literal("start").then(CommandManager.argument("job",IntegerArgumentType.integer(1,6)).executes(c->{ServerPlayerEntity p=c.getSource().getPlayerOrThrow();if(day(p)<COOLDOWN.getOrDefault(p.getUuid(),0L)){say(p,"Paid training job available next Minecraft day.");return 0;}ACTIVE.put(p.getUuid(),new int[]{IntegerArgumentType.getInteger(c,"job")-1,0,0});show(p);return 1;})))
            .then(CommandManager.literal("choose").then(CommandManager.argument("answer",IntegerArgumentType.integer(1,3)).executes(c->{choose(c.getSource().getPlayerOrThrow(),IntegerArgumentType.getInteger(c,"answer"));return 1;})))
            .then(CommandManager.literal("status").executes(c->{show(c.getSource().getPlayerOrThrow());return 1;}))
            .then(CommandManager.literal("cancel").executes(c->{ServerPlayerEntity p=c.getSource().getPlayerOrThrow();ACTIVE.remove(p.getUuid());say(p,"Training canceled.");return 1;})));
    }
}

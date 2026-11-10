package com.apcraft;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.*;
import java.nio.file.*;
import java.io.*;
import com.google.gson.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class APCraft implements ModInitializer {
    private static final String[] TOOLS = {"safety_wire_pliers", "torque_wrench", "multimeter", "compression_tester", "timing_light"};
    private static final String[][] QUESTIONS = {
        {"What is the purpose of interconnect tubes between can-type combustion chambers?", "Transfer lubricating oil", "Propagate flame during starting", "Control turbine clearance", "Increase compressor pressure", "2", "Interconnect tubes carry flame to chambers without igniters during starting."},
        {"During a propeller overspeed, what blade-angle change normally helps reduce RPM?", "Decrease blade angle", "Increase blade angle", "Feather immediately", "Reverse blade angle", "2", "Increasing blade angle increases propeller load and reduces RPM within the governing range."},
        {"What is the primary purpose of safety wire on a fastener?", "Seal fluid leaks", "Prevent loosening due to vibration", "Increase fastener strength", "Prevent corrosion", "2", "Correctly installed safety wire helps prevent fastener rotation toward loosening."},
        {"In a four-stroke engine, how many crankshaft revolutions complete one cycle?", "One", "Two", "Three", "Four", "2", "Intake, compression, power and exhaust require two crankshaft revolutions."},
        {"Which instrument is used to measure electrical resistance?", "Tachometer", "Ohmmeter", "Manometer", "Pyrometer", "2", "An ohmmeter measures resistance; isolate and de-energize the circuit before testing."}
    };
    private static final Map<UUID, Integer> ACTIVE = new HashMap<>();
    private static final Map<UUID, Set<Integer>> MISSED = new HashMap<>();
    private static final Map<UUID, Integer> XP = new HashMap<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, Account> ACCOUNTS = new HashMap<>();
    private static MinecraftServer server;
    private static int tickCounter = 0;
    private static class Account {
        int cash = 500;
        int debt = 0;
        long nextBillDay = -1;
        int jobs = 0;
        int simulationsCompleted = 0;
        long nextSimulationDay = 0;
    }
    private static Account account(ServerPlayerEntity p) {
        Account a = ACCOUNTS.computeIfAbsent(p.getUuid(), id -> new Account());
        if (a.nextBillDay < 0) a.nextBillDay = currentDay() + 7;
        return a;
    }
    private static long currentDay() {
        return server == null ? 0 : server.getOverworld().getTimeOfDay() / 24000L;
    }
    private static Path saveFile() {
        return server.getSavePath(WorldSavePath.ROOT).resolve("apcraft-economy.json");
    }
    private static void save() {
        if (server == null) return;
        try {
            JsonObject root = new JsonObject();
            for (Map.Entry<UUID, Account> e : ACCOUNTS.entrySet()) root.add(e.getKey().toString(), GSON.toJsonTree(e.getValue()));
            Files.writeString(saveFile(), GSON.toJson(root));
        } catch (IOException e) { System.err.println("A&P Craft save failed: " + e); }
    }
    private static void load() {
        ACCOUNTS.clear();
        try {
            if (!Files.exists(saveFile())) return;
            JsonObject root = JsonParser.parseString(Files.readString(saveFile())).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) ACCOUNTS.put(UUID.fromString(e.getKey()), GSON.fromJson(e.getValue(), Account.class));
        } catch (Exception e) { System.err.println("A&P Craft load failed: " + e); }
    }
    private static void bill(Account a) {
        long day = currentDay();
        if (a.nextBillDay < 0) a.nextBillDay = day + 7;
        while (day >= a.nextBillDay) {
            int due = 300; // rent 200, power 50, water 25, internet 25
            int paid = Math.min(a.cash, due);
            a.cash -= paid;
            a.debt += due - paid;
            a.nextBillDay += 7;
        }
    }
    private static void message(ServerPlayerEntity p, String s) { p.sendMessage(Text.literal("[A&P ECONOMY] " + s), false); }
    private static void buy(ServerPlayerEntity p, String product, int cost, ItemStack stack) {
        Account a = account(p);
        bill(a);
        if (a.cash < cost) { message(p, "Not enough money. Need $" + cost + ", have $" + a.cash); return; }
        if (!p.getInventory().insertStack(stack)) { message(p, "Inventory full. Purchase canceled."); return; }
        a.cash -= cost;
        message(p, "Purchased " + product + " for $" + cost + ". Balance $" + a.cash);
        save();
    }
    /** Shared fictional currency API for in-world vendors and games. */
    public static boolean spendCurrency(ServerPlayerEntity p, int amount) {
        if (amount <= 0) return false;
        Account a = account(p); bill(a);
        if (a.cash < amount) return false;
        a.cash -= amount; save(); return true;
    }
    public static void awardCurrency(ServerPlayerEntity p, int amount) {
        if (amount <= 0) return;
        Account a = account(p); a.cash += amount; save();
    }
    public static int currencyBalance(ServerPlayerEntity p) { Account a=account(p); bill(a); return a.cash; }
    // Interactive work orders: each stage requires the player to choose a diagnostic or repair action.
    // These are educational abstractions, not approved aircraft maintenance procedures.
    private static final String[][][] SIMS = {
        {
            {"PISTON ENGINE: Rough running during magneto check. Which instrument helps check ignition timing?", "Compression tester", "Magneto timing light", "Torque wrench", "2", "timing_light", "Use a timing light for the ignition timing check."},
            {"The ignition timing is out of specification. What is the appropriate next step?", "Adjust per the engine maintenance instructions", "Increase fuel pressure at random", "Ignore the discrepancy", "1", "timing_light", "Follow the applicable engine manufacturer's maintenance data."},
            {"After the approved adjustment, what confirms the repair?", "Return aircraft without checks", "Perform the prescribed timing verification and operational check", "Disconnect an ignition lead", "2", "Verify the work using the applicable maintenance instructions."}
        },
        {
            {"ELECTRICAL: A landing light is inoperative. What should you check first?", "Replace the alternator", "Inspect circuit protection and power supply per procedure", "Increase engine RPM", "2", "multimeter", "Begin with the approved troubleshooting procedure and power source checks."},
            {"Before measuring resistance on a circuit, what is required?", "De-energize and isolate the circuit", "Keep the circuit powered", "Short the battery terminals", "1", "multimeter", "Resistance testing requires a de-energized, isolated circuit."},
            {"A wire is found damaged. What should you do?", "Twist bare ends together", "Repair using approved wiring practices and verify operation", "Install an oversized breaker", "2", "multimeter", "Use approved wiring repair data and perform the required checks."}
        },
        {
            {"ENGINE INSPECTION: A cylinder shows low compression. What tool measures it?", "Timing light", "Differential compression tester", "Inspection mirror", "2", "compression_tester", "A differential compression tester helps evaluate cylinder sealing."},
            {"Air is heard at the exhaust during the test. What does that suggest?", "Exhaust valve leakage", "A healthy oil pump", "An electrical short", "1", "compression_tester", "Air escaping through the exhaust can indicate exhaust valve leakage."},
            {"What is the correct disposition?", "Release the aircraft regardless", "Evaluate against approved limits and maintenance instructions", "Increase ignition advance", "2", "compression_tester", "Follow manufacturer and regulatory inspection requirements."}
        },
        {
            {"FASTENER INSPECTION: A specified bolt requires torque verification. Which tool is appropriate?", "Timing light", "Torque wrench", "Compression tester", "2", "torque_wrench", "Use the specified calibrated torque tool and approved procedure."},
            {"Where do you obtain the required torque value?", "Applicable maintenance data", "A random internet comment", "The highest tool setting", "1", "torque_wrench", "Torque values must come from applicable approved or acceptable maintenance data."},
            {"A fastener requires safety wire after torqueing. What is the correct action?", "Use any wire and direction", "Install safety wire per the specified procedure", "Leave the fastener unsecured", "2", "safety_wire_pliers", "Safety wire must be installed in the prescribed direction and manner."}
        }
    };
    private static final String[] JOB_NAMES = {"Magneto Timing", "Electrical Fault", "Compression Check", "Fastener Inspection"};
    private static final int[] JOB_PAY = {180, 220, 200, 160};
    private static final Map<UUID, int[]> ACTIVE_SIM = new HashMap<>();
    private static boolean hasTool(ServerPlayerEntity p, String tool) {
        for (int i = 0; i < p.getInventory().size(); i++) {
            ItemStack stack = p.getInventory().getStack(i);
            if (Registries.ITEM.getId(stack.getItem()).equals(new Identifier("apcraft", tool))) return true;
        }
        return false;
    }
    private static void showSimStage(ServerPlayerEntity p) {
        int[] state = ACTIVE_SIM.get(p.getUuid());
        if (state == null) { message(p, "No active work order. Use /work start 1-4."); return; }
        String[] stage = SIMS[state[0]][state[1]];
        message(p, "WORK ORDER: " + JOB_NAMES[state[0]] + " | Step " + (state[1]+1) + "/3");
        message(p, stage[0]);
        message(p, "1) " + stage[1] + " | 2) " + stage[2] + " | 3) " + stage[3]);
        message(p, "Required tool in inventory: " + stage[5] + " | /work choose 1-3");
    }
    private static void chooseSim(ServerPlayerEntity p, int choice) {
        int[] state = ACTIVE_SIM.get(p.getUuid());
        if (state == null) { message(p, "Start a job with /work start 1-4."); return; }
        String[] stage = SIMS[state[0]][state[1]];
        if (!hasTool(p, stage[5])) { message(p, "Missing tool: " + stage[5] + ". Obtain one first (Creative inventory or /give @s apcraft:" + stage[5] + ")."); return; }
        if (choice != Integer.parseInt(stage[4])) {
            message(p, "Incorrect. " + stage[6] + " Retry this step; no payment awarded."); return;
        }
        message(p, "Step complete. " + stage[6]);
        state[1]++;
        if (state[1] < 3) { showSimStage(p); return; }
        Account a = account(p); bill(a);
        int pay = JOB_PAY[state[0]];
        a.cash += pay; a.jobs++; a.simulationsCompleted++;
        a.nextSimulationDay = currentDay() + 1;
        XP.merge(p.getUuid(), 100, Integer::sum);
        p.addExperience(10);
        ACTIVE_SIM.remove(p.getUuid());
        CareerJournal.recordCompletedJob(p);
        message(p, "WORK ORDER COMPLETED! +$" + pay + ", +100 A&P XP, +10 Minecraft XP. Cash: $" + a.cash + ". Next paid job available Minecraft day " + a.nextSimulationDay);
        save();
    }
    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(s -> { server = s; load(); });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> save());
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> { server = null; ACCOUNTS.clear(); });
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (++tickCounter < 1200) return;
            tickCounter = 0;
            for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
                Account a = account(p);
                long prior = a.nextBillDay;
                bill(a);
                if (prior != a.nextBillDay) message(p, "Weekly bills processed! Balance $" + a.cash + ", debt $" + a.debt);
            }
            save();
        });
        for (String name : TOOLS) {
            Item item = Registry.register(Registries.ITEM, new Identifier("apcraft", name), new Item(new FabricItemSettings().maxCount(1)));
            ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(item));
        }
        TrainingExpansion.registerItems();
        CarryBags.initialize();
        FaaPracticals.initialize();
        JobBoard.initialize();
        VillageCasino.initialize();
        VillagerBank.initialize();
        HomeCooking.initialize();
        NpcMarketplace.initialize();
        ToolTruck.initialize();
        Airfield.initialize();
        AirportGuide.initialize();
        VehicleFleet.initialize();
        GroundVehicleSystems.initialize();
        ApacheSystems.initialize();
        ApacheCrewSystems.initialize();
        ApacheFlightSystems.initialize();
        ApacheAircraft.initialize();
        ApacheVisuals.initialize();
        AircraftDisplays.initialize();
        CessnaEngineBench.initialize();
        CessnaCockpit.initialize();
        CessnaWalkaround.initialize();
        Cessna172Simulator.initialize();
        RuralIndustries.initialize();
        IndustryContracts.initialize();
        AviationBusiness.initialize();
        CharterOperations.initialize();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            TrainingExpansion.registerCommands(dispatcher);
            FaaAcademy.registerCommands(dispatcher);
            CareerJournal.registerCommands(dispatcher);
            FaaPracticals.registerCommands(dispatcher);
            CivilianTroubleshooting.registerCommands(dispatcher);
            dispatcher.register(CommandManager.literal("economy")
                .then(CommandManager.literal("balance").executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow(); Account a = account(p); bill(a);
                    message(p, "Cash $" + a.cash + " | Debt $" + a.debt + " | Next bills day " + a.nextBillDay); save(); return 1;
                }))
                .then(CommandManager.literal("bills").executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow(); Account a = account(p); bill(a);
                    message(p, "Every 7 Minecraft days: Rent $200, electricity $50, water $25, internet $25. Total $300. Next due day " + a.nextBillDay + ". Debt $" + a.debt); save(); return 1;
                }))
                .then(CommandManager.literal("paydebt").executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow(); Account a = account(p); bill(a);
                    int paid = Math.min(a.cash, a.debt); a.cash -= paid; a.debt -= paid;
                    message(p, "Paid $" + paid + " toward debt. Remaining $" + a.debt); save(); return 1;
                }))
                .then(CommandManager.literal("shop").executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    message(p, "Groceries: bread $12 (/economy buy bread), apples $15 (/economy buy apples), cooked beef $45 (/economy buy beef). Supplies: iron $60 (/economy buy iron)."); return 1;
                }))
                .then(CommandManager.literal("buy")
                    .then(CommandManager.literal("bread").executes(ctx -> { buy(ctx.getSource().getPlayerOrThrow(), "8 bread", 12, new ItemStack(Items.BREAD, 8)); return 1; }))
                    .then(CommandManager.literal("apples").executes(ctx -> { buy(ctx.getSource().getPlayerOrThrow(), "8 apples", 15, new ItemStack(Items.APPLE, 8)); return 1; }))
                    .then(CommandManager.literal("beef").executes(ctx -> { buy(ctx.getSource().getPlayerOrThrow(), "8 cooked beef", 45, new ItemStack(Items.COOKED_BEEF, 8)); return 1; }))
                    .then(CommandManager.literal("iron").executes(ctx -> { buy(ctx.getSource().getPlayerOrThrow(), "4 iron ingots", 60, new ItemStack(Items.IRON_INGOT, 4)); return 1; })))
                .then(CommandManager.literal("job").executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    message(p, "Maintenance work order: complete /apcraft quiz and answer correctly to earn $75. Study XP also awarded."); return 1;
                })));
            dispatcher.register(CommandManager.literal("work")
                .then(CommandManager.literal("list").executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    for (int i = 0; i < JOB_NAMES.length; i++) message(p, (i+1) + ") " + JOB_NAMES[i] + " - $" + JOB_PAY[i]);
                    message(p, "Start: /work start 1-4 | Answer: /work choose 1-3 | /work status | /work cancel"); return 1;
                }))
                .then(CommandManager.literal("start")
                    .then(CommandManager.argument("job", IntegerArgumentType.integer(1,4)).executes(ctx -> {
                        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow(); Account a = account(p); bill(a);
                        if (currentDay() < a.nextSimulationDay) { message(p, "Paid job cooldown until Minecraft day " + a.nextSimulationDay); return 0; }
                        int job = IntegerArgumentType.getInteger(ctx, "job") - 1;
                        ACTIVE_SIM.put(p.getUuid(), new int[]{job,0}); showSimStage(p); return 1;
                    })))
                .then(CommandManager.literal("choose")
                    .then(CommandManager.argument("option", IntegerArgumentType.integer(1,3)).executes(ctx -> {
                        chooseSim(ctx.getSource().getPlayerOrThrow(), IntegerArgumentType.getInteger(ctx,"option")); return 1;
                    })))
                .then(CommandManager.literal("status").executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow(); Account a = account(p);
                    message(p, "Completed simulations: " + a.simulationsCompleted + " | Next paid work day: " + a.nextSimulationDay);
                    showSimStage(p); return 1;
                }))
                .then(CommandManager.literal("cancel").executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow(); ACTIVE_SIM.remove(p.getUuid());
                    message(p, "Work order canceled without payment."); return 1;
                })));
            dispatcher.register(CommandManager.literal("apcraft")
                .then(CommandManager.literal("quiz").executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    int index = new Random().nextInt(QUESTIONS.length);
                    ask(p, index); return 1;
                }))
                .then(CommandManager.literal("answer")
                    .then(CommandManager.argument("choice", IntegerArgumentType.integer(1,4)).executes(ctx -> {
                        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                        int answer = IntegerArgumentType.getInteger(ctx, "choice");
                        Integer index = ACTIVE.remove(p.getUuid());
                        if (index == null) { p.sendMessage(Text.literal("Start a quiz with /apcraft quiz"), false); return 0; }
                        String[] q = QUESTIONS[index];
                        if (answer == Integer.parseInt(q[5])) {
                            XP.merge(p.getUuid(), 50, Integer::sum);
                            Account a = account(p); bill(a); a.cash += 75; a.jobs++;
                            save();
                            p.addExperience(5);
                            p.sendMessage(Text.literal("Correct! +$75, +50 A&P XP and +5 Minecraft XP. " + q[6]), false);
                        } else {
                            MISSED.computeIfAbsent(p.getUuid(), k -> new HashSet<>()).add(index);
                            p.sendMessage(Text.literal("Incorrect. Correct answer: " + q[5] + ". " + q[6] + " Added to missed questions."), false);
                        }
                        return 1;
                    })))
                .then(CommandManager.literal("review").executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    Set<Integer> missed = MISSED.getOrDefault(p.getUuid(), Collections.emptySet());
                    if (missed.isEmpty()) { p.sendMessage(Text.literal("No missed questions in this session."), false); return 1; }
                    int index = missed.iterator().next(); missed.remove(index); ask(p, index); return 1;
                }))
                .then(CommandManager.literal("stats").executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    p.sendMessage(Text.literal("A&P XP: " + XP.getOrDefault(p.getUuid(),0) + " | Missed questions: " + MISSED.getOrDefault(p.getUuid(),Collections.emptySet()).size()), false);
                    return 1;
                })));
        });
    }
    static boolean purchaseTrainingTool(ServerPlayerEntity p, int price) {
        Account a=account(p); bill(a); if(a.cash<price)return false; a.cash-=price; save(); return true;
    }
    static void refundFleet(ServerPlayerEntity p, int amount) { account(p).cash += amount; save(); }
    static void rewardTraining(ServerPlayerEntity p, int base, int bonus) {
        Account a = account(p); bill(a); a.cash += base + bonus; a.jobs++; a.simulationsCompleted++;
        XP.merge(p.getUuid(), 150, Integer::sum); p.addExperience(15); save();
        CareerJournal.recordCompletedJob(p);
    }
    private static void ask(ServerPlayerEntity p, int index) {
        ACTIVE.put(p.getUuid(), index);
        String[] q = QUESTIONS[index];
        p.sendMessage(Text.literal("[A&P CRAFT] " + q[0]), false);
        for (int i=1;i<=4;i++) p.sendMessage(Text.literal(i + ") " + q[i]), false);
        p.sendMessage(Text.literal("Answer with /apcraft answer 1-4"), false);
    }
}

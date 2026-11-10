package com.apcraft;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import java.nio.file.*;
import java.util.*;

/** Fictional RPG career progression, independent of FAA certification or privileges. */
public final class CareerJournal {
    private CareerJournal() {}
    private static final String[] TITLES = {"Shop Helper", "Apprentice Mechanic", "Line Technician", "Aircraft Technician", "Lead Mechanic", "Hangar Manager", "Aviation Entrepreneur"};
    private static final int[] THRESHOLDS = {0, 2, 6, 14, 28, 50, 80};
    private static final String[] GOALS = {
        "Complete a supervised training job", "Complete 2 training jobs", "Complete 6 training jobs",
        "Complete 14 training jobs", "Complete 28 training jobs", "Complete 50 training jobs",
        "Complete 80 training jobs"
    };
    private static final Map<UUID, Integer> JOBS = new HashMap<>();
    private static Path file(ServerPlayerEntity p) {
        return p.getServer().getSavePath(WorldSavePath.ROOT).resolve("apcraft-careers").resolve(p.getUuidAsString()+".txt");
    }
    private static int jobs(ServerPlayerEntity p) {
        Integer cached = JOBS.get(p.getUuid());
        if (cached != null) return cached;
        int count = 0;
        try { if (Files.exists(file(p))) count = Math.max(0, Integer.parseInt(Files.readString(file(p)).trim())); }
        catch (Exception e) { System.err.println("A&P Craft career read: " + e); }
        JOBS.put(p.getUuid(), count);
        return count;
    }
    private static int rank(int jobs) {
        int rank = 0;
        for (int i = 1; i < THRESHOLDS.length; i++) if (jobs >= THRESHOLDS[i]) rank = i;
        return rank;
    }
    private static void say(ServerPlayerEntity p, String message) { p.sendMessage(Text.literal("[CAREER JOURNAL] " + message), false); }
    public static void recordCompletedJob(ServerPlayerEntity p) {
        int before = jobs(p);
        int after = before + 1;
        JOBS.put(p.getUuid(), after);
        try { Files.createDirectories(file(p).getParent()); Files.writeString(file(p), Integer.toString(after)); }
        catch (Exception e) { say(p, "Career progress could not be saved: " + e.getMessage()); }
        if (rank(after) > rank(before)) say(p, "Promotion! New RPG rank: " + TITLES[rank(after)] + ". This is NOT an FAA certificate or authorization.");
        else say(p, "Job recorded (" + after + "). Next rank: " + (rank(after) == TITLES.length - 1 ? "maximum reached" : THRESHOLDS[rank(after)+1] + " completed jobs"));
    }
    public static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("career")
            .then(CommandManager.literal("journal").executes(ctx -> { show(ctx.getSource().getPlayerOrThrow()); return 1; }))
            .then(CommandManager.literal("ranks").executes(ctx -> {
                ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                for (int i = 0; i < TITLES.length; i++) say(p, TITLES[i] + " — " + THRESHOLDS[i] + " completed paid training jobs");
                return 1;
            }))
            .executes(ctx -> { show(ctx.getSource().getPlayerOrThrow()); return 1; }));
    }
    private static void show(ServerPlayerEntity p) {
        int count = jobs(p), level = rank(count);
        say(p, "RPG career: " + TITLES[level] + " | Completed jobs: " + count);
        say(p, "Current milestone: " + GOALS[level]);
        if (level < TITLES.length - 1) say(p, "Next promotion: " + TITLES[level+1] + " at " + THRESHOLDS[level+1] + " jobs (" + (THRESHOLDS[level+1]-count) + " remaining)");
        say(p, "Explore /faa lessons, /practical list, /work list, /aviationbusiness, and /fleet. RPG ranks do not confer FAA privileges.");
    }
}

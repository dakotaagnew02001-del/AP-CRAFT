package com.apcraft;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.village.VillagerProfession;
import net.minecraft.village.VillagerData;
import net.minecraft.village.VillagerType;
import net.minecraft.util.Hand;
import net.minecraft.block.Blocks;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.WorldSavePath;
import java.nio.file.*;
import java.util.*;

/** In-world entry point to existing paid maintenance work orders.
 * Job board blocks are lecterns and must be placed by an authorized operator.
 * This does not confer FAA maintenance privileges.
 */
public final class JobBoard {
    private JobBoard() {}
    private static final String DISPATCHER_TAG = "apcraft_hangar_dispatcher";
    private static final String INSTRUCTOR_TAG = "apcraft_faa_instructor";
    private static final Set<String> BOARDS = new HashSet<>();
    private static final String[] JOBS = {"Magneto timing — $180", "Electrical troubleshooting — $220", "Compression inspection — $200", "Fastener inspection — $160"};
    private static String key(ServerWorld w, BlockPos p) { return w.getRegistryKey().getValue()+"|"+p.getX()+"|"+p.getY()+"|"+p.getZ(); }
    private static Path file(net.minecraft.server.MinecraftServer s) { return s.getSavePath(WorldSavePath.ROOT).resolve("apcraft-job-boards.txt"); }
    private static void load(net.minecraft.server.MinecraftServer s) {
        BOARDS.clear();
        try { BOARDS.addAll(Files.readAllLines(file(s))); } catch (java.nio.file.NoSuchFileException ignored) { } catch (Exception e) { System.err.println("A&P Craft board load: "+e); }
    }
    private static void save(net.minecraft.server.MinecraftServer s) {
        try { Files.write(file(s), new TreeSet<>(BOARDS)); } catch (Exception e) { System.err.println("A&P Craft board save: "+e); }
    }
    /** Register a naturally placed airport job board; no player command required. */
    public static void registerAirportBoard(ServerWorld world, BlockPos pos) {
        if (world.getBlockState(pos).isOf(Blocks.LECTERN)) {
            BOARDS.add(key(world, pos));
            save(world.getServer());
        }
    }
    private static void show(ServerPlayerEntity p) {
        p.sendMessage(Text.literal("[HANGAR JOB BOARD] Paid work orders — tools required; one paid job per Minecraft day."),false);
        for (int i=0;i<JOBS.length;i++) {
            int id=i+1;
            p.sendMessage(Text.literal("[ACCEPT "+id+"] "+JOBS[i]).styled(s->s.withColor(Formatting.AQUA)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/work start "+id))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,Text.literal("Accept work order "+id)))),false);
        }
        p.sendMessage(Text.literal("[CONTINUE CURRENT JOB]").styled(s->s.withColor(Formatting.GREEN)
            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/work status"))),false);
        p.sendMessage(Text.literal("[VIEW MECHANIC PROFILE]").styled(s->s.withColor(Formatting.GOLD)
            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/career"))),false);
    }
    private static void showAcademy(ServerPlayerEntity p) {
        p.sendMessage(Text.literal("[FAA TRAINING CENTER] Choose a study path. FAA handbook references are displayed in each lesson."), false);
        for (int i=1;i<=12;i++) {
            int id=i;
            p.sendMessage(Text.literal("[LESSON "+id+"] "+(id<=4?"GENERAL":id<=7?"AIRFRAME":"POWERPLANT"))
                .styled(style->style.withColor(Formatting.AQUA)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/faa start "+id))),false);
        }
        p.sendMessage(Text.literal("[PRACTICAL TRAINING]").styled(style->style.withColor(Formatting.GREEN)
            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/practical list"))),false);
    }
    private static void spawnInstructor(ServerPlayerEntity p) {
        ServerWorld w=p.getServerWorld();
        boolean exists=!w.getEntitiesByClass(VillagerEntity.class,p.getBoundingBox().expand(96),
            v->v.getCommandTags().contains(INSTRUCTOR_TAG)).isEmpty();
        if (exists) return;
        VillagerEntity npc=EntityType.VILLAGER.create(w);
        if(npc==null)return;
        npc.refreshPositionAndAngles(p.getX()-3,p.getY(),p.getZ()+3,0,0);
        npc.setVillagerData(new VillagerData(VillagerType.PLAINS,VillagerProfession.LIBRARIAN,2));
        npc.setCustomName(Text.literal("FAA Training Instructor"));
        npc.setCustomNameVisible(true);
        npc.setPersistent();
        npc.addCommandTag(INSTRUCTOR_TAG);
        w.spawnEntity(npc);
    }
    private static void placeStarterBoard(ServerPlayerEntity p) {
        ServerWorld w=p.getServerWorld();
        for (int dx=-4;dx<=4;dx++) for (int dz=2;dz<=6;dz++) {
            BlockPos pos=p.getBlockPos().add(dx,0,dz);
            if (w.getBlockState(pos).isAir() && w.getBlockState(pos.up()).isAir()
                && w.getBlockState(pos.down()).isSideSolidFullSquare(w,pos.down(),net.minecraft.util.math.Direction.UP)) {
                w.setBlockState(pos,Blocks.LECTERN.getDefaultState());
                BOARDS.add(key(w,pos));save(p.getServer());return;
            }
        }
    }
    public static void initialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(JobBoard::load);
        // Automatic, command-free entry point. Existing dispatchers are reused.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
            ServerPlayerEntity p = handler.player;
            ServerWorld w = p.getServerWorld();
            boolean exists = !w.getEntitiesByClass(VillagerEntity.class,
                p.getBoundingBox().expand(96),
                v -> v.getCommandTags().contains(DISPATCHER_TAG)).isEmpty();
            if (!exists) {
                VillagerEntity npc = EntityType.VILLAGER.create(w);
                if (npc != null) {
                    npc.refreshPositionAndAngles(p.getX()+3, p.getY(), p.getZ()+3, 0, 0);
                    npc.setVillagerData(new VillagerData(VillagerType.PLAINS,VillagerProfession.TOOLSMITH,2));
                    npc.setCustomName(Text.literal("Hangar Dispatcher"));
                    npc.setCustomNameVisible(true);
                    npc.setPersistent();
                    npc.addCommandTag(DISPATCHER_TAG);
                    w.spawnEntity(npc);
                }
            }
            spawnInstructor(p);
            boolean nearbyBoard=false;
            for (int dx=-8;dx<=8 && !nearbyBoard;dx++) for(int dz=-8;dz<=8;dz++) {
                BlockPos pos=p.getBlockPos().add(dx,0,dz);
                if(BOARDS.contains(key(w,pos)) && w.getBlockState(pos).isOf(Blocks.LECTERN)) {nearbyBoard=true;break;}
            }
            if(!nearbyBoard) placeStarterBoard(p);
            p.sendMessage(Text.literal("[A&P CRAFT] Welcome to your aviation career! Find the Hangar Dispatcher nearby and right-click to browse jobs."), false);
        }));
        UseBlockCallback.EVENT.register((player,world,hand,hit)->{
            if (world.isClient() || !(player instanceof ServerPlayerEntity p) || !(world instanceof ServerWorld sw)) return ActionResult.PASS;
            String k=key(sw,hit.getBlockPos());
            if (!BOARDS.contains(k)) return ActionResult.PASS;
            if (!world.getBlockState(hit.getBlockPos()).isOf(Blocks.LECTERN)) {
                BOARDS.remove(k); save(p.getServer()); return ActionResult.PASS;
            }
            show(p); return ActionResult.SUCCESS;
        });
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity p) ||
                !(entity instanceof VillagerEntity villager) || !(villager.getCommandTags().contains(DISPATCHER_TAG) || villager.getCommandTags().contains(INSTRUCTOR_TAG)))
                return ActionResult.PASS;
            if (hand != Hand.MAIN_HAND) return ActionResult.SUCCESS;
            if(villager.getCommandTags().contains(INSTRUCTOR_TAG))showAcademy(p);
            else show(p);
            return ActionResult.SUCCESS;
        });
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->registerCommands(dispatcher));
    }
    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("jobboard")
            .then(CommandManager.literal("dispatcher").requires(src->src.hasPermissionLevel(2)).executes(ctx->{
                ServerPlayerEntity p=ctx.getSource().getPlayerOrThrow();
                ServerWorld w=p.getServerWorld();
                VillagerEntity npc=EntityType.VILLAGER.create(w);
                if(npc==null) return 0;
                npc.refreshPositionAndAngles(p.getX()+1,p.getY(),p.getZ()+1,p.getYaw(),0);
                npc.setVillagerData(new VillagerData(VillagerType.PLAINS,VillagerProfession.TOOLSMITH,2));
                npc.setCustomName(Text.literal("Hangar Dispatcher"));
                npc.setCustomNameVisible(true);
                npc.setPersistent();
                npc.addCommandTag(DISPATCHER_TAG);
                w.spawnEntity(npc);
                p.sendMessage(Text.literal("[JOB BOARD] Hangar Dispatcher hired! Right-click to view paid maintenance work orders."),false);
                return 1;
            }))
            .then(CommandManager.literal("place").requires(src->src.hasPermissionLevel(2)).executes(ctx->{
                ServerPlayerEntity p=ctx.getSource().getPlayerOrThrow();
                ServerWorld w=p.getServerWorld(); BlockPos pos=p.getBlockPos().offset(p.getHorizontalFacing(),2);
                if (!w.getBlockState(pos).isAir() || !w.getBlockState(pos.up()).isAir()) {
                    p.sendMessage(Text.literal("[JOB BOARD] Clear a two-block-high space two blocks ahead."),false);return 0;
                }
                w.setBlockState(pos,Blocks.LECTERN.getDefaultState());
                BOARDS.add(key(w,pos));save(p.getServer());show(p);return 1;
            }))
            .then(CommandManager.literal("open").executes(ctx->{show(ctx.getSource().getPlayerOrThrow());return 1;}))
            .executes(ctx->{show(ctx.getSource().getPlayerOrThrow());return 1;}));
    }
}

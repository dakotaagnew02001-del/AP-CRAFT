package com.apcraft;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import java.nio.file.*;

/** One-time, naturally provisioned starter airfield near the world spawn.
 *  This is a deterministic starter-site generator, NOT biome structure worldgen.
 */
public final class Airfield {
    private Airfield() {}
    private static int ticks;
    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++ticks % 200 != 0) return;
            ServerWorld world = server.getOverworld();
            Path marker = server.getSavePath(WorldSavePath.ROOT).resolve("apcraft-airfield-generated.txt");
            if (Files.exists(marker)) {
                Path displayMarker = server.getSavePath(WorldSavePath.ROOT).resolve("apcraft-aircraft-displays-v24.txt");
                if (!Files.exists(displayMarker)) {
                    try {
                        String[] parts = Files.readString(marker).trim().split("\\s+");
                        if(parts.length >= 6) {
                            int oldX=Integer.parseInt(parts[3]), oldY=Integer.parseInt(parts[4]), oldZ=Integer.parseInt(parts[5]);
                            if(world.isChunkLoaded(new BlockPos(oldX+53,oldY,oldZ+4)) &&
                               world.isChunkLoaded(new BlockPos(oldX+150,oldY,oldZ+4))) {
                                AircraftDisplays.generate(world,oldX,oldY,oldZ);
                                Files.writeString(displayMarker,"Aircraft displays generated");
                            }
                        }
                    } catch(Exception e) {System.err.println("A&P Craft display migration: "+e);}
                }

                // Upgrade existing worlds: add the maintenance station without rebuilding the airfield.
                Path upgraded = server.getSavePath(WorldSavePath.ROOT).resolve("apcraft-c172-station-added.txt");
                if (!Files.exists(upgraded)) {
                    try {
                        String[] parts = Files.readString(marker).trim().split("\\s+");
                        if (parts.length >= 6) {
                            int oldX = Integer.parseInt(parts[3]);
                            int oldY = Integer.parseInt(parts[4]);
                            int oldZ = Integer.parseInt(parts[5]);
                            if (world.isChunkLoaded(new BlockPos(oldX + 12, oldY, oldZ + 10))) {
                                Cessna172Simulator.spawnAtAirfield(world,oldX,oldY,oldZ);
                                AviationBusiness.spawnAtAirfield(world,oldX,oldY,oldZ);
                                Files.writeString(upgraded,"Added C172 station");
                            }
                        }
                    } catch(Exception e) {System.err.println("A&P Craft C172 station migration: "+e);}
                }
                // Existing saves receive the new annex once, only after its chunks are loaded.
                Path hubMarker = server.getSavePath(WorldSavePath.ROOT).resolve("apcraft-airport-hub-v46.txt");
                if (!Files.exists(hubMarker)) {
                    try {
                        String[] parts = Files.readString(marker).trim().split("\\s+");
                        if (parts.length >= 6) {
                            int hx=Integer.parseInt(parts[3]), hy=Integer.parseInt(parts[4]), hz=Integer.parseInt(parts[5]);
                            if (world.isChunkLoaded(new BlockPos(hx+5,hy,hz+33)) &&
                                world.isChunkLoaded(new BlockPos(hx+31,hy,hz+47))) {
                                StarterAirportHub.generate(world,hx,hy,hz);
                                Files.writeString(hubMarker,"Starter airport training annex generated");
                            }
                        }
                    } catch (Exception e) {System.err.println("A&P Craft hub migration: "+e);}
                }
                return;
            }
            BlockPos spawn = world.getSpawnPos();
            int x = spawn.getX() + 96, z = spawn.getZ() + 96;
            // Wait for site chunks to be loaded by a nearby player. Never force-load remote chunks.
            if (!world.isChunkLoaded(new BlockPos(x, 64, z)) ||
                !world.isChunkLoaded(new BlockPos(x + 40, 64, z + 22))) return;
            int y = 65;
            for (int dx = -2; dx <= 42; dx += 4)
                for (int dz = -2; dz <= 24; dz += 4)
                    y = Math.max(y, world.getTopY(Heightmap.Type.WORLD_SURFACE, x + dx, z + dz) + 2);
            y = Math.min(y, world.getTopY() - 12);
            generate(world, x, y, z);
            try { Files.writeString(marker, "Starter airfield at " + x + " " + y + " " + z); }
            catch (Exception e) { System.err.println("A&P Craft: could not save airfield marker: " + e); }
        });
    }
    private static void put(ServerWorld w, int x,int y,int z,net.minecraft.block.Block block) {
        w.setBlockState(new BlockPos(x,y,z), block.getDefaultState(), 3);
    }
    private static void generate(ServerWorld w, int x,int y,int z) {
        // Hangar 21x17, with a wide front opening facing south.
        for(int a=0;a<=20;a++) for(int b=0;b<=16;b++) {
            put(w,x+a,y-1,z+b,Blocks.SMOOTH_STONE);
            for(int h=0;h<=7;h++) {
                if (h==7) put(w,x+a,y+h,z+b,Blocks.LIGHT_GRAY_CONCRETE);
                else if ((a==0 || a==20 || b==0 || b==16) && !(b==16 && a>=4 && a<=16 && h<=5))
                    put(w,x+a,y+h,z+b,Blocks.GRAY_CONCRETE);
                else put(w,x+a,y+h,z+b,Blocks.AIR);
            }
        }
        for(int a=2;a<=18;a+=4) put(w,x+a,y+6,z+8,Blocks.SEA_LANTERN);
        // Apron and runway, deliberately elevated to avoid cutting natural terrain.
        for(int a=0;a<=40;a++) for(int b=17;b<=23;b++)
            put(w,x+a,y-1,z+b,Blocks.GRAY_CONCRETE);
        for(int a=24;a<=40;a+=5) put(w,x+a,y-1,z+20,Blocks.WHITE_CONCRETE);
        // Decorative truck parked beside hangar; villager is the actual shop interface.
        for(int a=24;a<=30;a++) for(int b=2;b<=5;b++) {
            put(w,x+a,y-1,z+b,Blocks.BLACK_CONCRETE);
            for(int h=0;h<=2;h++) put(w,x+a,y+h,z+b,Blocks.RED_CONCRETE);
        }
        for(int a : new int[]{25,29}) for(int b : new int[]{1,6})
            put(w,x+a,y,z+b,Blocks.BLACK_CONCRETE);
        for(int a=24;a<=30;a++) put(w,x+a,y+3,z+2,Blocks.WHITE_CONCRETE);
        put(w,x+24,y+1,z+3,Blocks.GLASS);
        VillagerEntity dealer = EntityType.VILLAGER.create(w);
        if (dealer != null) {
            dealer.refreshPositionAndAngles(x+23.5,y,z+8.5,0,0);
            dealer.setCustomName(Text.literal("A&P Tool Truck Dealer"));
            dealer.setCustomNameVisible(true);
            dealer.addCommandTag("APCraftToolTruck");
            dealer.setPersistent();
            w.spawnEntity(dealer);
        }
        NpcMarketplace.spawnAtAirfield(w,x,y,z);
        Cessna172Simulator.spawnAtAirfield(w,x,y,z);
        AviationBusiness.spawnAtAirfield(w,x,y,z);
        BusinessFacilities.generate(w,x,y,z);
        CharterOperations.spawn(w,x,y,z);
        // New worlds use the same safe, chunk-aware migration path as existing saves.
        // The annex is generated on a later tick when both annex corner chunks are loaded.
        System.out.println("A&P Craft starter airfield generated at " + x + " " + y + " " + z);
    }
}

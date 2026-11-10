package com.apcraft;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import java.nio.file.*;
import java.util.*;

/** Exploration-triggered rural sites. Does not alter native structure generation. */
public final class RuralIndustries {
    private RuralIndustries() {}
    private static final String[] TYPES={"Family Farm","Dairy Farm","Grain Elevator","Lumber Yard","Quarry","Auto Repair Shop","Freight Depot","Equipment Rental Yard"};
    private static final String[] INFO={
        "Grow wheat and vegetables; farm equipment requires oil, belts and routine service.",
        "Care for livestock; refrigeration compressors and generators need maintenance.",
        "Grain handling uses conveyors, motors, bearings and dust control.",
        "Sawmills use drive belts, bearings, hydraulic equipment and guards.",
        "Heavy machinery uses hydraulic systems, engines and wear inspections.",
        "Diagnose charging, ignition and cooling systems on road vehicles.",
        "Cargo handling uses forklifts, diesel engines and loading equipment.",
        "Rent tractors, trailers and lifting equipment for industrial jobs."};
    private static final Set<String> seen=new HashSet<>();
    private static int ticks;
    public static void initialize(){
        UseEntityCallback.EVENT.register((player,world,hand,entity,hit)->{
            if(!(entity instanceof VillagerEntity v))return ActionResult.PASS;
            String tag=null;
            for(String t:v.getCommandTags())if(t.startsWith("APCraftIndustry:")){tag=t;break;}
            if(tag==null)return ActionResult.PASS;
            if(!world.isClient && player instanceof ServerPlayerEntity p){
                int id=Integer.parseInt(tag.substring("APCraftIndustry:".length()));
                p.sendMessage(Text.literal("["+TYPES[id]+"] "+INFO[id]),false);
                IndustryContracts.show(p,v,id);
            }
            return ActionResult.SUCCESS;
        });
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(++ticks%160!=0)return;
            ServerWorld w=server.getOverworld();
            Path folder=server.getSavePath(WorldSavePath.ROOT).resolve("apcraft-rural-industries");
            for(ServerPlayerEntity p:w.getPlayers()){
                int gx=Math.floorDiv(p.getBlockX(),384),gz=Math.floorDiv(p.getBlockZ(),384);
                for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
                    int rx=gx+dx,rz=gz+dz;
                    long hash=mix(w.getSeed(),rx,rz);
                    // Avoid the origin region, which contains the starter airport.
                    if(rx==0&&rz==0)continue;
                    int x=rx*384+112+(int)Math.floorMod(hash,90);
                    int z=rz*384+112+(int)Math.floorMod(hash>>>20,90);
                    if(Math.abs(p.getX()-x)>90||Math.abs(p.getZ()-z)>90)continue;
                    String key=rx+"_"+rz;
                    if(seen.contains(key))continue;
                    Path marker=folder.resolve(key+".txt");
                    if(Files.exists(marker)){seen.add(key);continue;}
                    if(!loaded(w,x-2,z-2,x+30,z+29))continue;
                    int low=Integer.MAX_VALUE,high=Integer.MIN_VALUE;
                    boolean bad=false;
                    for(int sx=x;sx<=x+28;sx+=4)for(int sz=z;sz<=z+27;sz+=4){
                        int top=w.getTopY(Heightmap.Type.WORLD_SURFACE,sx,sz);
                        Block b=w.getBlockState(new BlockPos(sx,top-1,sz)).getBlock();
                        if(b==Blocks.WATER||b==Blocks.LAVA||top<64||top>w.getTopY()-12)bad=true;
                        low=Math.min(low,top);high=Math.max(high,top);
                    }
                    if(bad||high-low>3)continue;
                    int type=(int)Math.floorMod(hash>>>36,TYPES.length);
                    try{
                        Files.createDirectories(folder);
                        // Persist before building to avoid duplication after a restart.
                        Files.writeString(marker,TYPES[type]+" "+x+" "+z);
                        seen.add(key);
                        generate(w,x,high+1,z,type);
                    }catch(Exception e){System.err.println("A&P Craft rural generation: "+e);}
                }
            }
        });
    }
    private static boolean loaded(ServerWorld w,int x1,int z1,int x2,int z2){
        for(int x=Math.floorDiv(x1,16);x<=Math.floorDiv(x2,16);x++)
            for(int z=Math.floorDiv(z1,16);z<=Math.floorDiv(z2,16);z++)
                if(!w.isChunkLoaded(new BlockPos(x*16,64,z*16)))return false;
        return true;
    }
    private static long mix(long seed,int x,int z){
        long h=seed^((long)x*341873128712L)^((long)z*132897987541L);
        h^=h>>>33;h*=0xff51afd7ed558ccdL;h^=h>>>33;return h;
    }
    private static void put(ServerWorld w,int x,int y,int z,Block b){w.setBlockState(new BlockPos(x,y,z),b.getDefaultState(),3);}
    private static void shed(ServerWorld w,int x,int y,int z,Block walls){
        for(int a=0;a<13;a++)for(int b=0;b<11;b++){
            put(w,x+a,y-1,z+b,Blocks.COBBLESTONE);
            for(int h=0;h<=5;h++){
                boolean edge=a==0||a==12||b==0||b==10;
                boolean door=b==10&&a>=4&&a<=8&&h<=3;
                put(w,x+a,y+h,z+b,h==5?Blocks.SPRUCE_PLANKS:edge&&!door?walls:Blocks.AIR);
            }
        }
        put(w,x+6,y+4,z+5,Blocks.SEA_LANTERN);
    }
    private static void generate(ServerWorld w,int x,int y,int z,int type){
        Block wall=(type==0||type==1)?Blocks.RED_TERRACOTTA:type==2?Blocks.LIGHT_GRAY_CONCRETE:type==3?Blocks.OAK_PLANKS:type==4?Blocks.STONE_BRICKS:type==5?Blocks.BLUE_CONCRETE:type==6?Blocks.YELLOW_CONCRETE:Blocks.GREEN_CONCRETE;
        shed(w,x,y,z,wall);
        for(int a=0;a<29;a++)for(int b=13;b<29;b++)put(w,x+a,y-1,z+b,type<=1?Blocks.FARMLAND:Blocks.GRAVEL);
        if(type==0){
            for(int a=1;a<28;a++)for(int b=14;b<28;b++){
                if(b%5==0)put(w,x+a,y-1,z+b,Blocks.WATER);
                else put(w,x+a,y,z+b,Blocks.WHEAT);
            }
        }else if(type==1){
            for(int a=1;a<28;a+=6)for(int b=14;b<28;b+=6)put(w,x+a,y,z+b,Blocks.HAY_BLOCK);
        }else if(type==2){
            for(int a=3;a<25;a+=8)for(int b=16;b<26;b+=7)for(int h=0;h<8;h++)put(w,x+a,y+h,z+b,Blocks.IRON_BLOCK);
        }else if(type==3){
            for(int a=2;a<27;a+=4)for(int b=16;b<27;b+=5)for(int h=0;h<3;h++)put(w,x+a,y+h,z+b,Blocks.OAK_LOG);
        }else if(type==4){
            for(int a=2;a<27;a+=5)for(int b=16;b<27;b+=5)put(w,x+a,y,z+b,Blocks.STONE);
        }else if(type==5){
            for(int a=2;a<27;a+=8)for(int b=16;b<27;b+=7)put(w,x+a,y,z+b,Blocks.ANVIL);
        }else if(type==6){
            for(int a=2;a<27;a+=6)for(int b=16;b<27;b+=5)put(w,x+a,y,z+b,Blocks.BARREL);
        }else{
            for(int a=2;a<27;a+=7)for(int b=16;b<27;b+=6)put(w,x+a,y,z+b,Blocks.SMITHING_TABLE);
        }
        VillagerEntity v=EntityType.VILLAGER.create(w);
        if(v!=null){v.refreshPositionAndAngles(x+6.5,y,z+8.5,0,0);v.setCustomName(Text.literal(TYPES[type]+" Manager"));v.setCustomNameVisible(true);v.addCommandTag("APCraftIndustry:"+type);v.setPersistent();w.spawnEntity(v);}
    }
}

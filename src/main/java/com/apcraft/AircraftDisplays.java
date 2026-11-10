package com.apcraft;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;

/** Four distinctive walk-around training airframes built from vanilla blocks.
 * These are static exhibits, not flyable aircraft entities or certified maintenance trainers.
 */
public final class AircraftDisplays {
    private AircraftDisplays() {}
    private static final String[] NAMES = {"Cessna 172 Skyhawk", "Beechcraft King Air 200", "Bell 206 JetRanger", "AH-64 Apache"};
    private static final String[] SUBJECTS = {
        "Piston single: inspect cowling, propeller, landing gear, ignition and engine oil. For an actual aircraft, use the applicable maintenance data.",
        "Twin turboprop: review PT6 engine sections, propeller governing, pressurization, electrical generation and landing gear.",
        "Single-engine helicopter: review freewheeling unit, transmission, rotor hub, tail rotor drive and turbine powerplant.",
        "Twin-engine attack helicopter: review rotor system, drive train, hydraulic systems, T700 powerplants and safety procedures."
    };
    private static void put(ServerWorld w,int x,int y,int z,Block b){w.setBlockState(new BlockPos(x,y,z),b.getDefaultState(),3);}
    private static void cuboid(ServerWorld w,int x,int y,int z,int dx,int dy,int dz,Block b){
        for(int a=0;a<dx;a++)for(int h=0;h<dy;h++)for(int c=0;c<dz;c++)put(w,x+a,y+h,z+c,b);
    }
    public static void initialize(){
        UseEntityCallback.EVENT.register((player,world,hand,entity,hit)->{
            if(!(entity instanceof VillagerEntity v))return ActionResult.PASS;
            for(int i=0;i<4;i++)if(v.getCommandTags().contains("apcraft_display_"+i)){
                if(!world.isClient && player instanceof ServerPlayerEntity p){
                    p.sendMessage(Text.literal("[AIRCRAFT WALK-AROUND] "+NAMES[i]),false);
                    p.sendMessage(Text.literal(SUBJECTS[i]),false);
                    p.sendMessage(Text.literal("Static training exhibit. The purchased fleet still uses temporary boat entities; this exhibit does not fly."),false);
                }
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });
    }
    public static void generate(ServerWorld w,int x,int y,int z){
        // Four open-air display pads east of the starter apron, each 24 x 26 blocks.
        for(int i=0;i<4;i++){
            int px=x+53+i*29, pz=z+4;
            cuboid(w,px-12,y-1,pz-12,25,1,25,Blocks.SMOOTH_STONE);
            for(int t=-10;t<=10;t++)put(w,px+t,y-1,pz+10,Blocks.YELLOW_CONCRETE);
            switch(i){
                case 0 -> { fixedWing(w,px,y,pz,false,false); CessnaWalkaround.addStations(w,px,y,pz); CessnaEngineBench.build(w,px,y,pz); CessnaCockpit.build(w,px,y,pz); }
                case 1 -> fixedWing(w,px,y,pz,true,false);
                case 2 -> rotor(w,px,y,pz,false);
                case 3 -> rotor(w,px,y,pz,true);
            }
            VillagerEntity guide=EntityType.VILLAGER.create(w);
            if(guide!=null){
                guide.refreshPositionAndAngles(px+8.5,y,pz+8.5,0,0);
                guide.setCustomName(Text.literal("Inspect: "+NAMES[i]));guide.setCustomNameVisible(true);
                guide.addCommandTag("apcraft_display_"+i);guide.setPersistent();w.spawnEntity(guide);
            }
        }
    }
    private static void fixedWing(ServerWorld w,int x,int y,int z,boolean twin,boolean unused){
        Block skin=twin?Blocks.WHITE_CONCRETE:Blocks.LIGHT_GRAY_CONCRETE;
        // Nose faces negative Z; wings stretch along X.
        cuboid(w,x-1,y+2,z-8,3,2,15,skin);
        cuboid(w,x-1,y+3,z-4,3,1,4,Blocks.LIGHT_BLUE_STAINED_GLASS);
        cuboid(w,x-10,y+3,z-1,21,1,4,skin);
        cuboid(w,x-5,y+3,z+6,11,1,2,skin);
        cuboid(w,x,y+4,z+5,1,3,2,skin);
        // Main wheels and nose gear.
        for(int a:new int[]{-2,2})put(w,x+a,y,z,Blocks.BLACK_CONCRETE);
        put(w,x,y,z-7,Blocks.BLACK_CONCRETE);
        if(twin){
            for(int a:new int[]{-6,6}){
                cuboid(w,x+a-1,y+2,z-3,3,2,5,Blocks.GRAY_CONCRETE);
                cuboid(w,x+a-3,y+2,z-4,7,1,1,Blocks.BLACK_CONCRETE);
                cuboid(w,x+a,y,z+1,1,2,1,Blocks.IRON_BLOCK);
            }
        }else{
            cuboid(w,x-3,y+2,z-9,7,1,1,Blocks.BLACK_CONCRETE);
        }
    }
    private static void rotor(ServerWorld w,int x,int y,int z,boolean apache){
        Block skin=apache?Blocks.GREEN_TERRACOTTA:Blocks.WHITE_CONCRETE;
        cuboid(w,x-2,y+2,z-5,5,3,10,skin);
        cuboid(w,x-2,y+4,z-5,5,1,4,Blocks.LIGHT_BLUE_STAINED_GLASS);
        cuboid(w,x,y+3,z+5,1,1,10,skin);
        cuboid(w,x,y+4,z+13,1,4,1,skin);
        cuboid(w,x-3,y+7,z,7,1,1,Blocks.BLACK_CONCRETE);
        cuboid(w,x,y+7,z-10,1,1,21,Blocks.BLACK_CONCRETE);
        cuboid(w,x-4,y,z-3,1,1,10,Blocks.IRON_BLOCK);
        cuboid(w,x+4,y,z-3,1,1,10,Blocks.IRON_BLOCK);
        for(int a:new int[]{-3,3})for(int c:new int[]{-2,4})cuboid(w,x+a,y+1,z+c,1,2,1,Blocks.IRON_BLOCK);
        if(apache){
            cuboid(w,x-7,y+3,z-1,5,1,3,skin);
            cuboid(w,x+3,y+3,z-1,5,1,3,skin);
            cuboid(w,x-1,y+1,z-7,3,1,3,Blocks.BLACK_CONCRETE);
        }
    }
}

package com.apcraft;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Blocks;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Walk-up component interactions for the static C172 airframe, with saved work-order progress.
 * No claim of aircraft-accurate dimensions, flight physics or FAA-approved procedure.
 */
public final class CessnaWalkaround {
    private CessnaWalkaround() {}
    private static final String TAG="apcraft_display_0";
    private static final String[] STEPS={
        "Review discrepancy at the inspection stand",
        "Chock and secure the aircraft at the nose gear",
        "Open the cowling at the nose",
        "Inspect the engine compartment with a flashlight",
        "Inspect the left wing and fuel cap",
        "Inspect the right wing and fuel cap",
        "Inspect the propeller for visible damage",
        "Complete the maintenance record at the inspection stand"
    };
    public static void initialize(){
        UseBlockCallback.EVENT.register((player,world,hand,hit)->{
            if(world.isClient || !(player instanceof ServerPlayerEntity p))return ActionResult.PASS;
            BlockPos pos=hit.getBlockPos();
            List<VillagerEntity> guides=((ServerWorld)world).getEntitiesByClass(VillagerEntity.class,
                p.getBoundingBox().expand(32),v->v.getCommandTags().contains(TAG));
            for(VillagerEntity guide:guides){
                int x=guide.getBlockX()-8, y=guide.getBlockY(), z=guide.getBlockZ()-8;
                int action=identify(pos,x,y,z);
                if(action<0)continue;
                handle(p,action);
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });
    }
    private static int identify(BlockPos p,int x,int y,int z){
        if(p.equals(new BlockPos(x+6,y,z+7)))return 0; // inspection stand
        if(p.equals(new BlockPos(x,y+1,z-7)))return 1; // chock station
        if(p.equals(new BlockPos(x,y+3,z-9)))return 2; // cowling access
        if(p.equals(new BlockPos(x,y+4,z-7)))return 3; // engine bay
        if(p.equals(new BlockPos(x-8,y+4,z)))return 4;
        if(p.equals(new BlockPos(x+8,y+4,z)))return 5;
        if(p.equals(new BlockPos(x,y+2,z-10)))return 6;
        return -1;
    }
    private static Path file(ServerPlayerEntity p){
        return p.getServerWorld().getServer().getRunDirectory().toPath().resolve("apcraft-c172-walkaround")
            .resolve(p.getUuidAsString()+".txt");
    }
    private static int stage(ServerPlayerEntity p){
        try{return Integer.parseInt(Files.readString(file(p)).trim());}catch(Exception ignored){return 0;}
    }
    private static void save(ServerPlayerEntity p,int stage){
        try{Files.createDirectories(file(p).getParent());Files.writeString(file(p),Integer.toString(stage));}
        catch(Exception e){p.sendMessage(Text.literal("Could not save training progress: "+e.getMessage()),false);}
    }
    private static void handle(ServerPlayerEntity p,int action){
        int stage=stage(p);
        if(action==0){
            if(stage==0){save(p,1);p.sendMessage(Text.literal("WORK ORDER: Pilot reports rough engine during magneto check. Begin with aircraft safety. This is a simulated walk-around, not a real maintenance procedure."),false);}
            else if(stage==7){save(p,8);p.sendMessage(Text.literal("TRAINING COMPLETE: Inspection observations entered. No return-to-service authorization is implied. Revisit the stand to begin another scenario."),false);}
            else if(stage>=8){save(p,1);p.sendMessage(Text.literal("New training walk-around started."),false);}
            else p.sendMessage(Text.literal("Current step "+(stage+1)+"/8: "+STEPS[stage]),false);
            return;
        }
        if(stage==0){p.sendMessage(Text.literal("Read the work order at the lectern before touching the aircraft."),false);return;}
        if(stage>=8){p.sendMessage(Text.literal("Return to the inspection stand to start another work order."),false);return;}
        if(action!=stage){p.sendMessage(Text.literal("Complete step "+(stage+1)+" first: "+STEPS[stage]),false);return;}
        if(action==3 && !p.getMainHandStack().isOf(Items.TORCH) && !p.getOffHandStack().isOf(Items.TORCH)){
            p.sendMessage(Text.literal("Equip a torch as a simulated inspection light."),false);return;
        }
        if(action==2)p.sendMessage(Text.literal("Cowling access opened (training state). In real maintenance, use the aircraft-specific maintenance manual."),false);
        if(action==3)p.sendMessage(Text.literal("Engine bay inspected: training discrepancy recorded at cylinder 1. Use the dedicated C172 simulator for compression and ignition troubleshooting."),false);
        if(action==4||action==5)p.sendMessage(Text.literal("Wing inspection recorded: fuel cap and external surfaces visually checked."),false);
        if(action==6)p.sendMessage(Text.literal("Propeller visual inspection recorded. Never move a propeller without applying proper aircraft safety precautions."),false);
        save(p,stage+1);
        p.sendMessage(Text.literal("Step complete. Next: "+STEPS[stage+1]),false);
    }
    public static void addStations(ServerWorld w,int x,int y,int z){
        w.setBlockState(new BlockPos(x+6,y,z+7),Blocks.LECTERN.getDefaultState(),3);
        w.setBlockState(new BlockPos(x,y+1,z-7),Blocks.YELLOW_CONCRETE.getDefaultState(),3);
        w.setBlockState(new BlockPos(x,y+3,z-9),Blocks.IRON_TRAPDOOR.getDefaultState(),3);
        w.setBlockState(new BlockPos(x,y+4,z-7),Blocks.IRON_BLOCK.getDefaultState(),3);
        w.setBlockState(new BlockPos(x-8,y+4,z),Blocks.BLUE_CONCRETE.getDefaultState(),3);
        w.setBlockState(new BlockPos(x+8,y+4,z),Blocks.BLUE_CONCRETE.getDefaultState(),3);
        w.setBlockState(new BlockPos(x,y+2,z-10),Blocks.ANVIL.getDefaultState(),3);
    }
}

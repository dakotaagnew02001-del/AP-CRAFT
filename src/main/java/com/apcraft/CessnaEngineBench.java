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
import net.minecraft.util.WorldSavePath;
import java.nio.file.*;

/** Interactive physical engine-bay training rig at the C172 display. Not approved maintenance data. */
public final class CessnaEngineBench {
    private CessnaEngineBench() {}
    private static final String TAG="apcraft_display_0";
    private static final int COWL=0, MAG=1, CYLINDER=2, GAUGE=3, LOG=4;
    private static class State { boolean cowl, magnetoFault=true, cylinderFault=true, testedMag, testedCylinder, signed; }
    private static Path path(ServerPlayerEntity p) {
        return p.getServer().getSavePath(WorldSavePath.ROOT).resolve("apcraft-c172-engine")
            .resolve(p.getUuidAsString()+".txt");
    }
    private static State load(ServerPlayerEntity p){
        State s=new State();
        try {
            String[] a=Files.readString(path(p)).trim().split(",");
            if(a.length==6){s.cowl=Boolean.parseBoolean(a[0]);s.magnetoFault=Boolean.parseBoolean(a[1]);
                s.cylinderFault=Boolean.parseBoolean(a[2]);s.testedMag=Boolean.parseBoolean(a[3]);
                s.testedCylinder=Boolean.parseBoolean(a[4]);s.signed=Boolean.parseBoolean(a[5]);}
        }catch(Exception ignored){}
        return s;
    }
    private static void save(ServerPlayerEntity p,State s){
        try{Files.createDirectories(path(p).getParent());
            Files.writeString(path(p),s.cowl+","+s.magnetoFault+","+s.cylinderFault+","+s.testedMag+","+s.testedCylinder+","+s.signed);
        }catch(Exception e){say(p,"Save failed: "+e.getMessage());}
    }
    private static void say(ServerPlayerEntity p,String msg){p.sendMessage(Text.literal("[C172 ENGINE] "+msg),false);}
    private static boolean has(ServerPlayerEntity p,net.minecraft.item.Item item){
        return p.getMainHandStack().isOf(item)||p.getOffHandStack().isOf(item);
    }
    private static int target(BlockPos pos,int x,int y,int z){
        if(pos.equals(new BlockPos(x-3,y+2,z-8)))return COWL;
        if(pos.equals(new BlockPos(x-3,y+2,z-7)))return MAG;
        if(pos.equals(new BlockPos(x-3,y+2,z-6)))return CYLINDER;
        if(pos.equals(new BlockPos(x-3,y+2,z-5)))return GAUGE;
        if(pos.equals(new BlockPos(x-3,y+2,z-4)))return LOG;
        return -1;
    }
    /** Simplified ground-start interlock shared with the cockpit. */
    public static boolean readyForStart(ServerPlayerEntity p){
        State s=load(p);
        return !s.cowl && !s.magnetoFault && !s.cylinderFault;
    }
    public static void initialize(){
        UseBlockCallback.EVENT.register((player,world,hand,hit)->{
            if(world.isClient||!(player instanceof ServerPlayerEntity p))return ActionResult.PASS;
            if(hand!=net.minecraft.util.Hand.MAIN_HAND)return ActionResult.PASS;
            ServerWorld w=(ServerWorld)world;
            for(VillagerEntity guide:w.getEntitiesByClass(VillagerEntity.class,p.getBoundingBox().expand(40),
                    v->v.getCommandTags().contains(TAG))){
                int x=guide.getBlockX()-8,y=guide.getBlockY(),z=guide.getBlockZ()-8;
                int t=target(hit.getBlockPos(),x,y,z);
                if(t<0)continue;
                handle(p,w,hit.getBlockPos(),t);
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });
    }
    private static void handle(ServerPlayerEntity p,ServerWorld w,BlockPos pos,int t){
        State s=load(p);
        if(t==COWL){
            if(!has(p,Items.IRON_PICKAXE)){say(p,"Hold an iron pickaxe as the prototype fastener tool to open/close the cowling.");return;}
            s.cowl=!s.cowl;
            w.setBlockState(pos,(s.cowl?Blocks.OAK_TRAPDOOR:Blocks.IRON_TRAPDOOR).getDefaultState(),3);
            say(p,s.cowl?"Cowling access opened. Engine must remain off.":"Cowling closed and latched (training simulation).");
        }else if(t==MAG){
            if(!s.cowl){say(p,"Open the cowling first.");return;}
            if(has(p,Items.REDSTONE_TORCH)){
                s.testedMag=true;
                say(p,s.magnetoFault?"Timing-light test: simulated magneto timing out of specification. Repair required.":"Timing-light test: simulated ignition timing within training limits.");
            }else if(has(p,Items.IRON_INGOT)){
                if(!s.testedMag){say(p,"Diagnose ignition timing first with a redstone torch (training timing light).");return;}
                s.magnetoFault=false;s.signed=false;
                say(p,"Magneto adjustment completed. Recheck timing with the simulated timing light.");
            }else say(p,"Hold a redstone torch to test timing or iron ingot to adjust after diagnosis.");
        }else if(t==CYLINDER){
            if(!s.cowl){say(p,"Open the cowling first.");return;}
            if(has(p,Items.GLASS_BOTTLE)){
                s.testedCylinder=true;
                say(p,s.cylinderFault?"Cylinder 1: 58/80 simulated compression; others 74–76/80. Investigate per applicable engine data.":"Cylinder 1: 75/80 simulated compression after component replacement.");
            }else if(has(p,Items.IRON_INGOT)){
                if(!s.testedCylinder){say(p,"Test compression first using a glass bottle (prototype gauge).");return;}
                s.cylinderFault=false;s.signed=false;
                say(p,"Training cylinder component replaced. Repeat simulated compression test to verify.");
            }else say(p,"Hold a glass bottle for the training compression gauge, or iron ingot for replacement after diagnosis.");
        }else if(t==GAUGE){
            boolean rough=s.magnetoFault||s.cylinderFault;
            say(p,"SIMULATED PANEL: electrical bus 28.1 V; oil pressure 72 PSI; engine idle "+(rough?"rough / maintenance required":"stable / ready for ground check")+".");
            say(p,"This panel is not a flight instrument and does not authorize engine operation.");
        }else if(t==LOG){
            if(s.cowl){say(p,"Close the cowling before completing the work order.");return;}
            if(s.magnetoFault||s.cylinderFault||!s.testedMag||!s.testedCylinder){
                say(p,"Cannot close work order: faults or required verification tests remain.");return;
            }
            if(!s.signed){s.signed=true;say(p,"Training work order documented. No FAA return-to-service approval or real signature implied.");}
            else say(p,"Work order already documented. Both simulated faults cleared.");
        }
        save(p,s);
    }
    public static void build(ServerWorld w,int x,int y,int z){
        // Distinct clickable rig blocks next to the aircraft nose; keep existing walkaround points intact.
        w.setBlockState(new BlockPos(x-3,y+2,z-8),Blocks.IRON_TRAPDOOR.getDefaultState(),3);
        w.setBlockState(new BlockPos(x-3,y+2,z-7),Blocks.REDSTONE_LAMP.getDefaultState(),3);
        w.setBlockState(new BlockPos(x-3,y+2,z-6),Blocks.PISTON.getDefaultState(),3);
        w.setBlockState(new BlockPos(x-3,y+2,z-5),Blocks.OBSERVER.getDefaultState(),3);
        w.setBlockState(new BlockPos(x-3,y+2,z-4),Blocks.LECTERN.getDefaultState(),3);
    }
}

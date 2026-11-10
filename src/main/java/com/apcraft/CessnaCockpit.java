package com.apcraft;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Blocks;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.WorldSavePath;
import java.nio.file.*;

/** Physical cockpit control station beside the C172 exhibit. Simplified ground-only simulation. */
public final class CessnaCockpit {
    private CessnaCockpit() {}
    private static final String GUIDE="apcraft_display_0";
    private static class Controls { boolean master,magnetos,engine; int throttle; }
    private static Path path(ServerPlayerEntity p) {return p.getServer().getSavePath(WorldSavePath.ROOT).resolve("apcraft-c172-cockpit").resolve(p.getUuidAsString()+".txt");}
    private static Controls load(ServerPlayerEntity p){
        Controls c=new Controls();
        try {String[] s=Files.readString(path(p)).trim().split(","); if(s.length==4){c.master=Boolean.parseBoolean(s[0]);c.magnetos=Boolean.parseBoolean(s[1]);c.engine=Boolean.parseBoolean(s[2]);c.throttle=Integer.parseInt(s[3]);}}catch(Exception ignored){}
        return c;
    }
    private static void save(ServerPlayerEntity p,Controls c){try{Files.createDirectories(path(p).getParent());Files.writeString(path(p),c.master+","+c.magnetos+","+c.engine+","+c.throttle);}catch(Exception e){say(p,"Unable to save: "+e.getMessage());}}
    private static void say(ServerPlayerEntity p,String s){p.sendMessage(Text.literal("[C172 COCKPIT] "+s),false);}
    public static void initialize(){
        UseBlockCallback.EVENT.register((player,world,hand,hit)->{
            if(world.isClient || hand!=Hand.MAIN_HAND || !(player instanceof ServerPlayerEntity p))return ActionResult.PASS;
            ServerWorld w=(ServerWorld)world;
            for(VillagerEntity guide:w.getEntitiesByClass(VillagerEntity.class,p.getBoundingBox().expand(40),v->v.getCommandTags().contains(GUIDE))){
                int x=guide.getBlockX()-8,y=guide.getBlockY(),z=guide.getBlockZ()-8;
                BlockPos pos=hit.getBlockPos();
                int id=-1;
                for(int i=0;i<5;i++)if(pos.equals(new BlockPos(x+5+i,y+1,z-8)))id=i;
                if(id<0)continue;
                interact(p,id);
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });
    }
    private static void interact(ServerPlayerEntity p,int id){
        Controls c=load(p);
        switch(id){
            case 0 -> {c.master=!c.master; if(!c.master)c.engine=false;say(p,"Electrical master "+(c.master?"ON":"OFF")+". Engine "+(c.engine?"running":"stopped")+" (simplified training behavior).");}
            case 1 -> {c.magnetos=!c.magnetos;if(!c.magnetos)c.engine=false;say(p,"Ignition selector "+(c.magnetos?"BOTH":"OFF")+".");}
            case 2 -> {if(c.engine){c.engine=false;say(p,"Engine shut down.");}
                else if(!c.master||!c.magnetos){say(p,"Start inhibited: master and ignition must be on.");}
                else if(!CessnaEngineBench.readyForStart(p)){say(p,"Start inhibited: cowling open or unresolved maintenance discrepancy. Inspect engine rig.");}
                else {c.engine=true;say(p,"Engine started (ground-training simulation). Monitor instruments before adjusting throttle.");}}
            case 3 -> {if(!c.engine){say(p,"Throttle control requires a running engine.");}else {c.throttle=(c.throttle+1)%4;say(p,"Throttle setting "+c.throttle+" of 3; simulated RPM "+(700+c.throttle*450)+".");}}
            case 4 -> {int rpm=c.engine?700+c.throttle*450:0;
                say(p,"GROUND PANEL | RPM "+rpm+" | bus "+(c.master?"28.1":"0.0")+" V | oil pressure "+(c.engine?"72":"0")+" PSI | ignition "+(c.magnetos?"BOTH":"OFF"));
                say(p,"Maintenance readiness: "+(CessnaEngineBench.readyForStart(p)?"no active training discrepancies":"inspection required")+". Readings are fictional, not aircraft operating limits.");}
        }
        save(p,c);
    }
    public static void build(ServerWorld w,int x,int y,int z){
        // Five distinct clickable controls at the starboard side of the nose.
        net.minecraft.block.Block[] b={Blocks.LEVER,Blocks.REPEATER,Blocks.STONE_BUTTON,Blocks.COMPARATOR,Blocks.DAYLIGHT_DETECTOR};
        for(int i=0;i<b.length;i++){
            w.setBlockState(new BlockPos(x+5+i,y,z-8),Blocks.IRON_BLOCK.getDefaultState(),3);
            w.setBlockState(new BlockPos(x+5+i,y+1,z-8),b[i].getDefaultState(),3);
        }
        w.setBlockState(new BlockPos(x+7,y,z-7),Blocks.SEA_LANTERN.getDefaultState(),3);
    }
}

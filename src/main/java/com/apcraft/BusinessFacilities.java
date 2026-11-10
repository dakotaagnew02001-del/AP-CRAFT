package com.apcraft;

import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/** Small, visitable business office at the starter airfield. No claims of ownership of world blocks. */
public final class BusinessFacilities {
    private BusinessFacilities() {}
    public static void generate(ServerWorld w,int x,int y,int z){
        int ox=x+45,oz=z+2;
        for(int dx=0;dx<13;dx++)for(int dz=0;dz<11;dz++){
            w.setBlockState(new BlockPos(ox+dx,y-1,oz+dz),Blocks.POLISHED_ANDESITE.getDefaultState(),3);
            for(int h=0;h<6;h++){
                boolean wall=dx==0||dx==12||dz==0||dz==10;
                boolean doorway=dz==10&&dx>=5&&dx<=7&&h<3;
                var b=h==5?Blocks.BLUE_CONCRETE:wall&&!doorway?Blocks.WHITE_CONCRETE:Blocks.AIR;
                w.setBlockState(new BlockPos(ox+dx,y+h,oz+dz),b.getDefaultState(),3);
            }
        }
        w.setBlockState(new BlockPos(ox+3,y+2,oz+10),Blocks.GLASS_PANE.getDefaultState(),3);
        w.setBlockState(new BlockPos(ox+9,y+2,oz+10),Blocks.GLASS_PANE.getDefaultState(),3);
        w.setBlockState(new BlockPos(ox+6,y+4,oz+5),Blocks.SEA_LANTERN.getDefaultState(),3);
        w.setBlockState(new BlockPos(ox+4,y,oz+3),Blocks.LECTERN.getDefaultState(),3);
        if(w.getEntitiesByClass(VillagerEntity.class,new Box(ox,y-2,oz,ox+13,y+7,oz+11),
            v->v.getCommandTags().contains("APCraftBusinessBroker")).isEmpty()){
            VillagerEntity npc=EntityType.VILLAGER.create(w);
            if(npc!=null){npc.refreshPositionAndAngles(ox+6.5,y,oz+5.5,0,0);
                npc.setCustomName(Text.literal("Aviation Business Office Manager"));npc.setCustomNameVisible(true);
                npc.addCommandTag("APCraftBusinessBroker");npc.setPersistent();w.spawnEntity(npc);}
        }
    }
}

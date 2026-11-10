package com.apcraft;

import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.VillagerProfession;
import net.minecraft.village.VillagerData;
import net.minecraft.village.VillagerType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/** Small permanent training and employment annex at the starter airfield.
 * Coordinates are relative to the airfield marker, not to player login.
 * This deliberately does not regenerate once the migration marker is written.
 */
public final class StarterAirportHub {
    private StarterAirportHub() {}
    private static void block(ServerWorld w, int x, int y, int z, net.minecraft.block.Block b) {
        w.setBlockState(new BlockPos(x,y,z), b.getDefaultState(), 3);
    }
    private static void npc(ServerWorld w, int x,int y,int z,String name,String tag,VillagerProfession job) {
        Box box = new Box(x-3,y-2,z-3,x+4,y+5,z+4);
        if (!w.getEntitiesByClass(VillagerEntity.class,box,v->v.getCommandTags().contains(tag)).isEmpty()) return;
        VillagerEntity v=EntityType.VILLAGER.create(w);
        if(v==null)return;
        v.refreshPositionAndAngles(x+0.5,y,z+0.5,0,0);
        v.setVillagerData(new VillagerData(VillagerType.PLAINS,job,2));
        v.setCustomName(Text.literal(name));v.setCustomNameVisible(true);
        v.addCommandTag(tag);v.setPersistent();w.spawnEntity(v);
    }
    public static void generate(ServerWorld w,int x,int y,int z) {
        // Annex sits beyond the apron, away from the original hangar and business office.
        int ox=x+5, oz=z+33;
        for(int dx=0;dx<27;dx++)for(int dz=0;dz<15;dz++) {
            block(w,ox+dx,y-1,oz+dz,Blocks.POLISHED_ANDESITE);
            for(int h=0;h<7;h++) {
                boolean wall=dx==0||dx==26||dz==0||dz==14;
                boolean entrance=dz==0 && ((dx>=5&&dx<=7)||(dx>=19&&dx<=21))&&h<=2;
                boolean window=wall && h>=2&&h<=3 && ((dx==0||dx==26) ? dz%4==2 : dx%4==2);
                block(w,ox+dx,y+h,oz+dz,h==6?Blocks.DARK_PRISMARINE:entrance?Blocks.AIR:window?Blocks.GLASS:wall?Blocks.WHITE_CONCRETE:Blocks.AIR);
            }
        }
        // Interior division and lighting. The walkway stays open between both rooms.
        for(int dz=1;dz<14;dz++) for(int h=0;h<5;h++)
            if(!(dz>=6&&dz<=8&&h<=2))block(w,ox+13,y+h,oz+dz,Blocks.LIGHT_GRAY_CONCRETE);
        for(int dx=3;dx<26;dx+=5)for(int dz=3;dz<14;dz+=5)
            block(w,ox+dx,y+5,oz+dz,Blocks.SEA_LANTERN);
        // Employment desk: this lectern is registered with the actual job-board handler.
        BlockPos board=new BlockPos(ox+6,y,oz+5);
        block(w,board.getX(),board.getY(),board.getZ(),Blocks.LECTERN);
        JobBoard.registerAirportBoard(w,board);
        npc(w,ox+9,y,oz+9,"Hangar Dispatcher","apcraft_hangar_dispatcher",VillagerProfession.TOOLSMITH);
        npc(w,ox+18,y,oz+10,"FAA Training Instructor","apcraft_faa_instructor",VillagerProfession.LIBRARIAN);
        // Four registered hands-on decision stations, not just decorative workbenches.
        net.minecraft.block.Block[] benches={Blocks.SMITHING_TABLE,Blocks.REDSTONE_LAMP,Blocks.LECTERN,Blocks.BLAST_FURNACE};
        for(int i=0;i<benches.length;i++) {
            BlockPos p=new BlockPos(ox+16+i*3,y,oz+4);
            block(w,p.getX(),p.getY(),p.getZ(),benches[i]);
            FaaPracticals.registerAirportBench(w,p,i);
        }
        // Lit paved path connecting the annex to the airfield apron.
        for(int dz=24;dz<33;dz++)for(int dx=5;dx<=7;dx++)
            block(w,x+dx,y-1,z+dz,Blocks.SMOOTH_STONE);
        for(int dz=25;dz<33;dz+=4)block(w,x+5,y,z+dz,Blocks.LANTERN);
    }
}

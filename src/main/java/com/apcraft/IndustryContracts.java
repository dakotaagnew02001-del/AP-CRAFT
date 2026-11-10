package com.apcraft;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.WorldSavePath;
import java.nio.file.*;
import java.util.*;

/** NPC-issued, location-bound, staged industry contracts; educational gameplay only. */
public final class IndustryContracts {
    private IndustryContracts() {}
    private static MinecraftServer server;
    private static final Map<String,Job> jobs=new HashMap<>();
    private static final String[] TITLES={
        "Crop-duster preflight inspection", "Dairy generator service", "Grain conveyor motor inspection",
        "Helicopter utility inspection", "Quarry hydraulic equipment inspection", "Fleet vehicle charging diagnosis",
        "Cargo aircraft turnaround inspection", "Rental equipment hydraulic inspection"};
    private static final String[] STEPS={
        "Inspect agricultural aircraft fuel and spray-system condition", "Inspect generator belts and electrical connections",
        "Inspect conveyor guards and bearings", "Inspect helicopter sling-load equipment and attachment points",
        "Inspect hydraulic lines and visible leaks", "Check vehicle battery and charging circuit",
        "Inspect cargo restraint points and aircraft loading documentation", "Inspect rental equipment hoses and fluid condition"};
    private static final String[] VERIFY={
        "Review aircraft discrepancies and approved maintenance data", "Confirm generator operation and voltage",
        "Confirm guards are installed and motor operation is normal", "Verify helicopter equipment condition against applicable instructions",
        "Verify hydraulic system condition after servicing", "Confirm charging voltage after diagnosis",
        "Confirm cargo is secured and weight-and-balance paperwork reviewed", "Verify equipment operation and leak-free condition"};
    private static final int[] PAY={220,130,145,390,180,120,320,165};
    private static final class Job {String site;int type;int stage;long completedDay=-1;}
    private static Path folder(){return server.getSavePath(WorldSavePath.ROOT).resolve("apcraft-industry-contracts");}
    private static String key(ServerPlayerEntity p){return p.getUuidAsString();}
    private static void save(ServerPlayerEntity p){
        if(server==null)return;
        Job j=jobs.get(key(p));if(j==null)return;
        try {Files.createDirectories(folder());Files.writeString(folder().resolve(key(p)+".txt"),
            j.site+"\n"+j.type+"\n"+j.stage+"\n"+j.completedDay);}
        catch(Exception e){System.err.println("A&P Craft contract save: "+e);}
    }
    private static void load(){jobs.clear();try{
        if(!Files.isDirectory(folder()))return;
        try(var paths=Files.list(folder())){paths.filter(p->p.getFileName().toString().endsWith(".txt")).forEach(p->{try{
            List<String> l=Files.readAllLines(p);if(l.size()<4)return;
            Job j=new Job();j.site=l.get(0);j.type=Integer.parseInt(l.get(1));j.stage=Integer.parseInt(l.get(2));j.completedDay=Long.parseLong(l.get(3));
            if(j.type>=0&&j.type<8)jobs.put(p.getFileName().toString().replace(".txt",""),j);
        }catch(Exception ignored){}});}
    }catch(Exception e){System.err.println("A&P Craft contract load: "+e);}}
    public static void initialize(){
        ServerLifecycleEvents.SERVER_STARTED.register(s->{server=s;load();});
        ServerLifecycleEvents.SERVER_STOPPING.register(s->{for(ServerPlayerEntity p:s.getPlayerManager().getPlayerList())save(p);});
        ServerLifecycleEvents.SERVER_STOPPED.register(s->{server=null;jobs.clear();});
        CommandRegistrationCallback.EVENT.register((d,access,env)->d.register(CommandManager.literal("industry")
            .then(CommandManager.literal("accept").then(CommandManager.argument("type",IntegerArgumentType.integer(0,7))
                .executes(c->accept(c.getSource().getPlayerOrThrow(),IntegerArgumentType.getInteger(c,"type")))))
            .then(CommandManager.literal("inspect").executes(c->progress(c.getSource().getPlayerOrThrow(),1)))
            .then(CommandManager.literal("verify").executes(c->progress(c.getSource().getPlayerOrThrow(),2)))
            .then(CommandManager.literal("finish").executes(c->progress(c.getSource().getPlayerOrThrow(),3)))));
    }
    private static VillagerEntity manager(ServerPlayerEntity p,int type){
        List<VillagerEntity> nearby=p.getServerWorld().getEntitiesByClass(VillagerEntity.class,
            p.getBoundingBox().expand(7),v->v.getCommandTags().contains("APCraftIndustry:"+type));
        return nearby.isEmpty()?null:nearby.get(0);
    }
    private static void link(ServerPlayerEntity p,String name,String cmd){
        p.sendMessage(Text.literal("  ["+name+"]").setStyle(Style.EMPTY.withColor(Formatting.AQUA)
            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,cmd))),false);
    }
    public static void show(ServerPlayerEntity p,VillagerEntity v,int type){
        String site=site(v);
        p.sendMessage(Text.literal("[INDUSTRY CONTRACT] "+TITLES[type]+" | Reward $"+PAY[type]),false);
        Job j=jobs.get(key(p));
        if(j==null||j.stage==0||j.stage==4){link(p,"Accept job","/industry accept "+type);return;}
        if(j.type!=type||!j.site.equals(site)){p.sendMessage(Text.literal("You have an active job at another site."),false);return;}
        if(j.stage==1){p.sendMessage(Text.literal("TASK: "+STEPS[type]),false);link(p,"Perform inspection","/industry inspect");}
        if(j.stage==2){p.sendMessage(Text.literal("TASK: "+VERIFY[type]),false);link(p,"Verify work","/industry verify");}
        if(j.stage==3){p.sendMessage(Text.literal("TASK: Document findings and close work order."),false);link(p,"Complete work order","/industry finish");}
    }
    private static String site(VillagerEntity v){return v.getServerWorld().getRegistryKey().getValue()+":"+v.getBlockX()+":"+v.getBlockZ();}
    private static int accept(ServerPlayerEntity p,int type){
        VillagerEntity v=manager(p,type);if(v==null){p.sendMessage(Text.literal("Talk to the appropriate industry manager."),false);return 0;}
        Job old=jobs.get(key(p));long day=p.getServerWorld().getTimeOfDay()/24000;
        if(old!=null&&old.stage>0&&old.stage<4){p.sendMessage(Text.literal("Finish your current contract first."),false);return 0;}
        if(old!=null&&old.site.equals(site(v))&&old.type==type&&old.completedDay==day){
            p.sendMessage(Text.literal("This site's work order is already complete today."),false);return 0;}
        Job j=new Job();j.site=site(v);j.type=type;j.stage=1;j.completedDay=old==null?-1:old.completedDay;
        jobs.put(key(p),j);save(p);show(p,v,type);return 1;
    }
    private static int progress(ServerPlayerEntity p,int expected){
        Job j=jobs.get(key(p));if(j==null||j.stage!=expected){p.sendMessage(Text.literal("No work order at this stage."),false);return 0;}
        VillagerEntity v=manager(p,j.type);
        if(v==null||!site(v).equals(j.site)){p.sendMessage(Text.literal("Return to your job site and its manager."),false);return 0;}
        if(expected==1){
            boolean equipped=p.getInventory().contains(Items.IRON_PICKAXE.getDefaultStack()) ||
                p.getInventory().contains(Items.SHEARS.getDefaultStack()) ||
                p.getInventory().contains(Items.COMPASS.getDefaultStack());
            if(!equipped){p.sendMessage(Text.literal("Bring a basic inspection tool (iron pickaxe, shears, or compass) to continue."),false);return 0;}
        }
        if(expected==3){
            APCraft.rewardTraining(p,PAY[j.type],0);
            j.stage=4;j.completedDay=p.getServerWorld().getTimeOfDay()/24000;
            p.sendMessage(Text.literal("Work order completed. Paid $"+PAY[j.type]+". Educational scenario only."),false);
        }else{j.stage++;p.sendMessage(Text.literal("Inspection stage recorded."),false);}
        save(p);if(j.stage<4)show(p,v,j.type);return 1;
    }
}

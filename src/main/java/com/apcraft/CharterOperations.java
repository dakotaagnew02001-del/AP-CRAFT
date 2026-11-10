package com.apcraft;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import java.nio.file.*;
import java.util.*;

/** Fictional Part 135 charter management trainer. Does NOT grant real certification or maintenance authorization. */
public final class CharterOperations {
    private CharterOperations() {}
    private static final String TAG="APCraftCharterManager";
    private static MinecraftServer server;
    private static final Map<UUID,State> STATES=new HashMap<>();
    private static final class State {
        boolean owned, certificate, aircraft, maintenance, adReviewed, records, pilot, opsSpecs;
        int mechanicCount, balance, completedFlights, hoursSinceInspection;
        long lastFlight=-1;
    }
    private static State state(ServerPlayerEntity p){return STATES.computeIfAbsent(p.getUuid(),u->new State());}
    private static Path path(UUID id){return server.getSavePath(WorldSavePath.ROOT).resolve("apcraft-charter").resolve(id+".properties");}
    private static void save(UUID id,State s){if(server==null)return;try{
        Files.createDirectories(path(id).getParent());Properties p=new Properties();
        p.setProperty("owned",""+s.owned);p.setProperty("certificate",""+s.certificate);
        p.setProperty("aircraft",""+s.aircraft);p.setProperty("maintenance",""+s.maintenance);
        p.setProperty("adReviewed",""+s.adReviewed);p.setProperty("records",""+s.records);
        p.setProperty("pilot",""+s.pilot);p.setProperty("opsSpecs",""+s.opsSpecs);
        p.setProperty("mechanicCount",""+s.mechanicCount);p.setProperty("balance",""+s.balance);
        p.setProperty("completedFlights",""+s.completedFlights);p.setProperty("hoursSinceInspection",""+s.hoursSinceInspection);
        p.setProperty("lastFlight",""+s.lastFlight);
        try(var out=Files.newOutputStream(path(id))){p.store(out,"A&P Craft fictional charter simulation");}
    }catch(Exception e){System.err.println("Charter save: "+e);}}
    private static void load(){STATES.clear();if(server==null)return;Path folder=server.getSavePath(WorldSavePath.ROOT).resolve("apcraft-charter");
        if(!Files.isDirectory(folder))return;
        try(var files=Files.list(folder)){files.filter(f->f.toString().endsWith(".properties")).forEach(f->{try{
            Properties p=new Properties();try(var in=Files.newInputStream(f)){p.load(in);}
            State s=new State();s.owned=Boolean.parseBoolean(p.getProperty("owned","false"));
            s.certificate=Boolean.parseBoolean(p.getProperty("certificate","false"));
            s.aircraft=Boolean.parseBoolean(p.getProperty("aircraft","false"));
            s.maintenance=Boolean.parseBoolean(p.getProperty("maintenance","false"));
            s.adReviewed=Boolean.parseBoolean(p.getProperty("adReviewed","false"));
            s.records=Boolean.parseBoolean(p.getProperty("records","false"));
            s.pilot=Boolean.parseBoolean(p.getProperty("pilot","false"));
            s.opsSpecs=Boolean.parseBoolean(p.getProperty("opsSpecs","false"));
            s.mechanicCount=Integer.parseInt(p.getProperty("mechanicCount","0"));
            s.balance=Integer.parseInt(p.getProperty("balance","0"));
            s.completedFlights=Integer.parseInt(p.getProperty("completedFlights","0"));
            s.hoursSinceInspection=Integer.parseInt(p.getProperty("hoursSinceInspection","0"));
            s.lastFlight=Long.parseLong(p.getProperty("lastFlight","-1"));
            STATES.put(UUID.fromString(f.getFileName().toString().replace(".properties","")),s);
        }catch(Exception ignored){}});}catch(Exception e){System.err.println("Charter load: "+e);}
    }
    public static void initialize(){
        ServerLifecycleEvents.SERVER_STARTED.register(s->{server=s;load();});
        ServerLifecycleEvents.SERVER_STOPPING.register(s->{for(var e:STATES.entrySet())save(e.getKey(),e.getValue());});
        ServerLifecycleEvents.SERVER_STOPPED.register(s->{STATES.clear();server=null;});
        UseEntityCallback.EVENT.register((player,world,hand,entity,hit)->{
            if(!(entity instanceof VillagerEntity v)||!v.getCommandTags().contains(TAG))return ActionResult.PASS;
            if(!world.isClient&&player instanceof ServerPlayerEntity p)menu(p);
            return ActionResult.SUCCESS;
        });
        CommandRegistrationCallback.EVENT.register((d,a,e)->d.register(CommandManager.literal("charter")
            .then(CommandManager.literal("menu").executes(c->{menu(c.getSource().getPlayerOrThrow());return 1;}))
            .then(CommandManager.literal("action").then(CommandManager.argument("step",IntegerArgumentType.integer(1,10))
                .executes(c->action(c.getSource().getPlayerOrThrow(),IntegerArgumentType.getInteger(c,"step")))))));
    }
    private static void message(ServerPlayerEntity p,String s){p.sendMessage(Text.literal("[CHARTER] "+s),false);}
    private static void link(ServerPlayerEntity p,String label,int id){p.sendMessage(Text.literal("  ["+label+"]")
        .setStyle(Style.EMPTY.withColor(Formatting.AQUA).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/charter action "+id))),false);}
    private static boolean near(ServerPlayerEntity p){return !p.getServerWorld().getEntitiesByClass(VillagerEntity.class,
        p.getBoundingBox().expand(9),v->v.getCommandTags().contains(TAG)).isEmpty();}
    private static void menu(ServerPlayerEntity p){if(!near(p)){message(p,"Visit the charter office manager.");return;}
        State s=state(p);message(p,"CHARTER COMPANY | Owned: "+s.owned+" | Fleet: "+(s.aircraft?"1 trainer (fictional charter configuration)":"none")+
            " | Staff: "+s.mechanicCount+" | Company funds: $"+s.balance+" | Missions: "+s.completedFlights);
        message(p,"Dispatch readiness: "+(ready(s)?"READY":"GROUNDED")+" | Hours since simulated 100-hour inspection: "+s.hoursSinceInspection);
        link(p,"Purchase charter business ($12,000)",1);
        link(p,"Acquire training charter aircraft ($8,000)",2);
        link(p,"Hire mechanic ($450)",3);
        link(p,"Complete Part 119/135 certificate & OpSpecs scenario",4);
        link(p,"Review AD compliance",5);
        link(p,"Perform inspection & approve maintenance record (simulated)",6);
        link(p,"Verify records / equipment",7);
        link(p,"Assign qualified charter pilot",8);
        link(p,"Dispatch charter mission",9);
        link(p,"Collect company profits",10);
        message(p,"Reference: 14 CFR Parts 43, 91, 119, 135; §§ 135.411, 135.421. Educational simulation only.");
    }
    private static boolean ready(State s){return s.owned&&s.aircraft&&s.certificate&&s.opsSpecs&&s.maintenance&&s.adReviewed&&s.records&&s.pilot&&s.hoursSinceInspection<100;}
    private static int action(ServerPlayerEntity p,int id){if(!near(p)){message(p,"You must be at the charter office.");return 0;}
        State s=state(p);long day=p.getServerWorld().getTimeOfDay()/24000L;
        if(id==1){if(s.owned){message(p,"You already own a charter company.");return 0;}
            if(!APCraft.purchaseTrainingTool(p,12000)){message(p,"Insufficient cash ($12,000).");return 0;}s.owned=true;}
        else if(!s.owned){message(p,"Purchase the charter company first.");return 0;}
        else if(id==2){if(s.aircraft){message(p,"An aircraft is already assigned.");return 0;}
            if(!APCraft.purchaseTrainingTool(p,8000)){message(p,"Insufficient cash ($8,000).");return 0;}
            s.aircraft=true;s.maintenance=false;s.adReviewed=false;s.records=false;}
        else if(id==3){if(!APCraft.purchaseTrainingTool(p,450)){message(p,"Insufficient cash ($450).");return 0;}s.mechanicCount++;}
        else if(id==4){if(!s.aircraft){message(p,"Acquire an aircraft first.");return 0;}
            s.certificate=true;s.opsSpecs=true;message(p,"Fictional operator certification and operations specifications scenario completed. Real Part 135 certification is a separate FAA process.");}
        else if(id==5){if(!s.aircraft){message(p,"No aircraft to review.");return 0;}
            s.adReviewed=true;message(p,"Simulated applicable AD status reviewed; real AD research and compliance require aircraft-specific records.");}
        else if(id==6){if(!s.aircraft||s.mechanicCount<1){message(p,"Acquire an aircraft and hire a mechanic first.");return 0;}
            if(!s.adReviewed){message(p,"Review applicable ADs before sign-off.");return 0;}
            s.maintenance=true;s.hoursSinceInspection=0;message(p,"Simulated inspection complete; training record entered. This is not an FAA return-to-service approval.");}
        else if(id==7){if(!s.aircraft){message(p,"No aircraft to verify.");return 0;}s.records=true;
            message(p,"Simulated airworthiness, registration, equipment, and records checklist completed.");}
        else if(id==8){if(!s.certificate){message(p,"Complete operator certification scenario first.");return 0;}s.pilot=true;
            message(p,"Fictional qualified pilot assigned; actual Part 135 pilot qualification is more extensive.");}
        else if(id==9){if(!ready(s)){message(p,"Dispatch blocked: certificate, OpSpecs, aircraft, pilot, records, AD review, and maintenance must all be current.");return 0;}
            if(s.lastFlight==day){message(p,"Only one training charter mission per Minecraft day.");return 0;}
            s.lastFlight=day;s.completedFlights++;s.hoursSinceInspection+=5;s.balance+=650;
            message(p,"Charter flight completed. Company earned $650 net (fictional). +5 aircraft hours. Aircraft remains subject to inspection tracking.");}
        else if(id==10){if(s.balance<=0){message(p,"No profits to collect.");return 0;}
            APCraft.refundFleet(p,s.balance);message(p,"Collected $"+s.balance+" company profits.");s.balance=0;}
        save(p.getUuid(),s);menu(p);return 1;
    }
    public static void spawn(ServerWorld w,int x,int y,int z){
        // Independent office beyond the business broker building; avoid existing hangar footprint.
        int ox=x+62,oz=z+2;
        for(int a=0;a<13;a++)for(int b=0;b<11;b++){
            w.setBlockState(new BlockPos(ox+a,y-1,oz+b),Blocks.POLISHED_ANDESITE.getDefaultState(),3);
            for(int h=0;h<6;h++){
                boolean edge=a==0||a==12||b==0||b==10;
                boolean entrance=b==10&&a>=5&&a<=7&&h<3;
                var block=h==5?Blocks.DARK_PRISMARINE:edge&&!entrance?Blocks.WHITE_CONCRETE:Blocks.AIR;
                w.setBlockState(new BlockPos(ox+a,y+h,oz+b),block.getDefaultState(),3);
            }
        }
        w.setBlockState(new BlockPos(ox+3,y+2,oz+10),Blocks.GLASS_PANE.getDefaultState(),3);
        w.setBlockState(new BlockPos(ox+9,y+2,oz+10),Blocks.GLASS_PANE.getDefaultState(),3);
        w.setBlockState(new BlockPos(ox+4,y,oz+4),Blocks.LECTERN.getDefaultState(),3);
        w.setBlockState(new BlockPos(ox+6,y+4,oz+5),Blocks.SEA_LANTERN.getDefaultState(),3);
        if(!w.getEntitiesByClass(VillagerEntity.class,new Box(ox,y-2,oz,ox+13,y+7,oz+11),v->v.getCommandTags().contains(TAG)).isEmpty())return;
        VillagerEntity v=EntityType.VILLAGER.create(w);if(v==null)return;
        v.refreshPositionAndAngles(ox+6.5,y,oz+5.5,0,0);
        v.setCustomName(Text.literal("Part 135 Charter Operations Manager"));v.setCustomNameVisible(true);
        v.addCommandTag(TAG);v.setPersistent();w.spawnEntity(v);
    }
}

package com.apcraft;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
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
import java.nio.file.*;
import java.util.*;

/** Fictional aviation business economy. All figures are gameplay values, not real wages or FAA approvals. */
public final class AviationBusiness {
    private AviationBusiness() {}
    private static final String BROKER="APCraftBusinessBroker";
    private static final String[] SECTORS={"General Aviation FBO", "Airline MRO", "Defense Contractor", "Helicopter Services"};
    private static final int[] PRICE={3000,16000,24000,11000};
    private static final int[] GROSS={150,850,1250,550};
    private static final int[] COST={55,420,650,240};
    private static final int[] SHIFT={160,420,540,330};
    private static final class Portfolio {
        int career=-1, level=0, bank=0;
        long lastShift=-1;
        long lastAccrual=-1;
        int[] owned=new int[4];
        int[] staff=new int[4];
        int[] reputation=new int[4];
        long[] lastContract=new long[]{-1,-1,-1,-1};
    }
    private static final Map<UUID,Portfolio> DATA=new HashMap<>();
    private static MinecraftServer server;
    private static int ticks;
    private static Path folder(){return server.getSavePath(WorldSavePath.ROOT).resolve("apcraft-businesses");}
    private static Portfolio account(ServerPlayerEntity p){return DATA.computeIfAbsent(p.getUuid(),u->{Portfolio a=new Portfolio();a.lastAccrual=day(p);return a;});}
    private static long day(ServerPlayerEntity p){return p.getServerWorld().getTimeOfDay()/24000;}
    private static void save(UUID id,Portfolio a){if(server==null)return;try{
        Files.createDirectories(folder());
        Files.writeString(folder().resolve(id+".txt"),a.career+"\n"+a.level+"\n"+a.bank+"\n"+a.lastShift+"\n"+a.lastAccrual+"\n"+a.owned[0]+"\n"+a.owned[1]+"\n"+a.owned[2]+"\n"+a.owned[3]+"\n"+join(a.staff)+"\n"+join(a.reputation)+"\n"+join(a.lastContract));
    }catch(Exception e){System.err.println("A&P Craft business save: "+e);}}
    private static String join(int[] a){return a[0]+","+a[1]+","+a[2]+","+a[3];}
    private static String join(long[] a){return a[0]+","+a[1]+","+a[2]+","+a[3];}
    private static void load(){DATA.clear();try{
        if(!Files.isDirectory(folder()))return;
        try(var paths=Files.list(folder())){paths.filter(f->f.toString().endsWith(".txt")).forEach(f->{try{
            List<String> lines=Files.readAllLines(f);if(lines.size()<9)return;
            Portfolio a=new Portfolio();a.career=Integer.parseInt(lines.get(0));a.level=Integer.parseInt(lines.get(1));
            a.bank=Integer.parseInt(lines.get(2));a.lastShift=Long.parseLong(lines.get(3));a.lastAccrual=Long.parseLong(lines.get(4));
            for(int i=0;i<4;i++)a.owned[i]=Integer.parseInt(lines.get(5+i));
            if(lines.size()>=12){String[] st=lines.get(9).split(","),rp=lines.get(10).split(","),lc=lines.get(11).split(",");
                for(int i=0;i<4;i++){a.staff[i]=Integer.parseInt(st[i]);a.reputation[i]=Integer.parseInt(rp[i]);a.lastContract[i]=Long.parseLong(lc[i]);}}
            DATA.put(UUID.fromString(f.getFileName().toString().replace(".txt","")),a);
        }catch(Exception ignored){}});}
    }catch(Exception e){System.err.println("A&P Craft business load: "+e);}}
    public static void initialize(){
        ServerLifecycleEvents.SERVER_STARTED.register(s->{server=s;load();});
        ServerLifecycleEvents.SERVER_STOPPING.register(s->{for(var e:DATA.entrySet())save(e.getKey(),e.getValue());});
        ServerLifecycleEvents.SERVER_STOPPED.register(s->{server=null;DATA.clear();});
        ServerTickEvents.END_SERVER_TICK.register(s->{if(++ticks%1200!=0)return;
            for(ServerPlayerEntity p:s.getPlayerManager().getPlayerList())accrue(p);
        });
        UseEntityCallback.EVENT.register((player,world,hand,entity,hit)->{
            if(!(entity instanceof VillagerEntity v)||!v.getCommandTags().contains(BROKER))return ActionResult.PASS;
            if(!world.isClient&&player instanceof ServerPlayerEntity p)menu(p);
            return ActionResult.SUCCESS;
        });
        CommandRegistrationCallback.EVENT.register((d,access,env)->d.register(CommandManager.literal("aviationbusiness")
            .then(CommandManager.literal("menu").executes(c->{menu(c.getSource().getPlayerOrThrow());return 1;}))
            .then(CommandManager.literal("career").then(CommandManager.argument("sector",IntegerArgumentType.integer(1,4))
                .executes(c->career(c.getSource().getPlayerOrThrow(),IntegerArgumentType.getInteger(c,"sector")-1))))
            .then(CommandManager.literal("shift").executes(c->shift(c.getSource().getPlayerOrThrow())))
            .then(CommandManager.literal("buy").then(CommandManager.argument("sector",IntegerArgumentType.integer(1,4))
                .executes(c->buy(c.getSource().getPlayerOrThrow(),IntegerArgumentType.getInteger(c,"sector")-1))))
            .then(CommandManager.literal("hire").then(CommandManager.argument("sector",IntegerArgumentType.integer(1,4))
                .executes(c->hire(c.getSource().getPlayerOrThrow(),IntegerArgumentType.getInteger(c,"sector")-1))))
            .then(CommandManager.literal("contract").then(CommandManager.argument("sector",IntegerArgumentType.integer(1,4))
                .executes(c->contract(c.getSource().getPlayerOrThrow(),IntegerArgumentType.getInteger(c,"sector")-1))))
            .then(CommandManager.literal("collect").executes(c->collect(c.getSource().getPlayerOrThrow())))
            .then(CommandManager.literal("upgrade").then(CommandManager.argument("sector",IntegerArgumentType.integer(1,4))
                .executes(c->upgrade(c.getSource().getPlayerOrThrow(),IntegerArgumentType.getInteger(c,"sector")-1))))));
    }
    public static void spawnAtAirfield(ServerWorld w,int x,int y,int z){
        if(!w.getEntitiesByClass(VillagerEntity.class,new net.minecraft.util.math.Box(x+17,y-2,z+7,x+23,y+4,z+15),v->v.getCommandTags().contains(BROKER)).isEmpty())return;
        VillagerEntity v=EntityType.VILLAGER.create(w);if(v==null)return;
        v.refreshPositionAndAngles(x+19.5,y,z+11.5,0,0);
        v.setCustomName(Text.literal("Aviation Career & Business Broker"));v.setCustomNameVisible(true);
        v.addCommandTag(BROKER);v.setPersistent();w.spawnEntity(v);
    }
    private static boolean nearby(ServerPlayerEntity p){return !p.getServerWorld().getEntitiesByClass(VillagerEntity.class,
        p.getBoundingBox().expand(9),v->v.getCommandTags().contains(BROKER)).isEmpty();}
    private static void msg(ServerPlayerEntity p,String m){p.sendMessage(Text.literal("[AVIATION BUSINESS] "+m),false);}
    private static void link(ServerPlayerEntity p,String label,String cmd){p.sendMessage(Text.literal("  ["+label+"]")
        .setStyle(Style.EMPTY.withColor(Formatting.AQUA).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,cmd))),false);}
    private static void accrue(ServerPlayerEntity p){
        Portfolio a=account(p);long now=day(p);
        if(a.lastAccrual<0||now<a.lastAccrual){a.lastAccrual=now;save(p.getUuid(),a);return;}
        long elapsed=Math.min(7,now-a.lastAccrual);if(elapsed<=0)return;
        int net=0;
        for(int i=0;i<4;i++){
            // Each additional location earns less as the local market saturates.
            for(int n=0;n<a.owned[i];n++)net+=Math.max(0,(GROSS[i]*(100-15*n)/100)-COST[i]);
            // Employees increase throughput, but daily wages must be paid.
            net+=a.staff[i]*(95+Math.min(60,a.reputation[i]*3)-65);
        }
        a.bank+=net*(int)elapsed;a.lastAccrual=now;save(p.getUuid(),a);
        if(net>0)msg(p,"Your businesses earned $"+(net*elapsed)+" net over "+elapsed+" Minecraft day(s). Collect from the broker.");
    }
    private static void menu(ServerPlayerEntity p){if(!nearby(p)){msg(p,"Visit the business broker at the starter hangar.");return;}
        accrue(p);Portfolio a=account(p);
        msg(p,"CAREERS & OWNERSHIP | Business earnings ready: $"+a.bank+" | Career: "+(a.career<0?"Unemployed":SECTORS[a.career]));
        for(int i=0;i<4;i++){
            msg(p,SECTORS[i]+" | Buy $"+PRICE[i]+" | Owned "+a.owned[i]+" | Daily gross $"+GROSS[i]+" / operating cost $"+COST[i]);
            link(p,"Work in "+SECTORS[i],"/aviationbusiness career "+(i+1));
            link(p,"Purchase "+SECTORS[i],"/aviationbusiness buy "+(i+1));
            if(a.owned[i]>0)link(p,"Expand "+SECTORS[i]+" ($"+(PRICE[i]*(a.owned[i]+1))+")","/aviationbusiness upgrade "+(i+1));
        }
        for(int i=0;i<4;i++)if(a.owned[i]>0){
            msg(p,SECTORS[i]+" | NPC mechanics: "+a.staff[i]+" | Reputation: "+a.reputation[i]);
            link(p,"Hire mechanic ($450)","/aviationbusiness hire "+(i+1));
            link(p,"Bid on maintenance contract","/aviationbusiness contract "+(i+1));
        }
        link(p,"Complete a work shift","/aviationbusiness shift");
        link(p,"Collect net business income","/aviationbusiness collect");
    }
    private static int career(ServerPlayerEntity p,int i){if(!nearby(p))return 0;
        Portfolio a=account(p);a.career=i;save(p.getUuid(),a);msg(p,"Hired for "+SECTORS[i]+". Work shifts at the broker for gameplay wages.");return 1;}
    private static int shift(ServerPlayerEntity p){if(!nearby(p))return 0;
        Portfolio a=account(p);long today=day(p);
        if(a.career<0){msg(p,"Choose an employer first.");return 0;}
        if(a.lastShift==today){msg(p,"You already worked a shift this Minecraft day.");return 0;}
        a.lastShift=today;APCraft.rewardTraining(p,SHIFT[a.career],0);save(p.getUuid(),a);
        msg(p,"Completed a fictional "+SECTORS[a.career]+" shift. Paid $"+SHIFT[a.career]+".");return 1;}
    private static int buy(ServerPlayerEntity p,int i){if(!nearby(p))return 0;
        Portfolio a=account(p);if(a.owned[i]>0){msg(p,"You already own this sector. Choose Expand instead.");return 0;}
        if(!APCraft.purchaseTrainingTool(p,PRICE[i])){msg(p,"Insufficient cash for startup.");return 0;}
        accrue(p);a.owned[i]=1;save(p.getUuid(),a);
        msg(p,"Opened "+SECTORS[i]+". Earnings accrue per Minecraft day while you play, net of operating costs.");return 1;}
    private static int upgrade(ServerPlayerEntity p,int i){if(!nearby(p))return 0;
        Portfolio a=account(p);if(a.owned[i]<1||a.owned[i]>=4){msg(p,"Own a business first; maximum four sites per sector.");return 0;}
        int price=PRICE[i]*(a.owned[i]+1);if(!APCraft.purchaseTrainingTool(p,price)){msg(p,"Insufficient cash for expansion.");return 0;}
        accrue(p);a.owned[i]++;save(p.getUuid(),a);msg(p,"Expanded "+SECTORS[i]+" to "+a.owned[i]+" sites.");return 1;}
    private static int hire(ServerPlayerEntity p,int i){
        if(!nearby(p))return 0;Portfolio a=account(p);accrue(p);
        if(a.owned[i]==0){msg(p,"Purchase this business before hiring mechanics.");return 0;}
        if(a.staff[i]>=a.owned[i]*3){msg(p,"Your facilities are at staff capacity. Expand first.");return 0;}
        if(!APCraft.purchaseTrainingTool(p,450)){msg(p,"Hiring requires $450 cash.");return 0;}
        a.staff[i]++;save(p.getUuid(),a);msg(p,"Hired an NPC mechanic for "+SECTORS[i]+". Daily wages are deducted from revenue.");return 1;
    }
    private static int contract(ServerPlayerEntity p,int i){
        if(!nearby(p))return 0;Portfolio a=account(p);accrue(p);long today=day(p);
        if(a.owned[i]==0||a.staff[i]==0){msg(p,"Own a facility and hire at least one mechanic to bid.");return 0;}
        if(a.lastContract[i]==today){msg(p,"You already completed a contract in this sector today.");return 0;}
        // A predictable daily bid outcome; no random rewards or hidden gambling.
        int score=a.staff[i]*15+a.reputation[i]*5+a.owned[i]*10;
        int threshold=25+i*7;
        a.lastContract[i]=today;
        if(score<threshold){a.reputation[i]=Math.max(0,a.reputation[i]-1);save(p.getUuid(),a);
            msg(p,"Bid not awarded. Improve staff capacity and reputation before bidding again tomorrow.");return 0;}
        int payout=180+i*160+a.reputation[i]*12;
        a.reputation[i]=Math.min(20,a.reputation[i]+1);
        a.bank+=payout;save(p.getUuid(),a);
        msg(p,"Contract awarded and completed by your staff. $"+payout+" deposited in business earnings. Reputation increased.");return 1;
    }
    private static int collect(ServerPlayerEntity p){if(!nearby(p))return 0;
        accrue(p);Portfolio a=account(p);if(a.bank<=0){msg(p,"No earnings to collect yet.");return 0;}
        int amount=a.bank;a.bank=0;APCraft.refundFleet(p,amount);save(p.getUuid(),a);
        msg(p,"Transferred $"+amount+" net profit to your cash balance.");return 1;}
}

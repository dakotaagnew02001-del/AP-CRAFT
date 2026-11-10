package com.apcraft;

import com.google.gson.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.village.VillagerProfession;
import net.minecraft.village.VillagerData;
import net.minecraft.village.VillagerType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import net.minecraft.text.ClickEvent;
import net.minecraft.util.Formatting;
import net.minecraft.util.ActionResult;
import net.minecraft.util.WorldSavePath;
import java.nio.file.*;
import java.util.*;

/** Fictional closed-loop economy. Shares are not connected to real markets or real money. */
public final class VillagerBank {
    private VillagerBank() {}
    private static final String TAG="apcraft_banker";
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, Account> ACCOUNTS=new HashMap<>();
    private static MinecraftServer server;
    private static class Account { int savings=0; int shares=0; }
    private static Account account(ServerPlayerEntity p){return ACCOUNTS.computeIfAbsent(p.getUuid(),k->new Account());}
    private static Path path(){return server.getSavePath(WorldSavePath.ROOT).resolve("apcraft-bank.json");}
    private static void save(){if(server==null)return;try{
        JsonObject root=new JsonObject();
        for(var e:ACCOUNTS.entrySet())root.add(e.getKey().toString(),GSON.toJsonTree(e.getValue()));
        Files.writeString(path(),GSON.toJson(root));
    }catch(Exception e){System.err.println("A&P Craft bank save failed: "+e);}}
    private static void load(){ACCOUNTS.clear();try{if(!Files.exists(path()))return;
        JsonObject root=JsonParser.parseString(Files.readString(path())).getAsJsonObject();
        for(var e:root.entrySet())ACCOUNTS.put(UUID.fromString(e.getKey()),GSON.fromJson(e.getValue(),Account.class));
    }catch(Exception e){System.err.println("A&P Craft bank load failed: "+e);}}
    // Reproducible world-day price. No random redraws on each menu opening.
    private static int price(){long day=server==null?0:server.getOverworld().getTimeOfDay()/24000L;
        long cycle=Math.floorMod(day,20);return 80+(int)(cycle<=10?cycle*4:(20-cycle)*4);}
    private static Text action(String label,String command){return Text.literal(label).styled(s->s.withColor(Formatting.AQUA)
        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,command)));}
    private static void tell(ServerPlayerEntity p,String s){p.sendMessage(Text.literal("[VILLAGER BANK] "+s),false);}
    private static void menu(ServerPlayerEntity p){Account a=account(p);int price=price();
        tell(p,"Wallet $"+APCraft.currencyBalance(p)+" | Savings $"+a.savings+" | Village Aviation Co. "+a.shares+" shares @ $"+price+" | Portfolio $"+((long)a.shares*price));
        tell(p,"Fictional stock market: prices follow a 20-day repeating game cycle. Values can fall. No real-money trading.");
        p.sendMessage(action("[DEPOSIT $100]","/apbank deposit"),false);
        p.sendMessage(action("[WITHDRAW $100]","/apbank withdraw"),false);
        p.sendMessage(action("[BUY 1 SHARE]","/apbank buy"),false);
        p.sendMessage(action("[SELL 1 SHARE]","/apbank sell"),false);
        p.sendMessage(action("[REFRESH ACCOUNT]","/apbank view"),false);
    }
    private static void transaction(ServerPlayerEntity p,String type){Account a=account(p);
        switch(type){
            case "deposit" -> {if(!APCraft.spendCurrency(p,100)){tell(p,"Need $100 in wallet.");return;}a.savings+=100;}
            case "withdraw" -> {if(a.savings<100){tell(p,"Need $100 in savings.");return;}a.savings-=100;APCraft.awardCurrency(p,100);}
            case "buy" -> {int cost=price();if(!APCraft.spendCurrency(p,cost)){tell(p,"Need $"+cost+" in wallet.");return;}a.shares++;}
            case "sell" -> {if(a.shares<1){tell(p,"No shares to sell.");return;}a.shares--;APCraft.awardCurrency(p,price());}
            default -> {return;}
        }
        save();menu(p);
    }
    public static void initialize(){
        ServerLifecycleEvents.SERVER_STARTED.register(s->{server=s;load();});
        ServerLifecycleEvents.SERVER_STOPPING.register(s->save());
        ServerLifecycleEvents.SERVER_STOPPED.register(s->{server=null;ACCOUNTS.clear();});
        ServerPlayConnectionEvents.JOIN.register((handler,sender,s)->s.execute(()->{
            ServerPlayerEntity p=handler.player;ServerWorld world=p.getServerWorld();
            if(!world.getEntitiesByClass(VillagerEntity.class,p.getBoundingBox().expand(80),v->v.getCommandTags().contains(TAG)).isEmpty())return;
            VillagerEntity banker=EntityType.VILLAGER.create(world);if(banker==null)return;
            banker.refreshPositionAndAngles(p.getX()+9,p.getY(),p.getZ()+5,0,0);
            banker.setVillagerData(new VillagerData(VillagerType.PLAINS,VillagerProfession.LIBRARIAN,2));
            banker.setCustomName(Text.literal("Village Banker"));banker.setCustomNameVisible(true);
            banker.setPersistent();banker.addCommandTag(TAG);world.spawnEntity(banker);
        }));
        UseEntityCallback.EVENT.register((player,world,hand,entity,hit)->{
            if(world.isClient()||!(player instanceof ServerPlayerEntity p)||!entity.getCommandTags().contains(TAG))return ActionResult.PASS;
            menu(p);return ActionResult.SUCCESS;
        });
        CommandRegistrationCallback.EVENT.register((dispatcher,registryAccess,environment)->dispatcher.register(CommandManager.literal("apbank")
            .then(CommandManager.literal("view").executes(c->{menu(c.getSource().getPlayerOrThrow());return 1;}))
            .then(CommandManager.literal("deposit").executes(c->{transaction(c.getSource().getPlayerOrThrow(),"deposit");return 1;}))
            .then(CommandManager.literal("withdraw").executes(c->{transaction(c.getSource().getPlayerOrThrow(),"withdraw");return 1;}))
            .then(CommandManager.literal("buy").executes(c->{transaction(c.getSource().getPlayerOrThrow(),"buy");return 1;}))
            .then(CommandManager.literal("sell").executes(c->{transaction(c.getSource().getPlayerOrThrow(),"sell");return 1;}))));
    }
}

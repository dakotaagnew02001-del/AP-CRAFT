package com.apcraft;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.village.VillagerProfession;
import net.minecraft.village.VillagerData;
import net.minecraft.village.VillagerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.block.Blocks;
import net.minecraft.text.Text;
import net.minecraft.text.ClickEvent;
import net.minecraft.util.Formatting;
import net.minecraft.util.ActionResult;

/** Grocery vendor and ingredient-based meals. All prices are fictional in-game currency. */
public final class HomeCooking {
    private HomeCooking() {}
    private static final String GROCER = "apcraft_grocer";
    private static final Item[] GROCER_ITEMS = {Items.BEEF, Items.POTATO, Items.CARROT, Items.BREAD, Items.EGG, Items.WHEAT};
    private static final int[] GROCER_COSTS = {24, 8, 7, 12, 9, 6};
    private static final String[] GROCER_NAMES = {"Raw Beef", "Potato", "Carrot", "Bread", "Egg", "Wheat"};
    private static void say(ServerPlayerEntity p, String msg) {p.sendMessage(Text.literal("[KITCHEN] " + msg), false);}
    private static Text link(String title, String command) {
        return Text.literal(title).styled(s -> s.withColor(Formatting.AQUA).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
    }
    private static void groceryMenu(ServerPlayerEntity p) {
        say(p, "Village Grocery | Wallet $" + APCraft.currencyBalance(p) + " | Click an item to buy one.");
        for(int i=0;i<GROCER_ITEMS.length;i++) p.sendMessage(link("[BUY " + GROCER_NAMES[i] + " $" + GROCER_COSTS[i] + "]", "/apgrocer buy " + (i+1)), false);
        say(p, "Cook meals by right-clicking a smoker with an empty hand. Recipes require ingredients in your inventory.");
    }
    private static void buy(ServerPlayerEntity p, int id) {
        int i=id-1;
        if(i<0 || i>=GROCER_ITEMS.length)return;
        if(p.getInventory().getEmptySlot()<0){say(p,"Make room in your inventory before shopping.");return;}
        if(!APCraft.spendCurrency(p,GROCER_COSTS[i])){say(p,"Insufficient funds.");return;}
        p.getInventory().insertStack(new ItemStack(GROCER_ITEMS[i]));
        say(p,"Purchased " + GROCER_NAMES[i] + " for $" + GROCER_COSTS[i] + ".");
        groceryMenu(p);
    }
    private static int count(ServerPlayerEntity p, Item item){int n=0;for(int i=0;i<p.getInventory().size();i++){ItemStack s=p.getInventory().getStack(i);if(s.isOf(item))n+=s.getCount();}return n;}
    private static void take(ServerPlayerEntity p, Item item,int amount){
        for(int i=0;i<p.getInventory().size()&&amount>0;i++){
            ItemStack s=p.getInventory().getStack(i);
            if(s.isOf(item)){int n=Math.min(amount,s.getCount());s.decrement(n);amount-=n;}
        }
    }
    private static void cook(ServerPlayerEntity p,int recipe){
        Item[] ingredients;int[] amounts;Item result;String name;
        switch(recipe){
            case 1 -> {ingredients=new Item[]{Items.BEEF,Items.POTATO,Items.CARROT};amounts=new int[]{1,1,1};result=Items.COOKED_BEEF;name="Steak Dinner";}
            case 2 -> {ingredients=new Item[]{Items.EGG,Items.BREAD};amounts=new int[]{1,1};result=Items.PUMPKIN_PIE;name="Breakfast Plate";}
            case 3 -> {ingredients=new Item[]{Items.WHEAT,Items.CARROT,Items.POTATO};amounts=new int[]{2,1,1};result=Items.RABBIT_STEW;name="Vegetable Stew";}
            default -> {return;}
        }
        for(int i=0;i<ingredients.length;i++)if(count(p,ingredients[i])<amounts[i]){say(p,"Missing ingredients for " + name + ". Visit the grocery villager.");return;}
        if(p.getInventory().getEmptySlot()<0){say(p,"Free an inventory slot before cooking.");return;}
        for(int i=0;i<ingredients.length;i++)take(p,ingredients[i],amounts[i]);
        ItemStack meal=new ItemStack(result);
        meal.setCustomName(Text.literal(name));
        p.getInventory().insertStack(meal);
        say(p,"Prepared " + name + "! Ingredients consumed; meal added to inventory.");
    }
    private static void kitchenMenu(ServerPlayerEntity p){
        say(p,"Kitchen recipes (ingredients are consumed when you prepare a meal):");
        p.sendMessage(link("[COOK STEAK DINNER: beef + potato + carrot]","/apcook make 1"),false);
        p.sendMessage(link("[COOK BREAKFAST: egg + bread]","/apcook make 2"),false);
        p.sendMessage(link("[COOK VEGETABLE STEW: 2 wheat + carrot + potato]","/apcook make 3"),false);
    }
    public static void initialize(){
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->server.execute(()->{
            ServerPlayerEntity p=handler.player;ServerWorld world=p.getServerWorld();
            if(!world.getEntitiesByClass(VillagerEntity.class,p.getBoundingBox().expand(90),v->v.getCommandTags().contains(GROCER)).isEmpty())return;
            VillagerEntity v=EntityType.VILLAGER.create(world);if(v==null)return;
            v.refreshPositionAndAngles(p.getX()+13,p.getY(),p.getZ()+7,0,0);
            v.setVillagerData(new VillagerData(VillagerType.PLAINS,VillagerProfession.FARMER,2));
            v.setCustomName(Text.literal("Village Grocer"));v.setCustomNameVisible(true);v.setPersistent();v.addCommandTag(GROCER);world.spawnEntity(v);
        }));
        UseEntityCallback.EVENT.register((player,world,hand,entity,hit)->{
            if(world.isClient()||!(player instanceof ServerPlayerEntity p)||!entity.getCommandTags().contains(GROCER))return ActionResult.PASS;
            groceryMenu(p);return ActionResult.SUCCESS;
        });
        UseBlockCallback.EVENT.register((player,world,hand,hit)->{
            if(world.isClient()||!(player instanceof ServerPlayerEntity p)||world.getBlockState(hit.getBlockPos()).getBlock()!=Blocks.SMOKER||!p.getStackInHand(hand).isEmpty())return ActionResult.PASS;
            kitchenMenu(p);return ActionResult.SUCCESS;
        });
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->{
            dispatcher.register(CommandManager.literal("apgrocer").then(CommandManager.literal("buy").then(CommandManager.argument("item",com.mojang.brigadier.arguments.IntegerArgumentType.integer(1,6)).executes(c->{buy(c.getSource().getPlayerOrThrow(),com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"item"));return 1;}))));
            dispatcher.register(CommandManager.literal("apcook").then(CommandManager.literal("make").then(CommandManager.argument("recipe",com.mojang.brigadier.arguments.IntegerArgumentType.integer(1,3)).executes(c->{cook(c.getSource().getPlayerOrThrow(),com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"recipe"));return 1;}))));
        });
    }
}

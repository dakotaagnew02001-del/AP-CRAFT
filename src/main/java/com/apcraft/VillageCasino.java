package com.apcraft;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.VillagerProfession;
import net.minecraft.village.VillagerData;
import net.minecraft.village.VillagerType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.Formatting;
import java.util.concurrent.ThreadLocalRandom;

/** Fictional, in-game-currency-only villager casino. No real-money purchases or exchange. */
public final class VillageCasino {
    private VillageCasino() {}
    private static final String TAG = "apcraft_casino_host";
    private static final String TICKET_TAG = "apcraft_scratch_ticket";
    private static void tell(ServerPlayerEntity p,String message) { p.sendMessage(Text.literal("[VILLAGER CASINO] "+message),false); }
    private static Text option(String title,String command) {
        return Text.literal(title).styled(s->s.withColor(Formatting.GOLD)
            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,command)));
    }
    private static void menu(ServerPlayerEntity p) {
        tell(p,"Welcome! Fictional chips use your A&P Craft cash. Balance: $"+APCraft.currencyBalance(p));
        p.sendMessage(option("[BUY SCRATCH TICKET — $25]","/casino scratch"),false);
        p.sendMessage(option("[PLAY VILLAGER SLOTS — $20]","/casino slots"),false);
        p.sendMessage(option("[CHECK BALANCE]","/casino balance"),false);
        tell(p,"Scratch tickets are paper items: hold one and right-click to reveal your prize. No real-money wagering.");
    }
    private static void scratch(ServerPlayerEntity p) {
        if (!APCraft.spendCurrency(p,25)) { tell(p,"You need $25 for a ticket.");return; }
        ItemStack ticket=new ItemStack(Items.PAPER);
        NbtCompound tag=ticket.getOrCreateNbt();tag.putBoolean(TICKET_TAG,true);
        ticket.setCustomName(Text.literal("Villager Lucky Scratch Ticket"));
        if (!p.getInventory().insertStack(ticket)) p.dropItem(ticket,false);
        tell(p,"Purchased a $25 scratch ticket. Hold it and right-click to scratch!");
    }
    private static void reveal(ServerPlayerEntity p,ItemStack stack) {
        int roll=ThreadLocalRandom.current().nextInt(1000);
        int prize=roll<2?1000:roll<20?200:roll<100?50:roll<350?25:0;
        stack.decrement(1);
        if(prize>0) APCraft.awardCurrency(p,prize);
        tell(p,prize>0?"You won $"+prize+"! Balance: $"+APCraft.currencyBalance(p):"Not a winner this time.");
    }
    private static void slots(ServerPlayerEntity p) {
        if(!APCraft.spendCurrency(p,20)) { tell(p,"You need $20 to play.");return; }
        String[] icons={"EMERALD","DIAMOND","GOLD","IRON","COAL"};
        int a=ThreadLocalRandom.current().nextInt(icons.length), b=ThreadLocalRandom.current().nextInt(icons.length), c=ThreadLocalRandom.current().nextInt(icons.length);
        int prize=(a==b&&b==c)?(a==1?250:100):(a==b||b==c||a==c)?15:0;
        if(prize>0) APCraft.awardCurrency(p,prize);
        tell(p,"["+icons[a]+"] ["+icons[b]+"] ["+icons[c]+"] — "+(prize>0?"Prize $"+prize:"No prize")+". Cash $"+APCraft.currencyBalance(p));
        p.sendMessage(option("[PLAY AGAIN — $20]","/casino slots"),false);
    }
    public static void initialize() {
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->server.execute(()->{
            ServerPlayerEntity p=handler.player;
            ServerWorld world=p.getServerWorld();
            if(!world.getEntitiesByClass(VillagerEntity.class,p.getBoundingBox().expand(80),
                v->v.getCommandTags().contains(TAG)).isEmpty()) return;
            // Temporary onboarding NPC; replace with a naturally generated casino building.
            VillagerEntity host=EntityType.VILLAGER.create(world);
            if(host==null)return;
            host.refreshPositionAndAngles(p.getX()+6,p.getY(),p.getZ()+4,0,0);
            host.setVillagerData(new VillagerData(VillagerType.PLAINS,VillagerProfession.CLERIC,2));
            host.setCustomName(Text.literal("Lucky Villager — Casino Host"));
            host.setCustomNameVisible(true);host.setPersistent();host.addCommandTag(TAG);world.spawnEntity(host);
        }));
        UseEntityCallback.EVENT.register((player,world,hand,entity,hit)->{
            if(world.isClient() || !(player instanceof ServerPlayerEntity p) || !entity.getCommandTags().contains(TAG)) return ActionResult.PASS;
            menu(p);return ActionResult.SUCCESS;
        });
        UseItemCallback.EVENT.register((player,world,hand)->{
            ItemStack stack=player.getStackInHand(hand);
            if(world.isClient() || !(player instanceof ServerPlayerEntity p) || !stack.isOf(Items.PAPER)
                || !stack.hasNbt() || !stack.getNbt().getBoolean(TICKET_TAG)) return TypedActionResult.pass(stack);
            reveal(p,stack);return TypedActionResult.success(stack);
        });
        // Internal handlers for clickable text; players do not have to type commands.
        CommandRegistrationCallback.EVENT.register((dispatcher,registryAccess,environment)->{
            dispatcher.register(CommandManager.literal("casino")
                .then(CommandManager.literal("balance").executes(ctx->{ServerPlayerEntity p=ctx.getSource().getPlayerOrThrow();menu(p);return 1;}))
                .then(CommandManager.literal("scratch").executes(ctx->{scratch(ctx.getSource().getPlayerOrThrow());return 1;}))
                .then(CommandManager.literal("slots").executes(ctx->{slots(ctx.getSource().getPlayerOrThrow());return 1;})));
        });
    }
}

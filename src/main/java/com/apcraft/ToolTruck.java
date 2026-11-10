package com.apcraft;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import java.util.List;

/** In-world shop, fictional prices, and branded storage chests. */
public final class ToolTruck {
    private ToolTruck() {}
    private static final String[] BRANDS = {"pittsburgh", "icon", "mac", "snapon"};
    private static final String[] TYPES = {"wrench", "multimeter", "torque_wrench", "inspection_kit"};
    private static final int[] TOOL_PRICES = {90, 260, 550, 800};
    private static final int[] BOX_PRICES = {160, 400, 850, 1400};
    private static final Item[] BOXES = {Items.BROWN_SHULKER_BOX, Items.BLUE_SHULKER_BOX, Items.RED_SHULKER_BOX, Items.GRAY_SHULKER_BOX};
    private static final String TAG = "APCraftToolTruck";

    public static void initialize() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!(entity instanceof VillagerEntity villager) || !villager.getCommandTags().contains(TAG)) return ActionResult.PASS;
            if (world.isClient) return ActionResult.SUCCESS;
            if (player instanceof ServerPlayerEntity p) menu(p);
            return ActionResult.SUCCESS;
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> register(dispatcher));
    }
    private static void say(ServerPlayerEntity p, String message) { p.sendMessage(Text.literal("[TOOL TRUCK] " + message), false); }
    private static void link(ServerPlayerEntity p, String label, String command) {
        p.sendMessage(Text.literal(label + "  [BUY]").setStyle(Style.EMPTY.withColor(Formatting.AQUA)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))), false);
    }
    private static boolean nearby(ServerPlayerEntity p) {
        return p.getServerWorld().getEntitiesByClass(VillagerEntity.class,
                p.getBoundingBox().expand(7), v -> v.getCommandTags().contains(TAG)).size() > 0;
    }
    private static void menu(ServerPlayerEntity p) {
        say(p, "Welcome! Right-click the dealer to shop. Purchases require being within 7 blocks.");
        for (int b=0;b<4;b++) {
            say(p, (b+1) + ") " + BRANDS[b] + " | tools $" + TOOL_PRICES[b] + " | toolbox $" + BOX_PRICES[b]);
            link(p, "   " + BRANDS[b] + " toolbox ($" + BOX_PRICES[b] + ")", "/tooltruck box " + (b+1));
        }
        say(p, "Tool categories: 1 Wrench, 2 Multimeter, 3 Torque Wrench, 4 Inspection Kit");
        say(p, "Use /tooltruck catalog <brand 1-4> for clickable tools.");
    }
    private static void catalog(ServerPlayerEntity p, int brand) {
        if (!nearby(p)) { say(p, "Visit the tool-truck villager to shop."); return; }
        int b=brand-1;
        for (int t=0;t<4;t++) link(p, BRANDS[b] + " " + TYPES[t] + " ($" + TOOL_PRICES[b] + ")", "/tooltruck buy " + brand + " " + (t+1));
    }
    private static int purchase(ServerPlayerEntity p, int brand, int type, boolean box) {
        if (!nearby(p)) { say(p, "Stand near the tool-truck villager to buy."); return 0; }
        int b=brand-1;
        ItemStack stack;
        int price=box ? BOX_PRICES[b] : TOOL_PRICES[b];
        if (box) {
            stack = new ItemStack(BOXES[b]);
            stack.setCustomName(Text.literal(BRANDS[b].toUpperCase() + " Mechanic Toolbox"));
        } else {
            Item item=Registries.ITEM.get(new Identifier("apcraft", BRANDS[b] + "_" + TYPES[type-1]));
            if (item == Items.AIR) { say(p, "Tool not registered."); return 0; }
            stack=new ItemStack(item);
        }
        if (p.getInventory().getEmptySlot() < 0) { say(p, "Inventory full. Clear a slot before purchasing."); return 0; }
        if (!APCraft.purchaseTrainingTool(p,price)) { say(p, "Insufficient cash. Price $" + price); return 0; }
        p.getInventory().insertStack(stack);
        say(p, "Purchased " + stack.getName().getString() + " for $" + price + ". /economy balance");
        return 1;
    }
    private static int spawn(ServerPlayerEntity p) {
        ServerWorld w=p.getServerWorld();
        BlockPos pos=p.getBlockPos().offset(p.getHorizontalFacing(), 5);
        // Compact roadside display/truck stand, built from vanilla blocks. No terrain clearing.
        for(int x=-2;x<=2;x++) for(int z=-1;z<=1;z++) {
            BlockPos at=pos.add(x,-1,z);
            if(w.getBlockState(at).isAir()) w.setBlockState(at, Blocks.GRAY_CONCRETE.getDefaultState());
        }
        for(int x=-2;x<=2;x++) w.setBlockState(pos.add(x,2,1),Blocks.RED_CONCRETE.getDefaultState());
        w.setBlockState(pos.add(-2,0,1),Blocks.RED_CONCRETE.getDefaultState());
        w.setBlockState(pos.add(2,0,1),Blocks.RED_CONCRETE.getDefaultState());
        VillagerEntity dealer=EntityType.VILLAGER.create(w);
        if(dealer==null) return 0;
        dealer.refreshPositionAndAngles(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5,0,0);
        dealer.setCustomName(Text.literal("A&P Tool Truck Dealer"));
        dealer.setCustomNameVisible(true);
        dealer.addCommandTag(TAG);
        dealer.setPersistent();
        w.spawnEntity(dealer);
        say(p,"Tool truck dealer spawned. Right-click the villager for catalog and prices.");
        return 1;
    }
    private static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(CommandManager.literal("tooltruck")
            .then(CommandManager.literal("spawn").requires(s->s.hasPermissionLevel(2)).executes(c->spawn(c.getSource().getPlayerOrThrow())))
            .then(CommandManager.literal("shop").executes(c->{menu(c.getSource().getPlayerOrThrow());return 1;}))
            .then(CommandManager.literal("catalog").then(CommandManager.argument("brand",IntegerArgumentType.integer(1,4))
                .executes(c->{catalog(c.getSource().getPlayerOrThrow(),IntegerArgumentType.getInteger(c,"brand"));return 1;})))
            .then(CommandManager.literal("box").then(CommandManager.argument("brand",IntegerArgumentType.integer(1,4))
                .executes(c->purchase(c.getSource().getPlayerOrThrow(),IntegerArgumentType.getInteger(c,"brand"),1,true))))
            .then(CommandManager.literal("buy").then(CommandManager.argument("brand",IntegerArgumentType.integer(1,4))
                .then(CommandManager.argument("type",IntegerArgumentType.integer(1,4))
                .executes(c->purchase(c.getSource().getPlayerOrThrow(),IntegerArgumentType.getInteger(c,"brand"),IntegerArgumentType.getInteger(c,"type"),false))))));
    }
}

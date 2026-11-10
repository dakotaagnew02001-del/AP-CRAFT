package com.apcraft;

import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Portable storage. The backing inventory is serialized into the bag ItemStack's NBT. */
public final class CarryBags {
    private CarryBags() {}
    public static final Item WORK_BAG = register("work_bag", 9);
    public static final Item TOOL_BACKPACK = register("tool_backpack", 18);
    public static final Item EXPEDITION_BACKPACK = register("expedition_backpack", 27);
    public static final Item MECHANIC_TOOL_ROLL = register("mechanic_tool_roll", 9);
    public static final Item FIELD_SERVICE_CASE = register("field_service_case", 27);

    private static Item register(String id, int capacity) {
        Item item = Registry.register(Registries.ITEM, new Identifier("apcraft", id), new BagItem(capacity));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(item));
        return item;
    }
    public static void initialize() { /* class initialization registers items */ }

    private static final class BagItem extends Item {
        private final int capacity;
        BagItem(int capacity) { super(new FabricItemSettings().maxCount(1)); this.capacity = capacity; }
        @Override public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
            ItemStack bag = player.getStackInHand(hand);
            if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
                // The active bag stays in its inventory slot while the screen is open.
                int lockedSlot = hand == Hand.MAIN_HAND ? player.getInventory().selectedSlot : 40;
                NbtCompound data = bag.getOrCreateNbt();
                NbtList stored = data.getList("StoredItems", 10);
                final boolean[] loading = {true};
                SimpleInventory contents = new SimpleInventory(capacity) {
                    @Override public boolean isValid(int slot, ItemStack stack) {
                        return !(stack.getItem() instanceof BagItem); // prevent nested bags
                    }
                    @Override public void markDirty() {
                        super.markDirty();
                        if (loading[0]) return; // Do not overwrite saved contents while restoring slots.
                        NbtList list = new NbtList();
                        for (int i = 0; i < size(); i++) {
                            ItemStack stack = getStack(i);
                            if (stack.isEmpty()) continue;
                            NbtCompound entry = new NbtCompound();
                            entry.putByte("Slot", (byte)i);
                            stack.writeNbt(entry);
                            list.add(entry);
                        }
                        data.put("StoredItems", list);
                    }
                };
                for (int i = 0; i < stored.size(); i++) {
                    NbtCompound entry = stored.getCompound(i);
                    int slot = entry.getByte("Slot") & 255;
                    if (slot < capacity) contents.setStack(slot, ItemStack.fromNbt(entry));
                }
                loading[0] = false;
                serverPlayer.openHandledScreen(new NamedScreenHandlerFactory() {
                    @Override public Text getDisplayName() { return Text.translatable(bag.getItem().getTranslationKey()); }
                    @Override public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity p) {
                        int rows = capacity / 9;
                        return new GenericContainerScreenHandler((rows == 1 ? net.minecraft.screen.ScreenHandlerType.GENERIC_9X1 : rows == 2 ? net.minecraft.screen.ScreenHandlerType.GENERIC_9X2 : net.minecraft.screen.ScreenHandlerType.GENERIC_9X3),
                                syncId, inventory, contents, rows) {
                            @Override public boolean canUse(PlayerEntity user) {
                                return user.getInventory().getStack(lockedSlot) == bag && super.canUse(user);
                            }
                            @Override public void onClosed(PlayerEntity user) {
                                contents.markDirty();
                                super.onClosed(user);
                            }
                            @Override public ItemStack quickMove(PlayerEntity user, int slot) {
                                // Disable shift-click: vanilla handler bypasses custom inventory insertion rules.
                                return ItemStack.EMPTY;
                            }
                            @Override public boolean canInsertIntoSlot(ItemStack stack, Slot slot) {
                                return !(stack.getItem() instanceof BagItem) && super.canInsertIntoSlot(stack, slot);
                            }
                        };
                    }
                });
            }
            return TypedActionResult.success(bag, world.isClient());
        }
    }
}

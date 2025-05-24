package com.blocklogic.realfilingreborn.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TestBlocksGeneratorBlockEntity extends BlockEntity {
    private static final int GENERATION_RATE = 64;
    private static final int TICK_INTERVAL = 5;
    private int tickCounter = 0;
    private static final List<Item> STACKABLE_ITEMS = new ArrayList<>();
    private static final Random RANDOM = new Random();
    private static int currentItemIndex = 0;

    static {
        buildStackableItemList();
        System.out.println("Found " + STACKABLE_ITEMS.size() + " stackable items for stress testing");
    }

    private static void buildStackableItemList() {
        for (Item item : BuiltInRegistries.ITEM) {
            if (isValidForStressTesting(item)) {
                STACKABLE_ITEMS.add(item);
            }
        }
    }

    private static boolean isValidForStressTesting(Item item) {
        // Skip air
        if (item == Items.AIR) return false;

        // Create a test stack to check properties
        ItemStack testStack = new ItemStack(item);

        // Must be stackable (max stack size > 1)
        if (testStack.getMaxStackSize() <= 1) return false;

        // Skip items that typically have NBT data
        if (isNBTItem(item)) return false;

        return true;
    }

    private static boolean isNBTItem(Item item) {
        String itemName = item.toString();

        // Skip tools, weapons, armor (they have durability/enchantments)
        if (itemName.contains("sword") ||
                itemName.contains("pickaxe") ||
                itemName.contains("axe") ||
                itemName.contains("shovel") ||
                itemName.contains("hoe") ||
                itemName.contains("helmet") ||
                itemName.contains("chestplate") ||
                itemName.contains("leggings") ||
                itemName.contains("boots") ||
                itemName.contains("bow") ||
                itemName.contains("crossbow") ||
                itemName.contains("trident") ||
                itemName.contains("shield") ||
                itemName.contains("elytra")) {
            return true;
        }

        // Skip potions, books, maps, etc.
        if (item == Items.POTION ||
                item == Items.SPLASH_POTION ||
                item == Items.LINGERING_POTION ||
                item == Items.TIPPED_ARROW ||
                item == Items.ENCHANTED_BOOK ||
                item == Items.WRITTEN_BOOK ||
                item == Items.WRITABLE_BOOK ||
                item == Items.MAP ||
                item == Items.FILLED_MAP ||
                item == Items.FIREWORK_ROCKET ||
                item == Items.FIREWORK_STAR ||
                item == Items.SUSPICIOUS_STEW ||
                item == Items.PLAYER_HEAD ||
                item == Items.ZOMBIE_HEAD ||
                item == Items.SKELETON_SKULL ||
                item == Items.WITHER_SKELETON_SKULL ||
                item == Items.CREEPER_HEAD ||
                item == Items.DRAGON_HEAD ||
                item == Items.PIGLIN_HEAD) {
            return true;
        }

        // Skip spawn eggs
        if (itemName.contains("spawn_egg")) return true;

        // Skip debug/creative-only items
        if (item == Items.LIGHT ||
                item == Items.COMMAND_BLOCK ||
                item == Items.CHAIN_COMMAND_BLOCK ||
                item == Items.REPEATING_COMMAND_BLOCK ||
                item == Items.COMMAND_BLOCK_MINECART ||
                item == Items.STRUCTURE_BLOCK ||
                item == Items.STRUCTURE_VOID ||
                item == Items.JIGSAW ||
                item == Items.BARRIER ||
                item == Items.DEBUG_STICK ||
                item == Items.KNOWLEDGE_BOOK ||
                item == Items.BUNDLE) {
            return true;
        }

        // Skip boundary blocks and other debug items
        if (itemName.contains("light") && itemName.contains("block")) return true;
        if (itemName.contains("barrier")) return true;
        if (itemName.contains("structure_void")) return true;
        if (itemName.contains("jigsaw")) return true;
        if (itemName.contains("command")) return true;
        if (itemName.contains("debug")) return true;

        return false;
    }

    public TestBlocksGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TEST_BLOCKS_GENERATOR_BE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, TestBlocksGeneratorBlockEntity blockEntity) {
        if (level.isClientSide()) {
            return;
        }

        if (!level.hasNeighborSignal(pos)) {
            return;
        }

        blockEntity.tickCounter++;
        if (blockEntity.tickCounter >= TICK_INTERVAL) {
            blockEntity.tickCounter = 0;
            blockEntity.generateBlocks(level, pos);
        }
    }

    private void generateBlocks(Level level, BlockPos pos) {
        BlockPos chestPos = pos.above();
        BlockEntity blockEntity = level.getBlockEntity(chestPos);

        // Check if the block entity has an item handler capability
        if (blockEntity == null) {
            return;
        }

        // Try to get the item handler from the block entity
        net.neoforged.neoforge.items.IItemHandler itemHandler = blockEntity.getLevel().getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, chestPos, null);

        if (itemHandler == null) {
            return;
        }

        // Generate items in round-robin fashion for unique distribution
        for (int i = 0; i < GENERATION_RATE; i++) {
            if (STACKABLE_ITEMS.isEmpty()) break;

            // Round-robin through all available items
            Item currentItem = STACKABLE_ITEMS.get(currentItemIndex % STACKABLE_ITEMS.size());
            currentItemIndex++;

            ItemStack itemStack = new ItemStack(currentItem, 64);

            // Try to insert into any available slot
            ItemStack remaining = itemStack;
            for (int slot = 0; slot < itemHandler.getSlots(); slot++) {
                remaining = itemHandler.insertItem(slot, remaining, false);
                if (remaining.isEmpty()) {
                    break; // Successfully inserted all items
                }
            }

            if (!remaining.isEmpty() && remaining.getCount() == itemStack.getCount()) {
                break; // No space left in inventory
            }
        }

        blockEntity.setChanged();
    }

    // Debug method to print available items
    public static void printAvailableItems() {
        System.out.println("=== STACKABLE ITEMS FOR STRESS TESTING ===");
        for (int i = 0; i < Math.min(50, STACKABLE_ITEMS.size()); i++) {
            Item item = STACKABLE_ITEMS.get(i);
            System.out.println(i + ": " + item.toString());
        }
        System.out.println("... and " + (STACKABLE_ITEMS.size() - 50) + " more items");
        System.out.println("Total: " + STACKABLE_ITEMS.size() + " stackable items");
    }

    // Method to get a specific item by index (useful for priming folders)
    public static Item getItemByIndex(int index) {
        if (index < 0 || index >= STACKABLE_ITEMS.size()) {
            return Items.STONE; // Fallback
        }
        return STACKABLE_ITEMS.get(index);
    }

    // Get total number of available items
    public static int getTotalItemCount() {
        return STACKABLE_ITEMS.size();
    }
}
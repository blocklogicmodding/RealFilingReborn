package com.blocklogic.realfilingreborn.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TestNBTItemsGeneratorBlockEntity extends BlockEntity {
    private static final int TICK_INTERVAL = 5; // 20 ticks = 1 second
    private int tickCounter = 0;
    private static final Random RANDOM = new Random();

    // Pool of 12 items that can have NBT data
    private static final Item[] ITEM_POOL = {
            Items.DIAMOND_SWORD,
            Items.NETHERITE_SWORD,
            Items.DIAMOND_PICKAXE,
            Items.BOW,
            Items.CROSSBOW,
            Items.TRIDENT,
            Items.DIAMOND_HELMET,
            Items.NETHERITE_CHESTPLATE,
            Items.SHIELD,
            Items.ELYTRA,
            Items.FISHING_ROD,
            Items.GOLDEN_APPLE
    };

    // Name prefixes and suffixes for randomization
    private static final String[] NAME_PREFIXES = {
            "Legendary", "Ancient", "Divine", "Forgotten", "Mythical",
            "Celestial", "Infernal", "Royal", "Epic", "Heroic"
    };

    private static final String[] NAME_SUFFIXES = {
            "of Power", "of Glory", "of Doom", "of Twilight", "of Thunder",
            "of the Depths", "of Legends", "of Eternity", "of Souls", "of Dragons"
    };

    // Lore options
    private static final String[][] LORE_SETS = {
            {"Forged in the depths of the Nether", "Blessed by the ancient gods"},
            {"Crafted from the bones of a dragon", "Infused with magical essence"},
            {"Found in a forgotten temple", "Carries the whispers of the void"},
            {"A gift from the End", "It hungers for power"},
            {"Created during the first age", "Only heroes may wield it"},
            {"Stolen from the treasury of kings", "Cursed by its original owner"},
            {"Fell from the stars", "Contains cosmic energy"},
            {"Recovered from the ocean depths", "Smells faintly of salt and mystery"},
            {"Crafted by master artisans", "No two are alike"},
            {"Passed down through generations", "Its history is written in blood"}
    };

    public TestNBTItemsGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TEST_NBT_ITEMS_GENERATOR_BE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, TestNBTItemsGeneratorBlockEntity blockEntity) {
        if (level.isClientSide()) {
            return;
        }

        // Check if block is receiving a redstone signal
        if (!level.hasNeighborSignal(pos)) {
            return; // No redstone signal, don't generate
        }

        blockEntity.tickCounter++;
        if (blockEntity.tickCounter >= TICK_INTERVAL) {
            blockEntity.tickCounter = 0;
            blockEntity.generateNBTItem(level, pos);
        }
    }

    private void generateNBTItem(Level level, BlockPos pos) {
        // Check for a chest above
        BlockPos chestPos = pos.above();
        BlockState chestState = level.getBlockState(chestPos);

        if (!(chestState.getBlock() instanceof ChestBlock)) {
            return; // No chest above, don't generate
        }

        BlockEntity blockEntity = level.getBlockEntity(chestPos);
        if (!(blockEntity instanceof ChestBlockEntity chestEntity)) {
            return; // Not a chest entity
        }

        // Create a random NBT item
        ItemStack nbtItem = createRandomNBTItem();

        // Try to add the item to the chest inventory
        boolean added = false;
        for (int slot = 0; slot < chestEntity.getContainerSize(); slot++) {
            ItemStack existingStack = chestEntity.getItem(slot);

            if (existingStack.isEmpty()) {
                // Empty slot, add the item
                chestEntity.setItem(slot, nbtItem);
                added = true;
                break;
            }
        }

        if (added) {
            // Mark the chest as changed
            chestEntity.setChanged();
        }
    }

    private ItemStack createRandomNBTItem() {
        // Select a random item from our pool
        Item item = ITEM_POOL[RANDOM.nextInt(ITEM_POOL.length)];
        ItemStack itemStack = new ItemStack(item);

        // Decide which NBT features to add (can have multiple)
        boolean addName = RANDOM.nextBoolean();
        boolean addDamage = RANDOM.nextBoolean() && itemStack.isDamageableItem();
        boolean addLore = RANDOM.nextBoolean();

        // Ensure at least one NBT feature is added
        if (!addName && !addDamage && !addLore) {
            // Force one random feature
            int feature = RANDOM.nextInt(3);
            if (feature == 0) addName = true;
            else if (feature == 1 && itemStack.isDamageableItem()) addDamage = true;
            else addLore = true;
        }

        // Add custom name (if selected)
        if (addName) {
            String prefix = NAME_PREFIXES[RANDOM.nextInt(NAME_PREFIXES.length)];
            String itemName = item.getDescription().getString();

            // 50% chance to add a suffix
            String suffix = RANDOM.nextBoolean() ?
                    " " + NAME_SUFFIXES[RANDOM.nextInt(NAME_SUFFIXES.length)] : "";

            Component customName = Component.literal(prefix + " " + itemName + suffix);
            itemStack.set(DataComponents.CUSTOM_NAME, customName);
        }

        // Add damage (if selected and item is damageable)
        if (addDamage) {
            int maxDamage = itemStack.getMaxDamage();
            // Random damage between 10% and 75%
            int damagePercent = 10 + RANDOM.nextInt(65);
            int damage = (int)(maxDamage * (damagePercent / 100.0));
            itemStack.setDamageValue(damage);
        }

        // Add lore (if selected)
        if (addLore) {
            // Select a random lore set
            String[] loreSet = LORE_SETS[RANDOM.nextInt(LORE_SETS.length)];
            List<Component> loreLines = new ArrayList<>();

            // Add 1 or 2 lines from the set
            int lineCount = 1 + RANDOM.nextInt(loreSet.length);
            for (int i = 0; i < Math.min(lineCount, loreSet.length); i++) {
                loreLines.add(Component.literal(loreSet[i]));
            }

            ItemLore lore = new ItemLore(loreLines);
            itemStack.set(DataComponents.LORE, lore);
        }

        return itemStack;
    }
}
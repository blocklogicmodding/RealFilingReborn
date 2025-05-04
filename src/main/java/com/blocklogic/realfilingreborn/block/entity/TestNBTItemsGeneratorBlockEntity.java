package com.blocklogic.realfilingreborn.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TestNBTItemsGeneratorBlockEntity extends BlockEntity {
    private static final int TICK_INTERVAL = 5;
    private int tickCounter = 0;
    private static final Random RANDOM = new Random();

    private static final Item[] ITEM_POOL = {
            Items.DIAMOND_SWORD,
            Items.NETHERITE_SWORD,
            Items.DIAMOND_PICKAXE,
            Items.BOW,
            Items.IRON_BOOTS
    };

    private static final String[] NAME_PREFIXES = {
            "Legendary", "Ancient", "Divine", "Forgotten", "Mythical",
            "Celestial", "Infernal", "Royal", "Epic", "Heroic"
    };

    private static final String[] NAME_SUFFIXES = {
            "of Power", "of Glory", "of Doom", "of Twilight", "of Thunder",
            "of the Depths", "of Legends", "of Eternity", "of Souls", "of Dragons"
    };

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

        if (!level.hasNeighborSignal(pos)) {
            return;
        }

        blockEntity.tickCounter++;
        if (blockEntity.tickCounter >= TICK_INTERVAL) {
            blockEntity.tickCounter = 0;
            blockEntity.generateNBTItem(level, pos);
        }
    }

    private void generateNBTItem(Level level, BlockPos pos) {
        BlockPos chestPos = pos.above();
        BlockState chestState = level.getBlockState(chestPos);

        if (!(chestState.getBlock() instanceof BarrelBlock)) {
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(chestPos);
        if (!(blockEntity instanceof BarrelBlockEntity barrelBlockEntity)) {
            return;
        }

        ItemStack nbtItem = createRandomNBTItem();

        boolean added = false;
        for (int slot = 0; slot < barrelBlockEntity.getContainerSize(); slot++) {
            ItemStack existingStack = barrelBlockEntity.getItem(slot);

            if (existingStack.isEmpty()) {
                barrelBlockEntity.setItem(slot, nbtItem);
                added = true;
                break;
            }
        }

        if (added) {
            barrelBlockEntity.setChanged();
        }
    }

    private ItemStack createRandomNBTItem() {
        Item item = ITEM_POOL[RANDOM.nextInt(ITEM_POOL.length)];
        ItemStack itemStack = new ItemStack(item);

        boolean addName = RANDOM.nextBoolean();
        boolean addDamage = RANDOM.nextBoolean() && itemStack.isDamageableItem();
        boolean addLore = RANDOM.nextBoolean();

        if (!addName && !addDamage && !addLore) {
            int feature = RANDOM.nextInt(3);
            if (feature == 0) addName = true;
            else if (feature == 1 && itemStack.isDamageableItem()) addDamage = true;
            else addLore = true;
        }

        if (addName) {
            String prefix = NAME_PREFIXES[RANDOM.nextInt(NAME_PREFIXES.length)];
            String itemName = item.getDescription().getString();

            String suffix = RANDOM.nextBoolean() ?
                    " " + NAME_SUFFIXES[RANDOM.nextInt(NAME_SUFFIXES.length)] : "";

            Component customName = Component.literal(prefix + " " + itemName + suffix);
            itemStack.set(DataComponents.CUSTOM_NAME, customName);
        }

        if (addDamage) {
            int maxDamage = itemStack.getMaxDamage();
            int damagePercent = 10 + RANDOM.nextInt(65);
            int damage = (int)(maxDamage * (damagePercent / 100.0));
            itemStack.setDamageValue(damage);
        }

        if (addLore) {
            String[] loreSet = LORE_SETS[RANDOM.nextInt(LORE_SETS.length)];
            List<Component> loreLines = new ArrayList<>();

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
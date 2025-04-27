package com.blocklogic.realfilingreborn.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.ArrayList;
import java.util.Random;

public class TestBlocksGeneratorBlockEntity extends BlockEntity {
    private static final int GENERATION_RATE = 64; // blocks per second
    private static final int TICK_INTERVAL = 5; // 20 ticks = 1 second
    private int tickCounter = 0;
    private static final List<Block> COMMON_BLOCKS = new ArrayList<>();
    private static final Random RANDOM = new Random();

    static {
        // Initialize a list of common blocks to generate
        COMMON_BLOCKS.add(Blocks.STONE);
        COMMON_BLOCKS.add(Blocks.DIRT);
        COMMON_BLOCKS.add(Blocks.COBBLESTONE);
        COMMON_BLOCKS.add(Blocks.GRAVEL);
        COMMON_BLOCKS.add(Blocks.SAND);
        COMMON_BLOCKS.add(Blocks.GRASS_BLOCK);
        COMMON_BLOCKS.add(Blocks.OAK_LOG);
        COMMON_BLOCKS.add(Blocks.OAK_PLANKS);
        COMMON_BLOCKS.add(Blocks.ANDESITE);
        COMMON_BLOCKS.add(Blocks.DIORITE);
        COMMON_BLOCKS.add(Blocks.GRANITE);
        COMMON_BLOCKS.add(Blocks.COARSE_DIRT);
    }

    public TestBlocksGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TEST_BLOCKS_GENERATOR_BE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, TestBlocksGeneratorBlockEntity blockEntity) {
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
            blockEntity.generateBlocks(level, pos);
        }
    }

    private void generateBlocks(Level level, BlockPos pos) {
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

        // Generate 64 random blocks and attempt to put them in the chest
        for (int i = 0; i < GENERATION_RATE; i++) {
            Block randomBlock = COMMON_BLOCKS.get(RANDOM.nextInt(COMMON_BLOCKS.size()));
            ItemStack blockStack = new ItemStack(randomBlock);

            // Try to add the item to the chest inventory
            boolean added = false;
            for (int slot = 0; slot < chestEntity.getContainerSize(); slot++) {
                ItemStack existingStack = chestEntity.getItem(slot);

                if (existingStack.isEmpty()) {
                    // Empty slot, add the item
                    chestEntity.setItem(slot, blockStack);
                    added = true;
                    break;
                } else if (ItemStack.isSameItem(existingStack, blockStack) &&
                        existingStack.getCount() < existingStack.getMaxStackSize()) {
                    // Same item and has space, increase count
                    existingStack.grow(1);
                    added = true;
                    break;
                }
            }

            if (!added) {
                // Chest is full, stop generating
                break;
            }
        }

        // Mark the chest as changed
        chestEntity.setChanged();
    }
}
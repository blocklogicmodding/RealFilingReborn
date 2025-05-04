package com.blocklogic.realfilingreborn.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.ArrayList;
import java.util.Random;

public class TestBlocksGeneratorBlockEntity extends BlockEntity {
    private static final int GENERATION_RATE = 64;
    private static final int TICK_INTERVAL = 5;
    private int tickCounter = 0;
    private static final List<Block> COMMON_BLOCKS = new ArrayList<>();
    private static final Random RANDOM = new Random();

    static {
        COMMON_BLOCKS.add(Blocks.STONE);
        COMMON_BLOCKS.add(Blocks.DIRT);
        COMMON_BLOCKS.add(Blocks.COBBLESTONE);
        COMMON_BLOCKS.add(Blocks.GRAVEL);
        COMMON_BLOCKS.add(Blocks.SAND);
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
        BlockState chestState = level.getBlockState(chestPos);

        if (!(chestState.getBlock() instanceof BarrelBlock)) {
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(chestPos);
        if (!(blockEntity instanceof BarrelBlockEntity barrelBlockEntity)) {
            return;
        }

        for (int i = 0; i < GENERATION_RATE; i++) {
            Block randomBlock = COMMON_BLOCKS.get(RANDOM.nextInt(COMMON_BLOCKS.size()));
            ItemStack blockStack = new ItemStack(randomBlock);

            boolean added = false;
            for (int slot = 0; slot < barrelBlockEntity.getContainerSize(); slot++) {
                ItemStack existingStack = barrelBlockEntity.getItem(slot);

                if (existingStack.isEmpty()) {
                    barrelBlockEntity.setItem(slot, blockStack);
                    added = true;
                    break;
                } else if (ItemStack.isSameItem(existingStack, blockStack) &&
                        existingStack.getCount() < existingStack.getMaxStackSize()) {
                    existingStack.grow(1);
                    added = true;
                    break;
                }
            }

            if (!added) {
                break;
            }
        }

        barrelBlockEntity.setChanged();
    }
}
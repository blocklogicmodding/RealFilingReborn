package com.blocklogic.realfilingreborn.block.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class IndexCableCoreBlock extends Block {
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;

    public IndexCableCoreBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(NORTH, false)
                .setValue(SOUTH, false)
                .setValue(EAST, false)
                .setValue(WEST, false)
                .setValue(UP, false)
                .setValue(DOWN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        return this.defaultBlockState()
                .setValue(NORTH, canConnectTo(level, pos, Direction.NORTH))
                .setValue(SOUTH, canConnectTo(level, pos, Direction.SOUTH))
                .setValue(EAST, canConnectTo(level, pos, Direction.EAST))
                .setValue(WEST, canConnectTo(level, pos, Direction.WEST))
                .setValue(UP, canConnectTo(level, pos, Direction.UP))
                .setValue(DOWN, canConnectTo(level, pos, Direction.DOWN));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        BooleanProperty property = getPropertyForDirection(direction);
        if (property != null) {
            return state.setValue(property, canConnectTo(level, pos, direction));
        }
        return state;
    }

    private boolean canConnectTo(LevelAccessor level, BlockPos pos, Direction direction) {
        BlockPos neighborPos = pos.relative(direction);
        BlockState neighborState = level.getBlockState(neighborPos);
        Block neighborBlock = neighborState.getBlock();

        if (neighborBlock instanceof IndexCableCoreBlock) {
            return true;
        }

        if (neighborBlock instanceof FilingCabinetBlock) {
            return true;
        }

        if (neighborBlock instanceof FluidCabinetBlock) {
            return true;
        }

        if (neighborBlock instanceof FilingIndexBlock) {
            return true;
        }

        return false;
    }

    private BooleanProperty getPropertyForDirection(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
            case UP -> UP;
            case DOWN -> DOWN;
        };
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = Block.box(5, 5, 5, 11, 11, 11); // Core shape

        if (state.getValue(NORTH)) {
            shape = Shapes.or(shape, Block.box(5, 5, 0, 11, 11, 5));
        }
        if (state.getValue(SOUTH)) {
            shape = Shapes.or(shape, Block.box(5, 5, 11, 11, 11, 16));
        }
        if (state.getValue(EAST)) {
            shape = Shapes.or(shape, Block.box(11, 5, 5, 16, 11, 11));
        }
        if (state.getValue(WEST)) {
            shape = Shapes.or(shape, Block.box(0, 5, 5, 5, 11, 11));
        }
        if (state.getValue(UP)) {
            shape = Shapes.or(shape, Block.box(5, 11, 5, 11, 16, 11));
        }
        if (state.getValue(DOWN)) {
            shape = Shapes.or(shape, Block.box(5, 0, 5, 11, 5, 11));
        }

        return shape;
    }
}
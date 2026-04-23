package com.dimsimd.neocomputers.block;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class PeripheralBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    private static final VoxelShape MONITOR_SHAPE = Block.box(0.0D, 0.0D, 2.0D, 16.0D, 16.0D, 5.0D);
    private static final VoxelShape KEYBOARD_SHAPE = Block.box(1.0D, 0.0D, 4.0D, 15.0D, 3.0D, 12.0D);
    private static final VoxelShape MOUSE_SHAPE = Block.box(5.0D, 0.0D, 4.0D, 11.0D, 4.0D, 12.0D);
    private static final VoxelShape KEYBOARD_MOUSE_SHAPE = Shapes.or(
        Block.box(1.0D, 0.0D, 4.0D, 12.0D, 3.0D, 12.0D),
        Block.box(13.0D, 0.0D, 5.0D, 16.0D, 4.0D, 11.0D)
    );

    private final PeripheralKind kind;

    public PeripheralBlock(PeripheralKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    public PeripheralKind kind() {
        return kind;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (kind) {
            case MONITOR -> MONITOR_SHAPE;
            case KEYBOARD -> KEYBOARD_SHAPE;
            case MOUSE -> MOUSE_SHAPE;
            case KEYBOARD_MOUSE -> KEYBOARD_MOUSE_SHAPE;
        };
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PeripheralBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof PeripheralBlockEntity blockEntity) {
            blockEntity.applyDefaults(kind);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof PeripheralBlockEntity blockEntity) {
            blockEntity.refreshWiredLink();
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (level.getBlockEntity(pos) instanceof PeripheralBlockEntity blockEntity) {
            if (kind == PeripheralKind.KEYBOARD_MOUSE && !player.isShiftKeyDown()) {
                player.openMenu(blockEntity, buffer -> buffer.writeBlockPos(pos));
            } else if (player.isShiftKeyDown()) {
                blockEntity.cycleLightingColor();
            } else {
                blockEntity.cycleLightingBrightness();
            }
            return InteractionResult.CONSUME;
        }

        return InteractionResult.PASS;
    }

    public enum PeripheralKind implements StringRepresentable {
        MONITOR("monitor", 0x1EA7FF, 80),
        KEYBOARD("keyboard", 0x8A5CFF, 70),
        MOUSE("mouse", 0x33E0B0, 70),
        KEYBOARD_MOUSE("keyboard_mouse", 0x33E0B0, 70);

        private final String serializedName;
        private final int defaultColor;
        private final int defaultBrightness;

        PeripheralKind(String serializedName, int defaultColor, int defaultBrightness) {
            this.serializedName = serializedName;
            this.defaultColor = defaultColor;
            this.defaultBrightness = defaultBrightness;
        }

        public int defaultColor() {
            return defaultColor;
        }

        public int defaultBrightness() {
            return defaultBrightness;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }
}

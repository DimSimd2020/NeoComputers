package com.dimsimd.neocomputers.block;

import com.dimsimd.neocomputers.NeoComputers;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class PeripheralCableNetwork {
    private static final int MAX_VISITED_CABLES = 256;

    private PeripheralCableNetwork() {
    }

    @Nullable
    public static ComputerBlockEntity findConnectedComputer(Level level, BlockPos origin) {
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = origin.relative(direction);
            BlockState neighborState = level.getBlockState(neighborPos);
            if (neighborState.is(NeoComputers.COMPUTER_BLOCK.get())) {
                return computerAt(level, neighborPos);
            }
        }

        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = origin.relative(direction);
            if (level.getBlockState(neighborPos).is(NeoComputers.PERIPHERAL_CABLE_BLOCK.get())) {
                BlockPos immutable = neighborPos.immutable();
                visited.add(immutable);
                queue.add(immutable);
            }
        }

        while (!queue.isEmpty() && visited.size() <= MAX_VISITED_CABLES) {
            BlockPos cablePos = queue.removeFirst();
            for (Direction direction : Direction.values()) {
                BlockPos neighborPos = cablePos.relative(direction);
                BlockState neighborState = level.getBlockState(neighborPos);
                if (neighborState.is(NeoComputers.COMPUTER_BLOCK.get())) {
                    return computerAt(level, neighborPos);
                }
                if (neighborState.is(NeoComputers.PERIPHERAL_CABLE_BLOCK.get()) && !visited.contains(neighborPos)) {
                    BlockPos immutable = neighborPos.immutable();
                    visited.add(immutable);
                    queue.add(immutable);
                }
            }
        }

        return null;
    }

    @Nullable
    public static BlockPos findConnectedComputerPos(Level level, BlockPos origin) {
        ComputerBlockEntity computer = findConnectedComputer(level, origin);
        return computer == null ? null : computer.getBlockPos();
    }

    @Nullable
    private static ComputerBlockEntity computerAt(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof ComputerBlockEntity computerBlockEntity) {
            return computerBlockEntity;
        }
        return null;
    }
}

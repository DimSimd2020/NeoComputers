package com.vibecoder.neocomputers.block;

import com.vibecoder.neocomputers.NeoComputers;
import com.vibecoder.neocomputers.vm.NativeVmRuntime;
import java.lang.foreign.MemorySegment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class ComputerBlockEntity extends BlockEntity {
    private static final int VM_MEMORY_MEGABYTES = 512;

    private MemorySegment vmHandle = MemorySegment.NULL;

    public ComputerBlockEntity(BlockPos pos, BlockState blockState) {
        super(NeoComputers.COMPUTER_BLOCK_ENTITY.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ComputerBlockEntity blockEntity) {
        blockEntity.tickServer();
    }

    public void destroyVm() {
        if (!hasVm()) {
            return;
        }

        MemorySegment handle = vmHandle;
        vmHandle = MemorySegment.NULL;
        NativeVmRuntime.destroyVm(handle);
    }

    @Override
    public void setRemoved() {
        destroyVm();
        super.setRemoved();
    }

    private void tickServer() {
        ensureVm();
        NativeVmRuntime.tickVm(vmHandle);
    }

    private void ensureVm() {
        if (hasVm()) {
            return;
        }

        MemorySegment createdVm = NativeVmRuntime.createVm(VM_MEMORY_MEGABYTES);
        if (createdVm == MemorySegment.NULL) {
            throw new IllegalStateException("Native create_vm returned NULL for computer at " + worldPosition);
        }

        vmHandle = createdVm;
    }

    private boolean hasVm() {
        return vmHandle != MemorySegment.NULL;
    }
}

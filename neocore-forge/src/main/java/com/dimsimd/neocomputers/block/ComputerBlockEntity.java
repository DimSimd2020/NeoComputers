package com.dimsimd.neocomputers.block;

import com.dimsimd.neocomputers.NeoComputers;
import com.dimsimd.neocomputers.vm.NativeVmRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class ComputerBlockEntity extends BlockEntity {
    private static final int VM_MEMORY_MEGABYTES = 512;

    private long vmHandle = 0L;

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

        long handle = vmHandle;
        vmHandle = 0L;
        NativeVmRuntime.destroyVm(handle);
    }

    @Override
    public void setRemoved() {
        destroyVm();
        super.setRemoved();
    }

    private void tickServer() {
        if (!NativeVmRuntime.isAvailable()) {
            return;
        }
        ensureVm();
        NativeVmRuntime.tickVm(vmHandle);
    }

    private void ensureVm() {
        if (hasVm()) {
            return;
        }

        long createdVm = NativeVmRuntime.createVm(VM_MEMORY_MEGABYTES);
        if (createdVm == 0L) {
            throw new IllegalStateException("Native create_vm returned NULL for computer at " + worldPosition);
        }

        vmHandle = createdVm;
    }

    private boolean hasVm() {
        return vmHandle != 0L;
    }
}

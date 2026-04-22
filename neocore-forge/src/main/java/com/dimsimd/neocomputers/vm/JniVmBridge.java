package com.dimsimd.neocomputers.vm;

public final class JniVmBridge implements VmBridge {
    @Override
    public native long createVm(int memorySizeMb);

    @Override
    public native void tickVm(long handle);

    @Override
    public native void destroyVm(long handle);
}

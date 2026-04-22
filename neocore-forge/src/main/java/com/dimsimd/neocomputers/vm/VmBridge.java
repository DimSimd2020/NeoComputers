package com.dimsimd.neocomputers.vm;

public interface VmBridge {
    long NULL_HANDLE = 0L;

    long createVm(int memorySizeMb);

    void tickVm(long handle);

    void destroyVm(long handle);
}

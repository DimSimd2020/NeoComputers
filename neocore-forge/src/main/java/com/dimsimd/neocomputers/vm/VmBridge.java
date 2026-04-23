package com.dimsimd.neocomputers.vm;

public interface VmBridge {
    long NULL_HANDLE = 0L;

    long createVm(int memorySizeMb, int diskSizeMb, String diskImage);

    void tickVm(long handle);

    void submitCommand(long handle, String command);

    String diskImage(long handle);

    String terminalSnapshot(long handle);

    String framebufferSnapshot(long handle);

    String installedOs(long handle);

    boolean isHalted(long handle);

    int bootCount(long handle);

    void destroyVm(long handle);
}

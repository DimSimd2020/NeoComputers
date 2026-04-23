package com.dimsimd.neocomputers.vm;

public final class JniVmBridge implements VmBridge {
    @Override
    public native long createVm(int memorySizeMb, int diskSizeMb, String diskImage);

    @Override
    public native void tickVm(long handle);

    @Override
    public native void submitCommand(long handle, String command);

    @Override
    public native String diskImage(long handle);

    @Override
    public native String terminalSnapshot(long handle);

    @Override
    public native String framebufferSnapshot(long handle);

    @Override
    public native String installedOs(long handle);

    @Override
    public native boolean isHalted(long handle);

    @Override
    public native int bootCount(long handle);

    @Override
    public native void destroyVm(long handle);
}

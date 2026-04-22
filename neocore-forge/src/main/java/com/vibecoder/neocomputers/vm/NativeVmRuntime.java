package com.vibecoder.neocomputers.vm;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.util.Objects;

public final class NativeVmRuntime {
    private static volatile MethodHandle createVmDowncall;
    private static volatile MethodHandle tickVmDowncall;
    private static volatile MethodHandle destroyVmDowncall;

    private NativeVmRuntime() {
    }

    public static void installDowncalls(Object downcalls) {
        Objects.requireNonNull(downcalls, "downcalls");

        MethodHandle createVm = VmBridgeContract.extractDowncall(downcalls, "createVm");
        MethodHandle tickVm = VmBridgeContract.extractDowncall(downcalls, "tickVm");
        MethodHandle destroyVm = VmBridgeContract.extractDowncall(downcalls, "destroyVm");

        createVmDowncall = createVm;
        tickVmDowncall = tickVm;
        destroyVmDowncall = destroyVm;
    }

    public static MemorySegment createVm(int memorySizeMb) {
        try {
            return (MemorySegment) requireCreateVmDowncall().invokeExact(memorySizeMb);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Native create_vm failed", throwable);
        }
    }

    public static void tickVm(MemorySegment handle) {
        Objects.requireNonNull(handle, "handle");
        try {
            requireTickVmDowncall().invokeExact(handle);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Native tick_vm failed", throwable);
        }
    }

    public static void destroyVm(MemorySegment handle) {
        Objects.requireNonNull(handle, "handle");
        try {
            requireDestroyVmDowncall().invokeExact(handle);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Native destroy_vm failed", throwable);
        }
    }

    private static MethodHandle requireCreateVmDowncall() {
        MethodHandle methodHandle = createVmDowncall;
        if (methodHandle == null) {
            throw new IllegalStateException("create_vm downcall is not initialized");
        }
        return methodHandle;
    }

    private static MethodHandle requireTickVmDowncall() {
        MethodHandle methodHandle = tickVmDowncall;
        if (methodHandle == null) {
            throw new IllegalStateException("tick_vm downcall is not initialized");
        }
        return methodHandle;
    }

    private static MethodHandle requireDestroyVmDowncall() {
        MethodHandle methodHandle = destroyVmDowncall;
        if (methodHandle == null) {
            throw new IllegalStateException("destroy_vm downcall is not initialized");
        }
        return methodHandle;
    }
}

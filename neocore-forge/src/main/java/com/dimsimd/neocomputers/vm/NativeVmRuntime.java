package com.dimsimd.neocomputers.vm;

import java.util.Objects;
import org.slf4j.Logger;

public final class NativeVmRuntime {
    private static final VmBridge BRIDGE = new JniVmBridge();
    private static volatile boolean available;

    private NativeVmRuntime() {
    }

    public static boolean initialize(String nativeLibraryName, Logger logger) {
        Objects.requireNonNull(nativeLibraryName, "nativeLibraryName");
        Objects.requireNonNull(logger, "logger");

        try {
            System.loadLibrary(nativeLibraryName);
        } catch (Throwable throwable) {
            logger.warn("Failed to load native library '{}': {}", nativeLibraryName, throwable.toString());
            available = false;
            return false;
        }

        try {
            long handle = BRIDGE.createVm(1);
            if (handle != VmBridge.NULL_HANDLE) {
                BRIDGE.destroyVm(handle);
            }
            available = true;
            return true;
        } catch (Throwable throwable) {
            logger.warn("Failed to initialize JNI VM bridge: {}", throwable.toString());
            available = false;
            return false;
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    public static long createVm(int memorySizeMb) {
        if (!available) {
            return VmBridge.NULL_HANDLE;
        }
        try {
            return BRIDGE.createVm(memorySizeMb);
        } catch (Throwable throwable) {
            throw new IllegalStateException("JNI createVm failed", throwable);
        }
    }

    public static void tickVm(long handle) {
        if (!available || handle == VmBridge.NULL_HANDLE) {
            return;
        }
        try {
            BRIDGE.tickVm(handle);
        } catch (Throwable throwable) {
            throw new IllegalStateException("JNI tickVm failed", throwable);
        }
    }

    public static void destroyVm(long handle) {
        if (!available || handle == VmBridge.NULL_HANDLE) {
            return;
        }
        try {
            BRIDGE.destroyVm(handle);
        } catch (Throwable throwable) {
            throw new IllegalStateException("JNI destroyVm failed", throwable);
        }
    }
}

package com.dimsimd.neocomputers.vm;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import org.slf4j.Logger;

public final class NativeVmRuntime {
    private static final String NATIVE_PATH_PROPERTY = "neocomputers.native.path";
    private static final String BACKEND_PROPERTY = "neocomputers.vm.backend";
    private static final String BACKEND_ENV = "NEOCOMPUTERS_VM_BACKEND";
    private static final String SHA_256 = "SHA-256";
    private static final String BUNDLED_NATIVE_ROOT = "natives";
    private static final VmBridge BRIDGE = new JniVmBridge();
    private static volatile boolean available;
    private static volatile Backend backend = Backend.TINY;

    private NativeVmRuntime() {
    }

    public static boolean initialize(String nativeLibraryName, Logger logger) {
        Objects.requireNonNull(nativeLibraryName, "nativeLibraryName");
        Objects.requireNonNull(logger, "logger");

        backend = configuredBackend();
        if (backend == Backend.TINY) {
            available = TinyVmRuntime.initialize(logger);
            return available;
        }
        if (backend == Backend.QEMU) {
            available = QemuVmRuntime.initialize(logger);
            return available;
        }

        try {
            loadNativeLibrary(nativeLibraryName, logger);
        } catch (Throwable throwable) {
            logger.warn("Failed to load native library '{}': {}", nativeLibraryName, throwable.toString());
            available = false;
            return false;
        }

        try {
            long handle = BRIDGE.createVm(1, 1, "");
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

    private static void loadNativeLibrary(String nativeLibraryName, Logger logger) throws IOException {
        Path configuredPath = resolveConfiguredNativePath();
        if (configuredPath != null) {
            System.load(configuredPath.toString());
            logger.info("Loaded native VM bridge from '{}'", configuredPath);
            return;
        }

        Path bundledPath = extractBundledNative(nativeLibraryName, logger);
        if (bundledPath != null) {
            System.load(bundledPath.toString());
            logger.info("Loaded bundled native VM bridge from '{}'", bundledPath);
            return;
        }

        System.loadLibrary(nativeLibraryName);
    }

    private static Path resolveConfiguredNativePath() throws IOException {
        String configuredNativePath = System.getProperty(NATIVE_PATH_PROPERTY);
        if (configuredNativePath == null || configuredNativePath.isBlank()) {
            return null;
        }

        Path nativePath = Path.of(configuredNativePath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(nativePath)) {
            throw new IOException("Configured native path is not a file: " + nativePath);
        }
        return nativePath;
    }

    private static Path extractBundledNative(String nativeLibraryName, Logger logger) throws IOException {
        String platformId = detectPlatformId();
        if (platformId == null) {
            logger.warn("Unsupported OS/arch for bundled native VM bridge: os='{}', arch='{}'", osName(), osArch());
            return null;
        }

        String libraryFileName = mapLibraryFileName(nativeLibraryName, osName());
        String resourcePath = "/" + BUNDLED_NATIVE_ROOT + "/" + platformId + "/" + libraryFileName;
        byte[] nativeBytes;
        try (InputStream input = NativeVmRuntime.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                logger.warn("Bundled native resource not found: {}", resourcePath);
                return null;
            }
            nativeBytes = input.readAllBytes();
        }

        String hash = sha256Hex(nativeBytes);
        Path targetDir = Path.of(
            System.getProperty("java.io.tmpdir"),
            "neocomputers",
            "natives",
            platformId,
            hash
        ).toAbsolutePath().normalize();
        Files.createDirectories(targetDir);

        Path extractedNativePath = targetDir.resolve(libraryFileName);
        if (Files.isRegularFile(extractedNativePath)) {
            byte[] existingBytes = Files.readAllBytes(extractedNativePath);
            if (hash.equals(sha256Hex(existingBytes))) {
                return extractedNativePath;
            }
        }

        Path tempFile = Files.createTempFile(targetDir, libraryFileName + ".", ".tmp");
        boolean moved = false;
        try {
            Files.write(tempFile, nativeBytes);
            try {
                Files.move(tempFile, extractedNativePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                moved = true;
            } catch (IOException atomicMoveFailure) {
                Files.move(tempFile, extractedNativePath, StandardCopyOption.REPLACE_EXISTING);
                moved = true;
            }
        } finally {
            if (!moved) {
                Files.deleteIfExists(tempFile);
            }
        }

        return extractedNativePath;
    }

    private static String detectPlatformId() {
        String osName = osName();
        String osArch = osArch();

        if (osName.contains("win") && (osArch.equals("amd64") || osArch.equals("x86_64"))) {
            return "windows-x86_64";
        }
        if (osName.contains("linux") && (osArch.equals("amd64") || osArch.equals("x86_64"))) {
            return "linux-x86_64";
        }
        if (osName.contains("mac") && (osArch.equals("aarch64") || osArch.equals("arm64"))) {
            return "macos-aarch64";
        }
        if (osName.contains("mac") && (osArch.equals("amd64") || osArch.equals("x86_64"))) {
            return "macos-x86_64";
        }
        return null;
    }

    private static String mapLibraryFileName(String nativeLibraryName, String osName) {
        if (osName.contains("win")) {
            return nativeLibraryName + ".dll";
        }
        if (osName.contains("mac")) {
            return "lib" + nativeLibraryName + ".dylib";
        }
        return "lib" + nativeLibraryName + ".so";
    }

    private static String osName() {
        return System.getProperty("os.name", "").toLowerCase();
    }

    private static String osArch() {
        return System.getProperty("os.arch", "").toLowerCase();
    }

    private static String sha256Hex(byte[] data) {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(SHA_256);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }

        byte[] hash = digest.digest(data);
        StringBuilder builder = new StringBuilder(hash.length * 2);
        for (byte value : hash) {
            int normalized = value & 0xff;
            if (normalized < 16) {
                builder.append('0');
            }
            builder.append(Integer.toHexString(normalized));
        }
        return builder.toString();
    }

    public static boolean isAvailable() {
        return available;
    }

    public static boolean usesQemuBackend() {
        return backend == Backend.QEMU;
    }

    public static String qemuDiskPrefix() {
        return QemuVmRuntime.diskPrefix();
    }

    public static long createVm(int memorySizeMb, int diskSizeMb, String diskImage) {
        return createVm(memorySizeMb, diskSizeMb, diskImage, false);
    }

    public static long createVm(int memorySizeMb, int diskSizeMb, String diskImage, boolean networkEnabled) {
        if (!available) {
            return VmBridge.NULL_HANDLE;
        }
        if (backend == Backend.TINY) {
            return TinyVmRuntime.createVm(memorySizeMb, diskSizeMb, diskImage, networkEnabled);
        }
        if (backend == Backend.QEMU) {
            return QemuVmRuntime.createVm(memorySizeMb, diskSizeMb, diskImage);
        }
        try {
            return BRIDGE.createVm(memorySizeMb, diskSizeMb, diskImage == null ? "" : diskImage);
        } catch (Throwable throwable) {
            throw new IllegalStateException("JNI createVm failed", throwable);
        }
    }

    public static void tickVm(long handle) {
        if (!available || handle == VmBridge.NULL_HANDLE) {
            return;
        }
        if (backend == Backend.TINY) {
            TinyVmRuntime.tickVm(handle);
            return;
        }
        if (backend == Backend.QEMU) {
            QemuVmRuntime.tickVm(handle);
            return;
        }
        try {
            BRIDGE.tickVm(handle);
        } catch (Throwable throwable) {
            throw new IllegalStateException("JNI tickVm failed", throwable);
        }
    }

    public static void submitCommand(long handle, String command) {
        if (!available || handle == VmBridge.NULL_HANDLE) {
            return;
        }
        if (backend == Backend.TINY) {
            TinyVmRuntime.submitCommand(handle, command);
            return;
        }
        if (backend == Backend.QEMU) {
            QemuVmRuntime.submitCommand(handle, command);
            return;
        }
        try {
            BRIDGE.submitCommand(handle, command == null ? "" : command);
        } catch (Throwable throwable) {
            throw new IllegalStateException("JNI submitCommand failed", throwable);
        }
    }

    public static String diskImage(long handle) {
        if (backend == Backend.TINY) {
            return TinyVmRuntime.diskImage(handle);
        }
        if (backend == Backend.QEMU) {
            return QemuVmRuntime.diskImage(handle);
        }
        return readString(handle, BRIDGE::diskImage, "diskImage");
    }

    public static String terminalSnapshot(long handle) {
        if (backend == Backend.TINY) {
            return TinyVmRuntime.terminalSnapshot(handle);
        }
        if (backend == Backend.QEMU) {
            return QemuVmRuntime.terminalSnapshot(handle);
        }
        return readString(handle, BRIDGE::terminalSnapshot, "terminalSnapshot");
    }

    public static String framebufferSnapshot(long handle) {
        if (backend == Backend.TINY) {
            return TinyVmRuntime.framebufferSnapshot(handle);
        }
        if (backend == Backend.QEMU) {
            return QemuVmRuntime.framebufferSnapshot(handle);
        }
        return readString(handle, BRIDGE::framebufferSnapshot, "framebufferSnapshot");
    }

    public static String installedOs(long handle) {
        if (backend == Backend.TINY) {
            return TinyVmRuntime.installedOs(handle);
        }
        if (backend == Backend.QEMU) {
            return QemuVmRuntime.installedOs(handle);
        }
        return readString(handle, BRIDGE::installedOs, "installedOs");
    }

    public static boolean isHalted(long handle) {
        if (!available || handle == VmBridge.NULL_HANDLE) {
            return true;
        }
        if (backend == Backend.TINY) {
            return TinyVmRuntime.isHalted(handle);
        }
        if (backend == Backend.QEMU) {
            return QemuVmRuntime.isHalted(handle);
        }
        try {
            return BRIDGE.isHalted(handle);
        } catch (Throwable throwable) {
            throw new IllegalStateException("JNI isHalted failed", throwable);
        }
    }

    public static int bootCount(long handle) {
        if (!available || handle == VmBridge.NULL_HANDLE) {
            return 0;
        }
        if (backend == Backend.TINY) {
            return TinyVmRuntime.bootCount(handle);
        }
        if (backend == Backend.QEMU) {
            return QemuVmRuntime.bootCount(handle);
        }
        try {
            return BRIDGE.bootCount(handle);
        } catch (Throwable throwable) {
            throw new IllegalStateException("JNI bootCount failed", throwable);
        }
    }

    public static void destroyVm(long handle) {
        if (!available || handle == VmBridge.NULL_HANDLE) {
            return;
        }
        if (backend == Backend.TINY) {
            TinyVmRuntime.destroyVm(handle);
            return;
        }
        if (backend == Backend.QEMU) {
            QemuVmRuntime.destroyVm(handle);
            return;
        }
        try {
            BRIDGE.destroyVm(handle);
        } catch (Throwable throwable) {
            throw new IllegalStateException("JNI destroyVm failed", throwable);
        }
    }

    private static String readString(long handle, VmStringReader reader, String operation) {
        if (!available || handle == VmBridge.NULL_HANDLE) {
            return "";
        }
        try {
            String value = reader.read(handle);
            return value == null ? "" : value;
        } catch (Throwable throwable) {
            throw new IllegalStateException("JNI " + operation + " failed", throwable);
        }
    }

    @FunctionalInterface
    private interface VmStringReader {
        String read(long handle);
    }

    private static Backend configuredBackend() {
        String configured = System.getProperty(BACKEND_PROPERTY);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(BACKEND_ENV);
        }
        if (configured != null && configured.equalsIgnoreCase("native")) {
            return Backend.NATIVE;
        }
        if (configured != null && configured.equalsIgnoreCase("qemu")) {
            return Backend.QEMU;
        }
        return Backend.TINY;
    }

    private enum Backend {
        TINY,
        QEMU,
        NATIVE
    }
}

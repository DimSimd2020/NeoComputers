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
    private static final String SHA_256 = "SHA-256";
    private static final String BUNDLED_NATIVE_ROOT = "natives";
    private static final VmBridge BRIDGE = new JniVmBridge();
    private static volatile boolean available;

    private NativeVmRuntime() {
    }

    public static boolean initialize(String nativeLibraryName, Logger logger) {
        Objects.requireNonNull(nativeLibraryName, "nativeLibraryName");
        Objects.requireNonNull(logger, "logger");

        try {
            loadNativeLibrary(nativeLibraryName, logger);
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

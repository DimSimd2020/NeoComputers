package com.dimsimd.neocomputers.vm;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;

final class QemuVmRuntime {
    private static final String EXECUTABLE_PROPERTY = "neocomputers.qemu.executable";
    private static final String EXECUTABLE_ENV = "NEOCOMPUTERS_QEMU_EXECUTABLE";
    private static final String ISO_PROPERTY = "neocomputers.qemu.iso";
    private static final String ISO_ENV = "NEOCOMPUTERS_QEMU_ISO";
    private static final String DISK_PREFIX = "qemu-file:";
    private static final int MAX_LINES = 48;
    private static final AtomicLong NEXT_HANDLE = new AtomicLong(1L);
    private static final Map<Long, QemuVm> VMS = new ConcurrentHashMap<>();

    private static volatile String executable;
    private static volatile Path installerIso;

    private QemuVmRuntime() {
    }

    static boolean initialize(Logger logger) {
        executable = configuredExecutable();
        installerIso = configuredIso();

        try {
            Process process = new ProcessBuilder(executable, "--version")
                .redirectErrorStream(true)
                .start();
            boolean exited = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (!exited || process.exitValue() != 0) {
                logger.warn("QEMU executable check failed: {}", executable);
                return false;
            }
        } catch (IOException exception) {
            logger.warn("QEMU executable not available: {}", executable);
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while checking QEMU executable: {}", executable);
            return false;
        }

        if (installerIso == null) {
            logger.warn("QEMU backend enabled without installer ISO. Set -D{}=<alpine.iso> or {}.", ISO_PROPERTY, ISO_ENV);
        } else {
            logger.info("QEMU backend will attach installer ISO '{}'", installerIso);
        }
        logger.info("QEMU backend is active: {}", executable);
        return true;
    }

    static long createVm(int memorySizeMb, int diskSizeMb, String diskDescriptor) {
        Path diskPath = parseDiskPath(diskDescriptor);
        if (diskPath == null) {
            return VmBridge.NULL_HANDLE;
        }

        try {
            Files.createDirectories(diskPath.getParent());
            ensureRawDisk(diskPath, diskSizeMb);
            QemuMetadata metadata = readMetadata(diskPath);

            List<String> command = new ArrayList<>();
            command.add(executable);
            command.add("-m");
            command.add(Integer.toString(Math.max(64, memorySizeMb)));
            command.add("-smp");
            command.add("1");
            command.add("-machine");
            command.add("accel=tcg");
            command.add("-display");
            command.add("none");
            command.add("-serial");
            command.add("stdio");
            command.add("-monitor");
            command.add("none");
            command.add("-net");
            command.add("none");
            command.add("-no-reboot");
            command.add("-drive");
            command.add("file=" + diskPath + ",format=raw,if=virtio");
            if (installerIso != null) {
                command.add("-cdrom");
                command.add(installerIso.toString());
                command.add("-boot");
                command.add("order=d");
            }

            Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
            long handle = NEXT_HANDLE.getAndIncrement();
            QemuVm vm = new QemuVm(handle, process, diskPath, diskDescriptor, metadata.installedOs(), metadata.bootCount() + 1);
            if (installerIso == null) {
                vm.appendLine("QEMU started from disk. No installer ISO configured.");
                vm.appendLine("Set " + ISO_PROPERTY + " or " + ISO_ENV + " to boot Alpine installer.");
            } else {
                vm.appendLine("QEMU started with Alpine installer ISO attached.");
                vm.appendLine("Use setup-alpine inside the serial console to install to /dev/vda.");
            }
            VMS.put(handle, vm);
            vm.startReader();
            return handle;
        } catch (IOException exception) {
            return VmBridge.NULL_HANDLE;
        }
    }

    static void tickVm(long handle) {
        QemuVm vm = VMS.get(handle);
        if (vm != null) {
            vm.tick();
        }
    }

    static void submitCommand(long handle, String command) {
        QemuVm vm = VMS.get(handle);
        if (vm != null) {
            vm.submit(command);
        }
    }

    static String diskImage(long handle) {
        QemuVm vm = VMS.get(handle);
        return vm == null ? "" : vm.diskDescriptor();
    }

    static String terminalSnapshot(long handle) {
        QemuVm vm = VMS.get(handle);
        return vm == null ? "" : vm.terminalSnapshot();
    }

    static String framebufferSnapshot(long handle) {
        QemuVm vm = VMS.get(handle);
        return vm == null ? "" : vm.framebufferSnapshot();
    }

    static String installedOs(long handle) {
        QemuVm vm = VMS.get(handle);
        return vm == null ? "" : vm.installedOs();
    }

    static boolean isHalted(long handle) {
        QemuVm vm = VMS.get(handle);
        return vm == null || !vm.isAlive();
    }

    static int bootCount(long handle) {
        QemuVm vm = VMS.get(handle);
        return vm == null ? 0 : vm.bootCount();
    }

    static void destroyVm(long handle) {
        QemuVm vm = VMS.remove(handle);
        if (vm != null) {
            vm.destroy();
        }
    }

    static String diskPrefix() {
        return DISK_PREFIX;
    }

    private static String configuredExecutable() {
        String configured = System.getProperty(EXECUTABLE_PROPERTY);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(EXECUTABLE_ENV);
        }
        if (configured == null || configured.isBlank()) {
            return "qemu-system-x86_64";
        }
        return configured;
    }

    private static Path configuredIso() {
        String configured = System.getProperty(ISO_PROPERTY);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(ISO_ENV);
        }
        if (configured == null || configured.isBlank()) {
            return null;
        }
        Path path = Path.of(configured).toAbsolutePath().normalize();
        return Files.isRegularFile(path) ? path : null;
    }

    private static Path parseDiskPath(String diskDescriptor) {
        if (diskDescriptor == null || !diskDescriptor.startsWith(DISK_PREFIX)) {
            return null;
        }
        String rawPath = diskDescriptor.substring(DISK_PREFIX.length());
        if (rawPath.isBlank()) {
            return null;
        }
        return Path.of(rawPath).toAbsolutePath().normalize();
    }

    private static void ensureRawDisk(Path diskPath, int diskSizeMb) throws IOException {
        if (Files.isRegularFile(diskPath) && Files.size(diskPath) > 0) {
            return;
        }
        try (java.io.RandomAccessFile file = new java.io.RandomAccessFile(diskPath.toFile(), "rw")) {
            file.setLength(Math.max(1, diskSizeMb) * 1024L * 1024L);
        }
    }

    private static QemuMetadata readMetadata(Path diskPath) {
        Path metadataPath = metadataPath(diskPath);
        if (!Files.isRegularFile(metadataPath)) {
            return new QemuMetadata("", 0);
        }
        try {
            String installedOs = "";
            int bootCount = 0;
            for (String line : Files.readAllLines(metadataPath, StandardCharsets.UTF_8)) {
                int equals = line.indexOf('=');
                if (equals <= 0) {
                    continue;
                }
                String key = line.substring(0, equals);
                String value = line.substring(equals + 1);
                if (key.equals("installed_os")) {
                    installedOs = value;
                } else if (key.equals("boot_count")) {
                    bootCount = Integer.parseInt(value);
                }
            }
            return new QemuMetadata(installedOs, bootCount);
        } catch (IOException | NumberFormatException exception) {
            return new QemuMetadata("", 0);
        }
    }

    private static void writeMetadata(Path diskPath, String installedOs, int bootCount) throws IOException {
        Path metadataPath = metadataPath(diskPath);
        Files.writeString(
            metadataPath,
            "installed_os=" + (installedOs == null ? "" : installedOs) + "\nboot_count=" + Math.max(0, bootCount) + "\n",
            StandardCharsets.UTF_8
        );
    }

    private static Path metadataPath(Path diskPath) {
        return diskPath.resolveSibling(diskPath.getFileName() + ".meta");
    }

    private record QemuMetadata(String installedOs, int bootCount) {
    }

    private static final class QemuVm {
        private final long handle;
        private final Process process;
        private final Path diskPath;
        private final String diskDescriptor;
        private final List<String> lines = new ArrayList<>();
        private BufferedWriter writer;
        private int ticks;
        private int bootCount;
        private String installedOs;

        private QemuVm(long handle, Process process, Path diskPath, String diskDescriptor, String installedOs, int bootCount) {
            this.handle = handle;
            this.process = process;
            this.diskPath = diskPath;
            this.diskDescriptor = diskDescriptor;
            this.installedOs = installedOs == null ? "" : installedOs;
            this.bootCount = Math.max(1, bootCount);
            this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            saveMetadata();
        }

        private void startReader() {
            Thread reader = new Thread(() -> {
                try (BufferedReader output = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = output.readLine()) != null) {
                        appendLine(line);
                    }
                } catch (IOException exception) {
                    appendLine("QEMU serial reader stopped: " + exception.getMessage());
                }
            }, "NeoComputers-QEMU-" + handle);
            reader.setDaemon(true);
            reader.start();
        }

        private synchronized void submit(String command) {
            String trimmed = command == null ? "" : command.trim();
            if (trimmed.isEmpty()) {
                return;
            }

            appendLine("> " + trimmed);
            String normalized = trimmed.toLowerCase(Locale.ROOT);
            if (normalized.equals("install alpine")) {
                if (installerIso == null) {
                    appendLine("Install blocked: no Alpine ISO configured for QEMU.");
                } else {
                    appendLine("Alpine installer is running in QEMU. Complete setup-alpine on serial console.");
                }
            }
            if (normalized.equals("mark-installed alpine") || normalized.equals("mark-installed neolinux")) {
                installedOs = "Alpine Linux (QEMU)";
                appendLine("Marked disk as installed after guest-side setup.");
                saveMetadata();
                return;
            }

            try {
                writer.write(trimmed);
                writer.newLine();
                writer.flush();
            } catch (IOException exception) {
                appendLine("QEMU input failed: " + exception.getMessage());
            }
        }

        private synchronized void tick() {
            ticks++;
        }

        private synchronized String diskDescriptor() {
            return diskDescriptor;
        }

        private synchronized String terminalSnapshot() {
            return String.join("\n", lines);
        }

        private synchronized String framebufferSnapshot() {
            List<String> frame = new ArrayList<>();
            frame.add("+--------------------------------------+");
            frame.add("| QEMU x86_64                          |");
            frame.add("| boot=" + bootCount + " ticks=" + ticks + " alive=" + isAlive());
            int first = Math.max(0, lines.size() - 5);
            for (int i = first; i < lines.size(); i++) {
                frame.add("| " + trimForFrame(lines.get(i)));
            }
            frame.add("+--------------------------------------+");
            return String.join("\n", frame);
        }

        private synchronized String installedOs() {
            return installedOs;
        }

        private synchronized int bootCount() {
            return bootCount;
        }

        private boolean isAlive() {
            return process.isAlive();
        }

        private synchronized void appendLine(String line) {
            lines.add(line == null ? "" : line);
            if (lines.size() > MAX_LINES) {
                lines.remove(0);
            }
        }

        private synchronized void saveMetadata() {
            try {
                writeMetadata(diskPath, installedOs, bootCount);
            } catch (IOException exception) {
                appendLine("QEMU metadata write failed: " + exception.getMessage());
            }
        }

        private void destroy() {
            process.destroy();
            try {
                if (!process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }

        private static String trimForFrame(String line) {
            if (line.length() <= 36) {
                return line;
            }
            return line.substring(0, 33) + "...";
        }
    }
}

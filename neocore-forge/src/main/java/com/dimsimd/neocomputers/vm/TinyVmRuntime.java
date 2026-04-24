package com.dimsimd.neocomputers.vm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;

final class TinyVmRuntime {
    private static final String DISK_MAGIC = "NTVM1";
    private static final String OS_NAME = "NeoTiny 0.1";
    private static final int MAX_LINES = 48;
    private static final int MAX_FRAME_LINES = 10;
    private static final AtomicLong NEXT_HANDLE = new AtomicLong(1L);
    private static final Map<Long, TinyVm> VMS = new ConcurrentHashMap<>();

    private TinyVmRuntime() {
    }

    static boolean initialize(Logger logger) {
        logger.info("NeoComputers Tiny VM backend is active");
        return true;
    }

    static long createVm(int memorySizeMb, int diskSizeMb, String diskImage) {
        long handle = NEXT_HANDLE.getAndIncrement();
        TinyVm vm = TinyVm.fromDiskImage(handle, Math.max(1, memorySizeMb), Math.max(1, diskSizeMb), diskImage);
        vm.boot();
        VMS.put(handle, vm);
        return handle;
    }

    static void tickVm(long handle) {
        TinyVm vm = VMS.get(handle);
        if (vm != null) {
            vm.tick();
        }
    }

    static void submitCommand(long handle, String command) {
        TinyVm vm = VMS.get(handle);
        if (vm != null) {
            vm.submit(command);
        }
    }

    static String diskImage(long handle) {
        TinyVm vm = VMS.get(handle);
        return vm == null ? "" : vm.diskImage();
    }

    static String terminalSnapshot(long handle) {
        TinyVm vm = VMS.get(handle);
        return vm == null ? "" : vm.terminalSnapshot();
    }

    static String framebufferSnapshot(long handle) {
        TinyVm vm = VMS.get(handle);
        return vm == null ? "" : vm.framebufferSnapshot();
    }

    static String installedOs(long handle) {
        TinyVm vm = VMS.get(handle);
        return vm == null ? "" : vm.installedOs();
    }

    static boolean isHalted(long handle) {
        TinyVm vm = VMS.get(handle);
        return vm == null || vm.isHalted();
    }

    static int bootCount(long handle) {
        TinyVm vm = VMS.get(handle);
        return vm == null ? 0 : vm.bootCount();
    }

    static void destroyVm(long handle) {
        VMS.remove(handle);
    }

    private static final class TinyVm {
        private final long handle;
        private final int memorySizeMb;
        private final int diskSizeMb;
        private final List<String> terminalLines = new ArrayList<>();
        private final Map<String, String> files = new LinkedHashMap<>();
        private String installedOs = "";
        private int bootCount;
        private int ticks;
        private boolean halted;

        private TinyVm(long handle, int memorySizeMb, int diskSizeMb) {
            this.handle = handle;
            this.memorySizeMb = memorySizeMb;
            this.diskSizeMb = diskSizeMb;
        }

        private static TinyVm fromDiskImage(long handle, int memorySizeMb, int diskSizeMb, String diskImage) {
            TinyVm vm = new TinyVm(handle, memorySizeMb, diskSizeMb);
            if (diskImage == null || diskImage.isBlank()) {
                return vm;
            }

            String[] lines = diskImage.split("\\R");
            if (lines.length == 0 || !lines[0].equals(DISK_MAGIC)) {
                return vm;
            }

            for (int i = 1; i < lines.length; i++) {
                String line = lines[i];
                int equals = line.indexOf('=');
                if (equals <= 0) {
                    continue;
                }
                String key = line.substring(0, equals);
                String value = line.substring(equals + 1);
                switch (key) {
                    case "os" -> vm.installedOs = decode(value);
                    case "boot" -> vm.bootCount = parseInt(value);
                    case "ticks" -> vm.ticks = parseInt(value);
                    case "halted" -> vm.halted = value.equals("1");
                    case "line" -> vm.appendLine(decode(value));
                    case "file" -> vm.readFileRecord(value);
                    default -> {
                    }
                }
            }
            return vm;
        }

        private void boot() {
            halted = false;
            bootCount++;
            if (installedOs.isBlank()) {
                appendLine("NeoBIOS Tiny VM");
                appendLine("No OS installed. Type: install tiny");
            } else {
                appendLine(installedOs + " boot #" + bootCount);
                appendLine("Type help for commands.");
            }
        }

        private void tick() {
            if (!halted) {
                ticks++;
            }
        }

        private void submit(String command) {
            String trimmed = command == null ? "" : command.trim();
            if (trimmed.isEmpty()) {
                return;
            }

            if (halted && !trimmed.equalsIgnoreCase("reboot")) {
                appendLine("System halted. Type reboot or power cycle.");
                return;
            }

            String normalized = trimmed.toLowerCase(Locale.ROOT);
            if (normalized.equals("clear")) {
                terminalLines.clear();
                return;
            }

            appendLine("> " + trimmed);
            if (normalized.equals("install tiny") || normalized.equals("install neotiny") || normalized.equals("install core")) {
                installTiny();
            } else if (normalized.equals("help")) {
                appendLine("commands: help, install tiny, uname, ls, cat, write, rm, run, mem, df, uptime, clear, reboot, shutdown");
            } else if (normalized.equals("uname")) {
                appendLine(installedOs.isBlank() ? "NeoBIOS tiny-vm" : installedOs + " tiny-vm");
            } else if (normalized.equals("ls")) {
                appendLine(files.isEmpty() ? "(empty)" : String.join(" ", files.keySet()));
            } else if (normalized.startsWith("cat ")) {
                cat(trimmed.substring(4).trim());
            } else if (normalized.startsWith("write ")) {
                write(trimmed.substring(6));
            } else if (normalized.startsWith("rm ")) {
                remove(trimmed.substring(3).trim());
            } else if (normalized.startsWith("run ")) {
                runProgram(trimmed.substring(4).trim());
            } else if (normalized.equals("mem")) {
                appendLine("mem=" + memorySizeMb + "MB backend=tiny");
            } else if (normalized.equals("df")) {
                appendLine("/dev/card0 " + diskSizeMb + "MB ntfsim");
            } else if (normalized.equals("uptime")) {
                appendLine("ticks=" + ticks + " boot=" + bootCount);
            } else if (normalized.equals("reboot")) {
                appendLine("Rebooting.");
                boot();
            } else if (normalized.equals("shutdown") || normalized.equals("poweroff")) {
                appendLine("System halted.");
                halted = true;
            } else {
                appendLine("command not found: " + trimmed);
            }
        }

        private void installTiny() {
            installedOs = OS_NAME;
            files.clear();
            files.put("/etc/motd", "Welcome to NeoTiny.");
            files.put("/bin/hello", "PRINT Hello from NeoTiny bytecode\nPRINT This program is stored on the memory card");
            files.put("/home/readme", "NeoTiny is a compact VM runtime for NeoComputers.");
            appendLine("Formatting /dev/card0...");
            appendLine("Installing " + OS_NAME + "...");
            appendLine("Installed. Try: run /bin/hello");
        }

        private void cat(String path) {
            String normalizedPath = normalizePath(path);
            String content = files.get(normalizedPath);
            if (content == null) {
                appendLine("cat: " + normalizedPath + ": no such file");
                return;
            }
            for (String line : content.split("\\R", -1)) {
                appendLine(line);
            }
        }

        private void write(String arguments) {
            int space = arguments.indexOf(' ');
            if (space <= 0) {
                appendLine("usage: write <path> <text>");
                return;
            }
            String path = normalizePath(arguments.substring(0, space));
            String text = arguments.substring(space + 1);
            files.put(path, text);
            appendLine("wrote " + path);
        }

        private void remove(String path) {
            String normalizedPath = normalizePath(path);
            if (files.remove(normalizedPath) == null) {
                appendLine("rm: " + normalizedPath + ": no such file");
            } else {
                appendLine("removed " + normalizedPath);
            }
        }

        private void runProgram(String path) {
            String normalizedPath = normalizePath(path);
            String program = files.get(normalizedPath);
            if (program == null) {
                appendLine("run: " + normalizedPath + ": no such program");
                return;
            }

            appendLine("exec " + normalizedPath);
            int executed = 0;
            for (String rawInstruction : program.split("\\R")) {
                String instruction = rawInstruction.trim();
                if (instruction.isEmpty() || instruction.startsWith("#")) {
                    continue;
                }
                executeInstruction(instruction);
                executed++;
                if (executed >= 64) {
                    appendLine("program stopped: instruction budget exceeded");
                    break;
                }
            }
        }

        private void executeInstruction(String instruction) {
            String normalized = instruction.toUpperCase(Locale.ROOT);
            if (normalized.startsWith("PRINT ")) {
                appendLine(instruction.substring(6));
            } else if (normalized.startsWith("WRITE ")) {
                write(instruction.substring(6));
            } else if (normalized.equals("TICKS")) {
                appendLine(Integer.toString(ticks));
            } else {
                appendLine("bad instruction: " + instruction);
            }
        }

        private String diskImage() {
            StringBuilder builder = new StringBuilder();
            builder.append(DISK_MAGIC).append('\n');
            builder.append("os=").append(encode(installedOs)).append('\n');
            builder.append("boot=").append(bootCount).append('\n');
            builder.append("ticks=").append(ticks).append('\n');
            builder.append("halted=").append(halted ? "1" : "0").append('\n');
            for (String line : terminalLines) {
                builder.append("line=").append(encode(line)).append('\n');
            }
            for (Map.Entry<String, String> file : files.entrySet()) {
                builder.append("file=").append(encode(file.getKey())).append(':').append(encode(file.getValue())).append('\n');
            }
            return builder.toString();
        }

        private String terminalSnapshot() {
            return String.join("\n", terminalLines);
        }

        private String framebufferSnapshot() {
            List<String> frame = new ArrayList<>();
            frame.add("+--------------------------------------+");
            frame.add("| NeoTiny VM                           |");
            frame.add("| os=" + (installedOs.isBlank() ? "none" : installedOs));
            frame.add("| boot=" + bootCount + " ticks=" + ticks + " files=" + files.size());
            int first = Math.max(0, terminalLines.size() - MAX_FRAME_LINES);
            for (int i = first; i < terminalLines.size(); i++) {
                frame.add("| " + trimForFrame(terminalLines.get(i)));
            }
            frame.add("+--------------------------------------+");
            return String.join("\n", frame);
        }

        private String installedOs() {
            return installedOs;
        }

        private int bootCount() {
            return bootCount;
        }

        private boolean isHalted() {
            return halted;
        }

        private void appendLine(String line) {
            terminalLines.add(line == null ? "" : line);
            if (terminalLines.size() > MAX_LINES) {
                terminalLines.remove(0);
            }
        }

        private void readFileRecord(String value) {
            int separator = value.indexOf(':');
            if (separator <= 0) {
                return;
            }
            String path = decode(value.substring(0, separator));
            String content = decode(value.substring(separator + 1));
            files.put(normalizePath(path), content);
        }

        private static String normalizePath(String path) {
            if (path == null || path.isBlank()) {
                return "/";
            }
            String normalized = path.trim().replace('\\', '/');
            return normalized.startsWith("/") ? normalized : "/" + normalized;
        }

        private static String trimForFrame(String line) {
            if (line.length() <= 36) {
                return line;
            }
            return line.substring(0, 33) + "...";
        }
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static String encode(String value) {
        StringBuilder builder = new StringBuilder();
        for (byte b : value.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            int normalized = b & 0xff;
            if (normalized < 16) {
                builder.append('0');
            }
            builder.append(Integer.toHexString(normalized));
        }
        return builder.toString();
    }

    private static String decode(String value) {
        byte[] bytes = new byte[value.length() / 2];
        for (int i = 0; i + 1 < value.length(); i += 2) {
            int high = Character.digit(value.charAt(i), 16);
            int low = Character.digit(value.charAt(i + 1), 16);
            if (high < 0 || low < 0) {
                return "";
            }
            bytes[i / 2] = (byte) ((high << 4) | low);
        }
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }
}

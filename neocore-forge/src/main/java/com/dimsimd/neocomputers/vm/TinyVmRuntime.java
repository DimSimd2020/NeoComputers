package com.dimsimd.neocomputers.vm;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;

final class TinyVmRuntime {
    private static final String DISK_MAGIC = "NTVM2";
    private static final String LEGACY_DISK_MAGIC = "NTVM1";
    private static final String OS_NAME = "NeoTiny 0.2";
    private static final int MAX_LINES = 80;
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
        return createVm(memorySizeMb, diskSizeMb, diskImage, false);
    }

    static long createVm(int memorySizeMb, int diskSizeMb, String diskImage, boolean networkEnabled) {
        long handle = NEXT_HANDLE.getAndIncrement();
        TinyVm vm = TinyVm.fromDiskImage(handle, Math.max(1, memorySizeMb), Math.max(1, diskSizeMb), diskImage);
        vm.setNetworkEnabled(networkEnabled);
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
        private final Map<String, FsEntry> fs = new LinkedHashMap<>();
        private final Map<Integer, ProcessInfo> processes = new LinkedHashMap<>();
        private String installedOs = "";
        private String cwd = "/";
        private int bootCount;
        private int ticks;
        private int nextPid = 100;
        private boolean halted;
        private boolean networkEnabled;

        private TinyVm(long handle, int memorySizeMb, int diskSizeMb) {
            this.handle = handle;
            this.memorySizeMb = memorySizeMb;
            this.diskSizeMb = diskSizeMb;
            mkdirInternal("/");
        }

        private static TinyVm fromDiskImage(long handle, int memorySizeMb, int diskSizeMb, String diskImage) {
            TinyVm vm = new TinyVm(handle, memorySizeMb, diskSizeMb);
            if (diskImage == null || diskImage.isBlank()) {
                return vm;
            }

            String[] lines = diskImage.split("\\R");
            if (lines.length == 0) {
                return vm;
            }
            boolean legacy = lines[0].equals(LEGACY_DISK_MAGIC);
            if (!legacy && !lines[0].equals(DISK_MAGIC)) {
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
                    case "cwd" -> vm.cwd = vm.normalizePath(decode(value));
                    case "boot" -> vm.bootCount = parseInt(value);
                    case "ticks" -> vm.ticks = parseInt(value);
                    case "halted" -> vm.halted = value.equals("1");
                    case "pid" -> vm.nextPid = Math.max(100, parseInt(value));
                    case "line" -> vm.appendLine(decode(value));
                    case "file" -> {
                        if (legacy) {
                            vm.readLegacyFileRecord(value);
                        } else {
                            vm.readFileRecord(value);
                        }
                    }
                    case "dir" -> vm.mkdirInternal(decode(value));
                    default -> {
                    }
                }
            }
            if (!vm.fs.containsKey(vm.cwd) || !vm.fs.get(vm.cwd).directory()) {
                vm.cwd = "/";
            }
            return vm;
        }

        private void setNetworkEnabled(boolean networkEnabled) {
            this.networkEnabled = networkEnabled;
        }

        private void boot() {
            halted = false;
            bootCount++;
            ensureProcess(1, "init", "S");
            ensureProcess(2, "kthreadd", "S");
            ensureProcess(nextPid++, "shell", "R");
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

            List<String> tokens = tokenize(trimmed);
            if (tokens.isEmpty()) {
                return;
            }
            String commandName = tokens.get(0).toLowerCase(Locale.ROOT);
            if (commandName.equals("clear")) {
                terminalLines.clear();
                return;
            }

            appendLine("> " + trimmed);
            dispatch(commandName, tokens, trimmed);
        }

        private void dispatch(String commandName, List<String> tokens, String rawCommand) {
            switch (commandName) {
                case "install" -> installCommand(tokens);
                case "help" -> appendLine("commands: ls cd pwd mkdir rm cp mv touch find cat less head tail grep ps top htop kill df du free uname ping chmod sudo echo lua run write mem uptime clear reboot shutdown");
                case "ls" -> ls(tokens);
                case "cd" -> cd(tokens);
                case "pwd" -> appendLine(cwd);
                case "mkdir" -> mkdir(tokens);
                case "rm" -> rm(tokens);
                case "cp" -> cp(tokens);
                case "mv" -> mv(tokens);
                case "touch" -> touch(tokens);
                case "find" -> find(tokens);
                case "cat" -> cat(tokens);
                case "less" -> less(tokens);
                case "head" -> head(tokens);
                case "tail" -> tail(tokens);
                case "grep" -> grep(tokens);
                case "ps" -> ps();
                case "top", "htop" -> top();
                case "kill" -> kill(tokens);
                case "df" -> df();
                case "du" -> du(tokens);
                case "free" -> free();
                case "uname" -> uname(tokens);
                case "ping" -> ping(tokens);
                case "chmod" -> chmod(tokens);
                case "sudo" -> sudo(tokens, rawCommand);
                case "echo" -> echo(rawCommand);
                case "lua" -> lua(tokens);
                case "run" -> runProgram(tokens);
                case "write" -> writeCompat(tokens, rawCommand);
                case "mem" -> appendLine("mem=" + memorySizeMb + "MB backend=tiny");
                case "uptime" -> appendLine("ticks=" + ticks + " boot=" + bootCount);
                case "reboot" -> {
                    appendLine("Rebooting.");
                    boot();
                }
                case "shutdown", "poweroff" -> {
                    appendLine("System halted.");
                    halted = true;
                }
                default -> appendLine(commandName + ": command not found");
            }
        }

        private void installCommand(List<String> tokens) {
            String target = tokens.size() >= 2 ? tokens.get(1).toLowerCase(Locale.ROOT) : "tiny";
            if (!target.equals("tiny") && !target.equals("neotiny") && !target.equals("core")) {
                appendLine("install: unknown target " + target);
                return;
            }
            installedOs = OS_NAME;
            fs.clear();
            mkdirInternal("/");
            mkdirInternal("/bin");
            mkdirInternal("/etc");
            mkdirInternal("/home");
            mkdirInternal("/tmp");
            putFile("/etc/motd", "Welcome to NeoTiny.", "rw-r--r--");
            putFile("/bin/hello", "PRINT Hello from NeoTiny bytecode\nPRINT This program is stored on the memory card", "rwxr-xr-x");
            putFile("/home/readme", "NeoTiny is a compact VM runtime for NeoComputers.", "rw-r--r--");
            putFile("/home/demo.lua", "print(\"hello from lua\")\nx = 2 + 3\nprint(x)", "rw-r--r--");
            cwd = "/home";
            appendLine("Formatting /dev/card0...");
            appendLine("Installing " + OS_NAME + "...");
            appendLine("Installed. Try: run /bin/hello or lua /home/demo.lua");
        }

        private void ls(List<String> tokens) {
            String path = tokens.size() >= 2 ? normalizePath(tokens.get(1)) : cwd;
            FsEntry entry = fs.get(path);
            if (entry == null) {
                appendLine("ls: cannot access '" + path + "': no such file or directory");
                return;
            }
            if (!entry.directory()) {
                appendLine(path);
                return;
            }
            List<String> children = directChildren(path);
            appendLine(children.isEmpty() ? "(empty)" : String.join(" ", children));
        }

        private void cd(List<String> tokens) {
            String path = tokens.size() >= 2 ? normalizePath(tokens.get(1)) : "/";
            FsEntry entry = fs.get(path);
            if (entry == null || !entry.directory()) {
                appendLine("cd: " + path + ": no such directory");
                return;
            }
            cwd = path;
        }

        private void mkdir(List<String> tokens) {
            if (tokens.size() < 2) {
                appendLine("mkdir: missing operand");
                return;
            }
            for (int i = 1; i < tokens.size(); i++) {
                String path = normalizePath(tokens.get(i));
                ensureParentDirectory(path);
                mkdirInternal(path);
            }
        }

        private void rm(List<String> tokens) {
            if (tokens.size() < 2) {
                appendLine("rm: missing operand");
                return;
            }
            boolean recursive = tokens.contains("-r") || tokens.contains("-rf") || tokens.contains("-fr");
            for (int i = 1; i < tokens.size(); i++) {
                String token = tokens.get(i);
                if (token.startsWith("-")) {
                    continue;
                }
                String path = normalizePath(token);
                FsEntry entry = fs.get(path);
                if (entry == null) {
                    appendLine("rm: cannot remove '" + path + "': no such file or directory");
                } else if (entry.directory() && !recursive && !directChildren(path).isEmpty()) {
                    appendLine("rm: cannot remove '" + path + "': is a directory");
                } else {
                    removeTree(path);
                }
            }
        }

        private void cp(List<String> tokens) {
            if (tokens.size() != 3) {
                appendLine("usage: cp <src> <dst>");
                return;
            }
            String src = normalizePath(tokens.get(1));
            String dst = normalizePath(tokens.get(2));
            FsEntry entry = fs.get(src);
            if (entry == null || entry.directory()) {
                appendLine("cp: cannot copy '" + src + "'");
                return;
            }
            ensureParentDirectory(dst);
            putFile(dst, entry.content(), entry.permissions());
        }

        private void mv(List<String> tokens) {
            if (tokens.size() != 3) {
                appendLine("usage: mv <src> <dst>");
                return;
            }
            String src = normalizePath(tokens.get(1));
            String dst = normalizePath(tokens.get(2));
            FsEntry entry = fs.remove(src);
            if (entry == null) {
                appendLine("mv: cannot stat '" + src + "'");
                return;
            }
            ensureParentDirectory(dst);
            fs.put(dst, entry);
        }

        private void touch(List<String> tokens) {
            if (tokens.size() < 2) {
                appendLine("touch: missing file operand");
                return;
            }
            for (int i = 1; i < tokens.size(); i++) {
                String path = normalizePath(tokens.get(i));
                ensureParentDirectory(path);
                fs.putIfAbsent(path, FsEntry.file("", "rw-r--r--"));
            }
        }

        private void find(List<String> tokens) {
            String base = tokens.size() >= 2 && !tokens.get(1).startsWith("-") ? normalizePath(tokens.get(1)) : cwd;
            String nameFilter = null;
            for (int i = 1; i + 1 < tokens.size(); i++) {
                if (tokens.get(i).equals("-name")) {
                    nameFilter = tokens.get(i + 1).replace("*", "");
                }
            }
            for (String path : sortedPaths()) {
                if (!path.equals(base) && !path.startsWith(base.endsWith("/") ? base : base + "/")) {
                    continue;
                }
                if (nameFilter == null || fileName(path).contains(nameFilter)) {
                    appendLine(path);
                }
            }
        }

        private void cat(List<String> tokens) {
            if (tokens.size() < 2) {
                appendLine("cat: missing file operand");
                return;
            }
            for (int i = 1; i < tokens.size(); i++) {
                FsEntry entry = fileEntry(tokens.get(i), "cat");
                if (entry != null) {
                    appendContentLines(entry.content(), 0, Integer.MAX_VALUE);
                }
            }
        }

        private void less(List<String> tokens) {
            if (tokens.size() != 2) {
                appendLine("usage: less <file>");
                return;
            }
            FsEntry entry = fileEntry(tokens.get(1), "less");
            if (entry != null) {
                appendContentLines(entry.content(), 0, 12);
                appendLine("(less: showing first 12 lines)");
            }
        }

        private void head(List<String> tokens) {
            int count = 10;
            String path = null;
            for (int i = 1; i < tokens.size(); i++) {
                if (tokens.get(i).equals("-n") && i + 1 < tokens.size()) {
                    count = Math.max(0, parseInt(tokens.get(++i)));
                } else {
                    path = tokens.get(i);
                }
            }
            FsEntry entry = fileEntry(path, "head");
            if (entry != null) {
                appendContentLines(entry.content(), 0, count);
            }
        }

        private void tail(List<String> tokens) {
            int count = 10;
            boolean follow = false;
            String path = null;
            for (int i = 1; i < tokens.size(); i++) {
                if (tokens.get(i).equals("-f")) {
                    follow = true;
                } else if (tokens.get(i).equals("-n") && i + 1 < tokens.size()) {
                    count = Math.max(0, parseInt(tokens.get(++i)));
                } else {
                    path = tokens.get(i);
                }
            }
            FsEntry entry = fileEntry(path, "tail");
            if (entry == null) {
                return;
            }
            String[] lines = entry.content().split("\\R", -1);
            appendContentLines(entry.content(), Math.max(0, lines.length - count), Integer.MAX_VALUE);
            if (follow) {
                appendLine("(tail -f: live follow is simulated)");
            }
        }

        private void grep(List<String> tokens) {
            if (tokens.size() < 3) {
                appendLine("usage: grep <pattern> <file>");
                return;
            }
            String pattern = tokens.get(1);
            for (int i = 2; i < tokens.size(); i++) {
                String path = normalizePath(tokens.get(i));
                FsEntry entry = fileEntry(path, "grep");
                if (entry == null) {
                    continue;
                }
                for (String line : entry.content().split("\\R", -1)) {
                    if (line.contains(pattern)) {
                        appendLine(path + ": " + line);
                    }
                }
            }
        }

        private void ps() {
            appendLine(" PID STAT CMD");
            for (ProcessInfo process : processes.values()) {
                appendLine(String.format(Locale.ROOT, "%4d %s    %s", process.pid(), process.state(), process.command()));
            }
        }

        private void top() {
            appendLine("NeoTiny top - ticks " + ticks + ", mem " + memorySizeMb + "MB");
            ps();
        }

        private void kill(List<String> tokens) {
            if (tokens.size() != 2) {
                appendLine("usage: kill <pid>");
                return;
            }
            int pid = parseInt(tokens.get(1));
            if (pid <= 2) {
                appendLine("kill: refusing to kill system process " + pid);
            } else if (processes.remove(pid) == null) {
                appendLine("kill: " + pid + ": no such process");
            } else {
                appendLine("killed " + pid);
            }
        }

        private void df() {
            int used = usedBytes();
            int capacity = diskSizeMb * 1024 * 1024;
            appendLine("Filesystem 1B-blocks Used Available Mounted on");
            appendLine("/dev/card0 " + capacity + " " + used + " " + Math.max(0, capacity - used) + " /");
        }

        private void du(List<String> tokens) {
            String path = tokens.size() >= 2 ? normalizePath(tokens.get(1)) : cwd;
            int size = subtreeBytes(path);
            appendLine(size + "\t" + path);
        }

        private void free() {
            int used = Math.min(memorySizeMb, 16 + processes.size() * 2);
            appendLine("              total        used        free");
            appendLine(String.format(Locale.ROOT, "Mem:%14d%12d%12d", memorySizeMb, used, Math.max(0, memorySizeMb - used)));
        }

        private void uname(List<String> tokens) {
            if (tokens.contains("-a")) {
                appendLine("NeoTiny neocomputer 0.2 tinyvm " + memorySizeMb + "MB");
            } else {
                appendLine("NeoTiny");
            }
        }

        private void ping(List<String> tokens) {
            if (tokens.size() < 2) {
                appendLine("usage: ping <host>");
                return;
            }
            if (!networkEnabled) {
                appendLine("ping: network unreachable (install network card)");
                return;
            }
            String host = pingHost(tokens);
            appendLine("PING " + host + " 56(84) bytes of data.");
            long started = System.nanoTime();
            try {
                boolean reachable = InetAddress.getByName(host).isReachable(1000);
                long elapsedMs = Math.max(1L, (System.nanoTime() - started) / 1_000_000L);
                if (reachable) {
                    appendLine("64 bytes from " + host + ": icmp_seq=1 ttl=64 time=" + elapsedMs + " ms");
                    appendLine("--- " + host + " ping statistics ---");
                    appendLine("1 packets transmitted, 1 received, 0% packet loss");
                } else {
                    appendLine("--- " + host + " ping statistics ---");
                    appendLine("1 packets transmitted, 0 received, 100% packet loss");
                }
            } catch (IOException exception) {
                appendLine("ping: " + exception.getMessage());
            }
        }

        private String pingHost(List<String> tokens) {
            for (int i = 1; i < tokens.size(); i++) {
                String token = tokens.get(i);
                if ("-c".equals(token) || "-W".equals(token) || "-w".equals(token)) {
                    i++;
                    continue;
                }
                if (!token.startsWith("-")) {
                    return token;
                }
            }
            return tokens.get(tokens.size() - 1);
        }

        private void chmod(List<String> tokens) {
            if (tokens.size() != 3) {
                appendLine("usage: chmod <mode> <file>");
                return;
            }
            String path = normalizePath(tokens.get(2));
            FsEntry entry = fs.get(path);
            if (entry == null) {
                appendLine("chmod: cannot access '" + path + "'");
                return;
            }
            fs.put(path, entry.withPermissions(modeToPermissions(tokens.get(1))));
        }

        private void sudo(List<String> tokens, String rawCommand) {
            if (tokens.size() < 2) {
                appendLine("usage: sudo <command>");
                return;
            }
            String nested = rawCommand.substring(rawCommand.indexOf(' ') + 1).trim();
            appendLine("[sudo] root privileges granted");
            List<String> nestedTokens = tokenize(nested);
            if (!nestedTokens.isEmpty()) {
                dispatch(nestedTokens.get(0).toLowerCase(Locale.ROOT), nestedTokens, nested);
            }
        }

        private void echo(String rawCommand) {
            String text = rawCommand.length() <= 4 ? "" : rawCommand.substring(4).trim();
            int appendRedirect = text.indexOf(">>");
            int writeRedirect = text.indexOf('>');
            if (appendRedirect >= 0) {
                redirectEcho(text.substring(0, appendRedirect).trim(), text.substring(appendRedirect + 2).trim(), true);
            } else if (writeRedirect >= 0) {
                redirectEcho(text.substring(0, writeRedirect).trim(), text.substring(writeRedirect + 1).trim(), false);
            } else {
                appendLine(unquote(text));
            }
        }

        private void lua(List<String> tokens) {
            if (tokens.size() != 2) {
                appendLine("usage: lua <file>");
                return;
            }
            FsEntry entry = fileEntry(tokens.get(1), "lua");
            if (entry != null) {
                new LuaSubset(entry.content()).execute();
            }
        }

        private void runProgram(List<String> tokens) {
            if (tokens.size() != 2) {
                appendLine("usage: run <file>");
                return;
            }
            String path = normalizePath(tokens.get(1));
            FsEntry entry = fileEntry(path, "run");
            if (entry == null) {
                return;
            }
            appendLine("exec " + path);
            int executed = 0;
            for (String rawInstruction : entry.content().split("\\R")) {
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

        private void writeCompat(List<String> tokens, String rawCommand) {
            if (tokens.size() < 3) {
                appendLine("usage: write <path> <text>");
                return;
            }
            int firstSpace = rawCommand.indexOf(' ');
            int secondSpace = rawCommand.indexOf(' ', firstSpace + 1);
            if (secondSpace <= 0) {
                appendLine("usage: write <path> <text>");
                return;
            }
            String path = normalizePath(rawCommand.substring(firstSpace + 1, secondSpace));
            String text = rawCommand.substring(secondSpace + 1);
            putFile(path, text, "rw-r--r--");
            appendLine("wrote " + path);
        }

        private void executeInstruction(String instruction) {
            String normalized = instruction.toUpperCase(Locale.ROOT);
            if (normalized.startsWith("PRINT ")) {
                appendLine(instruction.substring(6));
            } else if (normalized.startsWith("WRITE ")) {
                writeCompat(tokenize("write " + instruction.substring(6)), "write " + instruction.substring(6));
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
            builder.append("cwd=").append(encode(cwd)).append('\n');
            builder.append("boot=").append(bootCount).append('\n');
            builder.append("ticks=").append(ticks).append('\n');
            builder.append("pid=").append(nextPid).append('\n');
            builder.append("halted=").append(halted ? "1" : "0").append('\n');
            for (String line : terminalLines) {
                builder.append("line=").append(encode(line)).append('\n');
            }
            for (Map.Entry<String, FsEntry> entry : fs.entrySet()) {
                if (entry.getKey().equals("/")) {
                    continue;
                }
                if (entry.getValue().directory()) {
                    builder.append("dir=").append(encode(entry.getKey())).append('\n');
                } else {
                    builder.append("file=")
                        .append(encode(entry.getKey())).append(':')
                        .append(encode(entry.getValue().permissions())).append(':')
                        .append(encode(entry.getValue().content())).append('\n');
                }
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
            frame.add("| cwd=" + cwd + " net=" + (networkEnabled ? "up" : "down"));
            frame.add("| boot=" + bootCount + " ticks=" + ticks + " files=" + fs.size());
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

        private void appendContentLines(String content, int firstLine, int maxLines) {
            String[] lines = content.split("\\R", -1);
            int emitted = 0;
            for (int i = Math.max(0, firstLine); i < lines.length && emitted < maxLines; i++) {
                appendLine(lines[i]);
                emitted++;
            }
        }

        private FsEntry fileEntry(String rawPath, String commandName) {
            if (rawPath == null || rawPath.isBlank()) {
                appendLine(commandName + ": missing file operand");
                return null;
            }
            String path = normalizePath(rawPath);
            FsEntry entry = fs.get(path);
            if (entry == null) {
                appendLine(commandName + ": " + path + ": no such file");
                return null;
            }
            if (entry.directory()) {
                appendLine(commandName + ": " + path + ": is a directory");
                return null;
            }
            return entry;
        }

        private void putFile(String path, String content, String permissions) {
            String normalizedPath = normalizePath(path);
            ensureParentDirectory(normalizedPath);
            fs.put(normalizedPath, FsEntry.file(content, permissions));
        }

        private void mkdirInternal(String path) {
            fs.put(normalizePath(path), FsEntry.directoryEntry());
        }

        private void ensureParentDirectory(String path) {
            int slash = path.lastIndexOf('/');
            if (slash <= 0) {
                mkdirInternal("/");
                return;
            }
            String parent = path.substring(0, slash);
            if (parent.isBlank()) {
                parent = "/";
            }
            if (!fs.containsKey(parent)) {
                ensureParentDirectory(parent);
                mkdirInternal(parent);
            }
        }

        private void removeTree(String path) {
            List<String> toRemove = new ArrayList<>();
            for (String candidate : fs.keySet()) {
                if (candidate.equals(path) || candidate.startsWith(path.endsWith("/") ? path : path + "/")) {
                    toRemove.add(candidate);
                }
            }
            for (String candidate : toRemove) {
                if (!candidate.equals("/")) {
                    fs.remove(candidate);
                }
            }
        }

        private List<String> directChildren(String path) {
            String prefix = path.equals("/") ? "/" : path + "/";
            List<String> children = new ArrayList<>();
            for (String candidate : sortedPaths()) {
                if (candidate.equals(path) || !candidate.startsWith(prefix)) {
                    continue;
                }
                String rest = candidate.substring(prefix.length());
                if (!rest.isEmpty() && !rest.contains("/")) {
                    children.add(rest + (fs.get(candidate).directory() ? "/" : ""));
                }
            }
            return children;
        }

        private List<String> sortedPaths() {
            return fs.keySet().stream().sorted(Comparator.naturalOrder()).toList();
        }

        private int usedBytes() {
            int total = 0;
            for (Map.Entry<String, FsEntry> entry : fs.entrySet()) {
                total += entry.getKey().length();
                total += entry.getValue().content().length();
            }
            return total;
        }

        private int subtreeBytes(String path) {
            int total = 0;
            for (Map.Entry<String, FsEntry> entry : fs.entrySet()) {
                String candidate = entry.getKey();
                if (candidate.equals(path) || candidate.startsWith(path.endsWith("/") ? path : path + "/")) {
                    total += candidate.length() + entry.getValue().content().length();
                }
            }
            return total;
        }

        private String normalizePath(String path) {
            if (path == null || path.isBlank()) {
                return cwd;
            }
            String normalized = path.trim().replace('\\', '/');
            if (normalized.equals(".")) {
                return cwd;
            }
            if (normalized.equals("..")) {
                return parentOf(cwd);
            }
            if (!normalized.startsWith("/")) {
                normalized = cwd.equals("/") ? "/" + normalized : cwd + "/" + normalized;
            }
            List<String> parts = new ArrayList<>();
            for (String part : normalized.split("/")) {
                if (part.isBlank() || part.equals(".")) {
                    continue;
                }
                if (part.equals("..")) {
                    if (!parts.isEmpty()) {
                        parts.remove(parts.size() - 1);
                    }
                } else {
                    parts.add(part);
                }
            }
            return parts.isEmpty() ? "/" : "/" + String.join("/", parts);
        }

        private String parentOf(String path) {
            if (path == null || path.equals("/")) {
                return "/";
            }
            int slash = path.lastIndexOf('/');
            return slash <= 0 ? "/" : path.substring(0, slash);
        }

        private String fileName(String path) {
            int slash = path.lastIndexOf('/');
            return slash < 0 ? path : path.substring(slash + 1);
        }

        private void readFileRecord(String value) {
            String[] parts = value.split(":", 3);
            if (parts.length != 3) {
                return;
            }
            putFile(decode(parts[0]), decode(parts[2]), decode(parts[1]));
        }

        private void readLegacyFileRecord(String value) {
            int separator = value.indexOf(':');
            if (separator <= 0) {
                return;
            }
            putFile(decode(value.substring(0, separator)), decode(value.substring(separator + 1)), "rw-r--r--");
        }

        private String modeToPermissions(String mode) {
            return switch (mode) {
                case "755", "+x" -> "rwxr-xr-x";
                case "644" -> "rw-r--r--";
                case "600" -> "rw-------";
                case "777" -> "rwxrwxrwx";
                default -> mode.length() == 9 ? mode : "rw-r--r--";
            };
        }

        private void redirectEcho(String text, String path, boolean append) {
            String normalizedPath = normalizePath(path);
            FsEntry current = fs.get(normalizedPath);
            String content = unquote(text);
            if (append && current != null && !current.directory()) {
                content = current.content() + "\n" + content;
            }
            putFile(normalizedPath, content, current == null ? "rw-r--r--" : current.permissions());
        }

        private void ensureProcess(int pid, String command, String state) {
            processes.putIfAbsent(pid, new ProcessInfo(pid, command, state));
            nextPid = Math.max(nextPid, pid + 1);
        }

        private static String trimForFrame(String line) {
            if (line.length() <= 36) {
                return line;
            }
            return line.substring(0, 33) + "...";
        }

        private final class LuaSubset {
            private final String source;
            private final Map<String, Integer> numbers = new HashMap<>();

            private LuaSubset(String source) {
                this.source = source;
            }

            private void execute() {
                int executed = 0;
                for (String rawLine : source.split("\\R")) {
                    String line = rawLine.trim();
                    if (line.isEmpty() || line.startsWith("--")) {
                        continue;
                    }
                    if (line.startsWith("print(") && line.endsWith(")")) {
                        printExpression(line.substring(6, line.length() - 1).trim());
                    } else if (line.contains("=")) {
                        int equals = line.indexOf('=');
                        String name = line.substring(0, equals).trim();
                        int value = evaluateNumber(line.substring(equals + 1).trim());
                        numbers.put(name, value);
                    } else {
                        appendLine("lua: unsupported statement: " + line);
                    }
                    executed++;
                    if (executed > 128) {
                        appendLine("lua: instruction budget exceeded");
                        return;
                    }
                }
            }

            private void printExpression(String expression) {
                if ((expression.startsWith("\"") && expression.endsWith("\"")) || (expression.startsWith("'") && expression.endsWith("'"))) {
                    appendLine(expression.substring(1, expression.length() - 1));
                } else {
                    appendLine(Integer.toString(evaluateNumber(expression)));
                }
            }

            private int evaluateNumber(String expression) {
                int result = 0;
                int sign = 1;
                for (String token : expression.replace("+", " + ").replace("-", " - ").split("\\s+")) {
                    if (token.isBlank()) {
                        continue;
                    }
                    if (token.equals("+")) {
                        sign = 1;
                    } else if (token.equals("-")) {
                        sign = -1;
                    } else {
                        result += sign * (numbers.containsKey(token) ? numbers.get(token) : parseInt(token));
                        sign = 1;
                    }
                }
                return result;
            }
        }
    }

    private record FsEntry(boolean directory, String content, String permissions) {
        private static FsEntry directoryEntry() {
            return new FsEntry(true, "", "rwxr-xr-x");
        }

        private static FsEntry file(String content, String permissions) {
            return new FsEntry(false, content == null ? "" : content, permissions == null ? "rw-r--r--" : permissions);
        }

        private FsEntry withPermissions(String nextPermissions) {
            return new FsEntry(directory, content, nextPermissions);
        }
    }

    private record ProcessInfo(int pid, String command, String state) {
    }

    private static List<String> tokenize(String command) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        char quote = 0;
        for (int i = 0; i < command.length(); i++) {
            char ch = command.charAt(i);
            if (quoted) {
                if (ch == quote) {
                    quoted = false;
                } else {
                    current.append(ch);
                }
            } else if (ch == '"' || ch == '\'') {
                quoted = true;
                quote = ch;
            } else if (Character.isWhitespace(ch)) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(ch);
            }
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private static String unquote(String text) {
        if (text.length() >= 2 && ((text.startsWith("\"") && text.endsWith("\"")) || (text.startsWith("'") && text.endsWith("'")))) {
            return text.substring(1, text.length() - 1);
        }
        return text;
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

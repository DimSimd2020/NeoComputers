/**
 * Frozen JNI contract for vm_bridge.h.
 *
 * <p>This interface defines symbol intent and JVM-side handle shape only.
 * It does not provide emulator logic.</p>
 */
public interface VmBridge {
    String CREATE_VM_SYMBOL = "create_vm";
    String TICK_VM_SYMBOL = "tick_vm";
    String SUBMIT_COMMAND_SYMBOL = "vm_submit_command";
    String DISK_IMAGE_SYMBOL = "vm_disk_image";
    String TERMINAL_SNAPSHOT_SYMBOL = "vm_terminal_snapshot";
    String FRAMEBUFFER_SNAPSHOT_SYMBOL = "vm_framebuffer_snapshot";
    String INSTALLED_OS_SYMBOL = "vm_installed_os";
    String IS_HALTED_SYMBOL = "vm_is_halted";
    String BOOT_COUNT_SYMBOL = "vm_boot_count";
    String DESTROY_VM_SYMBOL = "destroy_vm";

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

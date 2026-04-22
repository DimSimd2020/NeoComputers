/**
 * Frozen JNI contract for vm_bridge.h.
 *
 * <p>This interface defines symbol intent and JVM-side handle shape only.
 * It does not provide emulator logic.</p>
 */
public interface VmBridge {
    String CREATE_VM_SYMBOL = "create_vm";
    String TICK_VM_SYMBOL = "tick_vm";
    String DESTROY_VM_SYMBOL = "destroy_vm";

    long NULL_HANDLE = 0L;

    long createVm(int memorySizeMb);

    void tickVm(long handle);

    void destroyVm(long handle);
}

import java.lang.foreign.AddressLayout;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Frozen Panama FFM contract for vm_bridge.h.
 *
 * <p>This interface defines ABI layouts and symbol names only.
 * It does not provide emulator logic.</p>
 */
public interface VmBridge {
    String CREATE_VM_SYMBOL = "create_vm";
    String TICK_VM_SYMBOL = "tick_vm";
    String DESTROY_VM_SYMBOL = "destroy_vm";

    AddressLayout VM_HANDLE_LAYOUT = ValueLayout.ADDRESS.withName("vm_handle_t");
    ValueLayout.OfInt INT32_LAYOUT = ValueLayout.JAVA_INT.withName("int32_t");

    FunctionDescriptor CREATE_VM_DESC =
            FunctionDescriptor.of(VM_HANDLE_LAYOUT, INT32_LAYOUT);

    FunctionDescriptor TICK_VM_DESC =
            FunctionDescriptor.ofVoid(VM_HANDLE_LAYOUT);

    FunctionDescriptor DESTROY_VM_DESC =
            FunctionDescriptor.ofVoid(VM_HANDLE_LAYOUT);

    MemorySegment createVm(int memorySizeMb) throws Throwable;

    void tickVm(MemorySegment handle) throws Throwable;

    void destroyVm(MemorySegment handle) throws Throwable;

    record Downcalls(
            MethodHandle createVm,
            MethodHandle tickVm,
            MethodHandle destroyVm
    ) {
    }
}

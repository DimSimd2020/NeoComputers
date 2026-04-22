package com.vibecoder.neocomputers.vm;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Objects;

public final class VmBridgeContract {
    private static final String BRIDGE_CLASS_NAME = "VmBridge";
    private static final String DOWNCALLS_CLASS_NAME = "VmBridge$Downcalls";

    private VmBridgeContract() {
    }

    public static Object createDowncalls(Linker linker, SymbolLookup symbolLookup) {
        Objects.requireNonNull(linker, "linker");
        Objects.requireNonNull(symbolLookup, "symbolLookup");

        try {
            Class<?> bridgeClass = Class.forName(BRIDGE_CLASS_NAME);
            String createVmSymbol = readField(bridgeClass, "CREATE_VM_SYMBOL", String.class);
            String tickVmSymbol = readField(bridgeClass, "TICK_VM_SYMBOL", String.class);
            String destroyVmSymbol = readField(bridgeClass, "DESTROY_VM_SYMBOL", String.class);

            FunctionDescriptor createVmDesc = readField(bridgeClass, "CREATE_VM_DESC", FunctionDescriptor.class);
            FunctionDescriptor tickVmDesc = readField(bridgeClass, "TICK_VM_DESC", FunctionDescriptor.class);
            FunctionDescriptor destroyVmDesc = readField(bridgeClass, "DESTROY_VM_DESC", FunctionDescriptor.class);

            MethodHandle createVm = linker.downcallHandle(requiredSymbol(symbolLookup, createVmSymbol), createVmDesc);
            MethodHandle tickVm = linker.downcallHandle(requiredSymbol(symbolLookup, tickVmSymbol), tickVmDesc);
            MethodHandle destroyVm = linker.downcallHandle(requiredSymbol(symbolLookup, destroyVmSymbol), destroyVmDesc);

            Class<?> downcallsClass = Class.forName(DOWNCALLS_CLASS_NAME);
            Constructor<?> constructor = downcallsClass.getDeclaredConstructor(MethodHandle.class, MethodHandle.class, MethodHandle.class);
            return constructor.newInstance(createVm, tickVm, destroyVm);
        } catch (ReflectiveOperationException reflectiveOperationException) {
            throw new IllegalStateException("Failed to initialize VmBridge contract bindings", reflectiveOperationException);
        }
    }

    public static MethodHandle extractDowncall(Object downcalls, String accessorName) {
        Objects.requireNonNull(downcalls, "downcalls");
        Objects.requireNonNull(accessorName, "accessorName");

        try {
            Method accessor = downcalls.getClass().getMethod(accessorName);
            Object value = accessor.invoke(downcalls);
            if (!(value instanceof MethodHandle methodHandle)) {
                throw new IllegalStateException("Downcall accessor '" + accessorName + "' did not return MethodHandle");
            }
            return methodHandle;
        } catch (ReflectiveOperationException reflectiveOperationException) {
            throw new IllegalStateException("Failed to read downcall accessor '" + accessorName + "'", reflectiveOperationException);
        }
    }

    private static <T> T readField(Class<?> owner, String fieldName, Class<T> fieldType) throws ReflectiveOperationException {
        Object value = owner.getField(fieldName).get(null);
        if (!fieldType.isInstance(value)) {
            throw new IllegalStateException("Contract field '" + fieldName + "' has incompatible type");
        }
        return fieldType.cast(value);
    }

    private static MemorySegment requiredSymbol(SymbolLookup symbolLookup, String symbolName) {
        return symbolLookup.find(symbolName).orElseThrow(() -> new IllegalStateException("Missing native symbol: " + symbolName));
    }
}

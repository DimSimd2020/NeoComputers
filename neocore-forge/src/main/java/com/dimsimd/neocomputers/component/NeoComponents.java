package com.dimsimd.neocomputers.component;

import com.dimsimd.neocomputers.NeoComputers;
import com.dimsimd.neocomputers.component.data.CpuData;
import com.dimsimd.neocomputers.component.data.FirmwareData;
import com.dimsimd.neocomputers.component.data.MotherboardData;
import com.dimsimd.neocomputers.component.data.RamData;
import com.dimsimd.neocomputers.component.data.StorageData;
import net.minecraft.core.component.DataComponentType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class NeoComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENT_TYPES = DeferredRegister.createDataComponents(NeoComputers.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CpuData>> CPU_DATA = DATA_COMPONENT_TYPES.registerComponentType(
        "cpu_data",
        builder -> builder.persistent(CpuData.CODEC)
    );
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<RamData>> RAM_DATA = DATA_COMPONENT_TYPES.registerComponentType(
        "ram_data",
        builder -> builder.persistent(RamData.CODEC)
    );
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MotherboardData>> MOTHERBOARD_DATA = DATA_COMPONENT_TYPES.registerComponentType(
        "motherboard_data",
        builder -> builder.persistent(MotherboardData.CODEC)
    );
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<StorageData>> STORAGE_DATA = DATA_COMPONENT_TYPES.registerComponentType(
        "storage_data",
        builder -> builder.persistent(StorageData.CODEC)
    );
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FirmwareData>> FIRMWARE_DATA = DATA_COMPONENT_TYPES.registerComponentType(
        "firmware_data",
        builder -> builder.persistent(FirmwareData.CODEC)
    );

    private NeoComponents() {
    }

    public static void register(IEventBus modEventBus) {
        DATA_COMPONENT_TYPES.register(modEventBus);
    }
}

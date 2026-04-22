package com.dimsimd.neocomputers.item;

import com.dimsimd.neocomputers.NeoComputers;
import com.dimsimd.neocomputers.component.NeoComponents;
import com.dimsimd.neocomputers.component.data.CpuData;
import com.dimsimd.neocomputers.component.data.MotherboardData;
import com.dimsimd.neocomputers.component.data.RamData;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ItemDeferredRegister {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NeoComputers.MOD_ID);

    public static final DeferredItem<Item> CPU_TIER1 = ITEMS.register("cpu_tier1", () -> new Item(singleStack()
        .component(NeoComponents.CPU_DATA.get(), new CpuData(1, 1.2F, "Socket A"))));
    public static final DeferredItem<Item> CPU_TIER2 = ITEMS.register("cpu_tier2", () -> new Item(singleStack()
        .component(NeoComponents.CPU_DATA.get(), new CpuData(2, 2.4F, "Socket A"))));
    public static final DeferredItem<Item> CPU_TIER3 = ITEMS.register("cpu_tier3", () -> new Item(singleStack()
        .component(NeoComponents.CPU_DATA.get(), new CpuData(4, 4.0F, "Socket B"))));

    public static final DeferredItem<Item> RAM_DDR3 = ITEMS.register("ram_ddr3", () -> new Item(singleStack()
        .component(NeoComponents.RAM_DATA.get(), new RamData(4, 1200, "ddr3"))));
    public static final DeferredItem<Item> RAM_DDR4 = ITEMS.register("ram_ddr4", () -> new Item(singleStack()
        .component(NeoComponents.RAM_DATA.get(), new RamData(8, 2800, "ddr4"))));
    public static final DeferredItem<Item> RAM_DDR5 = ITEMS.register("ram_ddr5", () -> new Item(singleStack()
        .component(NeoComponents.RAM_DATA.get(), new RamData(16, 5600, "ddr5"))));

    public static final DeferredItem<Item> MOTHERBOARD_MATX = ITEMS.register("motherboard_matx", () -> new Item(singleStack()
        .component(NeoComponents.MOTHERBOARD_DATA.get(), new MotherboardData("Socket A", 2, 0, 1))));
    public static final DeferredItem<Item> MOTHERBOARD_ATX = ITEMS.register("motherboard_atx", () -> new Item(singleStack()
        .component(NeoComponents.MOTHERBOARD_DATA.get(), new MotherboardData("Socket A", 4, 1, 2))));

    public static final DeferredItem<Item> GPU_BASIC = ITEMS.registerSimpleItem("gpu_basic", singleStack());
    public static final DeferredItem<Item> NETWORK_CARD = ITEMS.registerSimpleItem("network_card", singleStack());

    private ItemDeferredRegister() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }

    private static Item.Properties singleStack() {
        return new Item.Properties().stacksTo(1);
    }
}

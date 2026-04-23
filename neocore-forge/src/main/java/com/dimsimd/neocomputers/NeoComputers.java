package com.dimsimd.neocomputers;

import com.mojang.logging.LogUtils;
import com.dimsimd.neocomputers.block.ComputerBlock;
import com.dimsimd.neocomputers.block.ComputerBlockEntity;
import com.dimsimd.neocomputers.block.PeripheralBlock;
import com.dimsimd.neocomputers.block.PeripheralBlock.PeripheralKind;
import com.dimsimd.neocomputers.block.PeripheralCableBlock;
import com.dimsimd.neocomputers.block.PeripheralBlockEntity;
import com.dimsimd.neocomputers.component.NeoComponents;
import com.dimsimd.neocomputers.item.ItemDeferredRegister;
import com.dimsimd.neocomputers.menu.NeoMenus;
import com.dimsimd.neocomputers.network.KeyboardInputPayload;
import com.dimsimd.neocomputers.vm.NativeVmRuntime;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(NeoComputers.MOD_ID)
public final class NeoComputers {
    public static final String MOD_ID = "neocomputers";
    private static final String NATIVE_LIBRARY_NAME = "vm_bridge";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final DeferredBlock<ComputerBlock> COMPUTER_BLOCK = BLOCKS.register("computer", () -> new ComputerBlock(
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(2.0F, 6.0F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops()
    ));
    public static final DeferredBlock<PeripheralBlock> MONITOR_BLOCK = BLOCKS.register("monitor", () -> new PeripheralBlock(
        PeripheralKind.MONITOR,
        peripheralProperties()
    ));
    public static final DeferredBlock<PeripheralBlock> KEYBOARD_BLOCK = BLOCKS.register("keyboard", () -> new PeripheralBlock(
        PeripheralKind.KEYBOARD,
        peripheralProperties()
    ));
    public static final DeferredBlock<PeripheralBlock> MOUSE_BLOCK = BLOCKS.register("mouse", () -> new PeripheralBlock(
        PeripheralKind.MOUSE,
        peripheralProperties()
    ));
    public static final DeferredBlock<PeripheralBlock> KEYBOARD_MOUSE_BLOCK = BLOCKS.register("keyboard_mouse", () -> new PeripheralBlock(
        PeripheralKind.KEYBOARD_MOUSE,
        peripheralProperties()
    ));
    public static final DeferredBlock<PeripheralCableBlock> PERIPHERAL_CABLE_BLOCK = BLOCKS.register("peripheral_cable", () -> new PeripheralCableBlock(
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BLACK)
            .strength(0.3F, 1.0F)
            .sound(SoundType.METAL)
            .noOcclusion()
    ));

    public static final DeferredItem<BlockItem> COMPUTER_BLOCK_ITEM = ITEMS.register("computer", () -> new BlockItem(
        COMPUTER_BLOCK.get(),
        new Item.Properties()
    ));
    public static final DeferredItem<BlockItem> MONITOR_BLOCK_ITEM = ITEMS.register("monitor", () -> new BlockItem(
        MONITOR_BLOCK.get(),
        new Item.Properties()
    ));
    public static final DeferredItem<BlockItem> KEYBOARD_BLOCK_ITEM = ITEMS.register("keyboard", () -> new BlockItem(
        KEYBOARD_BLOCK.get(),
        new Item.Properties()
    ));
    public static final DeferredItem<BlockItem> MOUSE_BLOCK_ITEM = ITEMS.register("mouse", () -> new BlockItem(
        MOUSE_BLOCK.get(),
        new Item.Properties()
    ));
    public static final DeferredItem<BlockItem> KEYBOARD_MOUSE_BLOCK_ITEM = ITEMS.register("keyboard_mouse", () -> new BlockItem(
        KEYBOARD_MOUSE_BLOCK.get(),
        new Item.Properties()
    ));
    public static final DeferredItem<BlockItem> PERIPHERAL_CABLE_BLOCK_ITEM = ITEMS.register("peripheral_cable", () -> new BlockItem(
        PERIPHERAL_CABLE_BLOCK.get(),
        new Item.Properties()
    ));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ComputerBlockEntity>> COMPUTER_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
        "computer",
        () -> BlockEntityType.Builder.of(ComputerBlockEntity::new, COMPUTER_BLOCK.get()).build(null)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PeripheralBlockEntity>> PERIPHERAL_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
        "peripheral",
        () -> BlockEntityType.Builder.of(PeripheralBlockEntity::new, MONITOR_BLOCK.get(), KEYBOARD_BLOCK.get(), MOUSE_BLOCK.get(), KEYBOARD_MOUSE_BLOCK.get()).build(null)
    );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> COMPUTER_TAB = CREATIVE_MODE_TABS.register("computers", () -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.neocomputers"))
        .withTabsBefore(CreativeModeTabs.FUNCTIONAL_BLOCKS)
        .icon(() -> COMPUTER_BLOCK_ITEM.get().getDefaultInstance())
        .displayItems((parameters, output) -> {
            output.accept(COMPUTER_BLOCK_ITEM.get());
            output.accept(MONITOR_BLOCK_ITEM.get());
            output.accept(KEYBOARD_MOUSE_BLOCK_ITEM.get());
            output.accept(PERIPHERAL_CABLE_BLOCK_ITEM.get());
            output.accept(ItemDeferredRegister.CPU_TIER1.get());
            output.accept(ItemDeferredRegister.CPU_TIER2.get());
            output.accept(ItemDeferredRegister.CPU_TIER3.get());
            output.accept(ItemDeferredRegister.RAM_DDR3.get());
            output.accept(ItemDeferredRegister.RAM_DDR4.get());
            output.accept(ItemDeferredRegister.RAM_DDR5.get());
            output.accept(ItemDeferredRegister.MOTHERBOARD_MATX.get());
            output.accept(ItemDeferredRegister.MOTHERBOARD_ATX.get());
            output.accept(ItemDeferredRegister.MOTHERBOARD_EATX.get());
            output.accept(ItemDeferredRegister.GPU_BASIC.get());
            output.accept(ItemDeferredRegister.NETWORK_CARD.get());
            output.accept(ItemDeferredRegister.BIOS_CARD.get());
            output.accept(ItemDeferredRegister.HDD_TIER1.get());
            output.accept(ItemDeferredRegister.HDD_TIER2.get());
            output.accept(ItemDeferredRegister.HDD_TIER3.get());
            output.accept(ItemDeferredRegister.SSD_TIER1.get());
            output.accept(ItemDeferredRegister.SSD_TIER2.get());
            output.accept(ItemDeferredRegister.SSD_TIER3.get());
        })
        .build());

    public NeoComputers(IEventBus modEventBus, ModContainer modContainer) {
        initializeNativeBridge();

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        ItemDeferredRegister.register(modEventBus);
        NeoComponents.register(modEventBus);
        NeoMenus.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        modEventBus.addListener(NeoComputers::addCreativeModeItems);
        modEventBus.addListener(NeoComputers::registerPayloads);
    }

    private static void initializeNativeBridge() {
        boolean initialized = NativeVmRuntime.initialize(NATIVE_LIBRARY_NAME, LOGGER);
        if (initialized) {
            LOGGER.info("Native NeoComputers VM bridge is active");
        } else {
            LOGGER.warn("Native NeoComputers VM bridge is unavailable, computer blocks run in inert mode");
        }
    }

    private static void addCreativeModeItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(COMPUTER_BLOCK_ITEM.get());
            event.accept(MONITOR_BLOCK_ITEM.get());
            event.accept(KEYBOARD_MOUSE_BLOCK_ITEM.get());
            event.accept(PERIPHERAL_CABLE_BLOCK_ITEM.get());
        }
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(ItemDeferredRegister.CPU_TIER1.get());
            event.accept(ItemDeferredRegister.CPU_TIER2.get());
            event.accept(ItemDeferredRegister.CPU_TIER3.get());
            event.accept(ItemDeferredRegister.RAM_DDR3.get());
            event.accept(ItemDeferredRegister.RAM_DDR4.get());
            event.accept(ItemDeferredRegister.RAM_DDR5.get());
            event.accept(ItemDeferredRegister.MOTHERBOARD_MATX.get());
            event.accept(ItemDeferredRegister.MOTHERBOARD_ATX.get());
            event.accept(ItemDeferredRegister.MOTHERBOARD_EATX.get());
            event.accept(ItemDeferredRegister.GPU_BASIC.get());
            event.accept(ItemDeferredRegister.NETWORK_CARD.get());
            event.accept(ItemDeferredRegister.BIOS_CARD.get());
            event.accept(ItemDeferredRegister.HDD_TIER1.get());
            event.accept(ItemDeferredRegister.HDD_TIER2.get());
            event.accept(ItemDeferredRegister.HDD_TIER3.get());
            event.accept(ItemDeferredRegister.SSD_TIER1.get());
            event.accept(ItemDeferredRegister.SSD_TIER2.get());
            event.accept(ItemDeferredRegister.SSD_TIER3.get());
        }
    }

    private static BlockBehaviour.Properties peripheralProperties() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BLACK)
            .strength(0.8F, 3.0F)
            .sound(SoundType.METAL)
            .noOcclusion();
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
            .playToServer(KeyboardInputPayload.TYPE, KeyboardInputPayload.STREAM_CODEC, KeyboardInputPayload::handle);
    }
}

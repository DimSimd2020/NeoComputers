package com.dimsimd.neocomputers.client;

import com.dimsimd.neocomputers.NeoComputers;
import com.dimsimd.neocomputers.block.PeripheralBlockEntity;
import com.dimsimd.neocomputers.client.renderer.PeripheralBlockEntityRenderer;
import com.dimsimd.neocomputers.client.screen.ComputerScreen;
import com.dimsimd.neocomputers.client.screen.KeyboardMouseScreen;
import com.dimsimd.neocomputers.menu.NeoMenus;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = NeoComputers.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class NeoComputersClientEvents {
    private NeoComputersClientEvents() {
    }

    @SubscribeEvent
    public static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(NeoMenus.COMPUTER_MENU.get(), ComputerScreen::new);
        event.register(NeoMenus.KEYBOARD_MOUSE_MENU.get(), KeyboardMouseScreen::new);
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(NeoComputers.PERIPHERAL_BLOCK_ENTITY.get(), PeripheralBlockEntityRenderer::new);
    }

    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            if (tintIndex != 0) {
                return 0xFFFFFF;
            }
            if (level == null || pos == null) {
                return 0x40D8FF;
            }

            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof PeripheralBlockEntity peripheralBlockEntity) {
                return peripheralBlockEntity.lightingColorForRender();
            }
            return 0x40D8FF;
        }, NeoComputers.MONITOR_BLOCK.get(), NeoComputers.KEYBOARD_BLOCK.get(), NeoComputers.MOUSE_BLOCK.get(), NeoComputers.KEYBOARD_MOUSE_BLOCK.get());
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> tintIndex == 0 ? 0x40D8FF : 0xFFFFFF,
            NeoComputers.MONITOR_BLOCK_ITEM.get(),
            NeoComputers.KEYBOARD_BLOCK_ITEM.get(),
            NeoComputers.MOUSE_BLOCK_ITEM.get(),
            NeoComputers.KEYBOARD_MOUSE_BLOCK_ITEM.get()
        );
    }
}

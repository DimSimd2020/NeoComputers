package com.dimsimd.neocomputers.menu;

import com.dimsimd.neocomputers.NeoComputers;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class NeoMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, NeoComputers.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<ComputerMenu>> COMPUTER_MENU = MENUS.register(
        "computer",
        () -> new MenuType<>(ComputerMenu::new, FeatureFlags.DEFAULT_FLAGS)
    );
    public static final DeferredHolder<MenuType<?>, MenuType<KeyboardMouseMenu>> KEYBOARD_MOUSE_MENU = MENUS.register(
        "keyboard_mouse",
        () -> IMenuTypeExtension.create(KeyboardMouseMenu::new)
    );

    private NeoMenus() {
    }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}

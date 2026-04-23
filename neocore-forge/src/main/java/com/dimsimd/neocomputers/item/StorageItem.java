package com.dimsimd.neocomputers.item;

import com.dimsimd.neocomputers.component.NeoComponents;
import com.dimsimd.neocomputers.component.data.StorageData;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public final class StorageItem extends Item {
    public StorageItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        StorageData storageData = stack.get(NeoComponents.STORAGE_DATA.get());
        if (storageData == null) {
            return;
        }

        if (!Screen.hasShiftDown()) {
            tooltipComponents.add(Component.literal("Нажмите [SHIFT] для подробных характеристик").withStyle(ChatFormatting.GRAY));
            return;
        }

        tooltipComponents.add(Component.literal("Storage").withStyle(ChatFormatting.BLUE));
        tooltipComponents.add(line("Тип", storageData.type().toUpperCase(Locale.ROOT)));
        tooltipComponents.add(line("Объем", formatCapacity(storageData.capacityMb())));
        tooltipComponents.add(line("Пропускная способность", String.format(Locale.ROOT, "%d MB/s", storageData.throughputMbS())));
        tooltipComponents.add(line("OS", storageData.hasInstalledOs() ? storageData.installedOs() : "not installed"));
    }

    private static String formatCapacity(int capacityMb) {
        if (capacityMb >= 1024 && capacityMb % 1024 == 0) {
            return String.format(Locale.ROOT, "%d GB", capacityMb / 1024);
        }
        return String.format(Locale.ROOT, "%d MB", capacityMb);
    }

    private static Component line(String title, String value) {
        return Component.literal(title + ": ").withStyle(ChatFormatting.BLUE)
            .append(Component.literal(value).withStyle(ChatFormatting.GOLD));
    }
}

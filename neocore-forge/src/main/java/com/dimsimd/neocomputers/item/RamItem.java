package com.dimsimd.neocomputers.item;

import com.dimsimd.neocomputers.component.NeoComponents;
import com.dimsimd.neocomputers.component.data.RamData;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public final class RamItem extends Item {
    public RamItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        RamData ramData = stack.get(NeoComponents.RAM_DATA.get());
        if (ramData == null) {
            return;
        }

        if (!Screen.hasShiftDown()) {
            tooltipComponents.add(Component.literal("Нажмите [SHIFT] для подробных характеристик").withStyle(ChatFormatting.GRAY));
            return;
        }

        tooltipComponents.add(Component.literal("RAM").withStyle(ChatFormatting.BLUE));
        tooltipComponents.add(line("Объем", String.format(Locale.ROOT, "%d MB", ramData.memoryMb())));
        tooltipComponents.add(line("Частота", String.format(Locale.ROOT, "%d MHz", ramData.frequencyMhz())));
        tooltipComponents.add(line("Тип", ramData.type().toUpperCase(Locale.ROOT)));
    }

    private static Component line(String title, String value) {
        return Component.literal(title + ": ").withStyle(ChatFormatting.BLUE)
            .append(Component.literal(value).withStyle(ChatFormatting.GOLD));
    }
}

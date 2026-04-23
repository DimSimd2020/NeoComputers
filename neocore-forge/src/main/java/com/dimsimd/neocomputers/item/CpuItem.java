package com.dimsimd.neocomputers.item;

import com.dimsimd.neocomputers.component.NeoComponents;
import com.dimsimd.neocomputers.component.data.CpuData;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public final class CpuItem extends Item {
    private final float ipcMultiplier;

    public CpuItem(Properties properties, float ipcMultiplier) {
        super(properties);
        this.ipcMultiplier = ipcMultiplier;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        CpuData cpuData = stack.get(NeoComponents.CPU_DATA.get());
        if (cpuData == null) {
            return;
        }

        if (!Screen.hasShiftDown()) {
            tooltipComponents.add(Component.literal("Нажмите [SHIFT] для подробных характеристик").withStyle(ChatFormatting.GRAY));
            return;
        }

        float flops = cpuData.cores() * cpuData.frequencyGhz() * ipcMultiplier;
        tooltipComponents.add(Component.literal("CPU").withStyle(ChatFormatting.BLUE));
        tooltipComponents.add(line("Ядра", Integer.toString(cpuData.cores())));
        tooltipComponents.add(line("Частота", format("%.1f GHz", cpuData.frequencyGhz())));
        tooltipComponents.add(line("Сокет", cpuData.socket()));
        tooltipComponents.add(line("IPC", format("x%.2f", ipcMultiplier)));
        tooltipComponents.add(line("Теоретическая мощность", format("%.2f FLOPS", flops)));
    }

    private static Component line(String title, String value) {
        return Component.literal(title + ": ").withStyle(ChatFormatting.BLUE)
            .append(Component.literal(value).withStyle(ChatFormatting.GOLD));
    }

    private static String format(String pattern, Object value) {
        return String.format(Locale.ROOT, pattern, value);
    }
}

package com.dimsimd.neocomputers.item;

import com.dimsimd.neocomputers.component.NeoComponents;
import com.dimsimd.neocomputers.component.data.MotherboardData;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public final class MotherboardItem extends Item {
    public MotherboardItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        MotherboardData motherboardData = stack.get(NeoComponents.MOTHERBOARD_DATA.get());
        if (motherboardData == null) {
            return;
        }

        if (!Screen.hasShiftDown()) {
            tooltipComponents.add(Component.literal("Нажмите [SHIFT] для подробных характеристик").withStyle(ChatFormatting.GRAY));
            return;
        }

        tooltipComponents.add(Component.literal("Motherboard").withStyle(ChatFormatting.BLUE));
        tooltipComponents.add(line("Сокет", motherboardData.socket()));
        tooltipComponents.add(line("RAM слоты", Integer.toString(motherboardData.ramSlots())));
        tooltipComponents.add(line("PCIe слоты", Integer.toString(motherboardData.pcieSlots())));
        tooltipComponents.add(line("SATA слоты", Integer.toString(motherboardData.sataSlots())));
        tooltipComponents.add(line("Профиль", formatBoardProfile(motherboardData)));
    }

    private static Component line(String title, String value) {
        return Component.literal(title + ": ").withStyle(ChatFormatting.BLUE)
            .append(Component.literal(value).withStyle(ChatFormatting.GOLD));
    }

    private static String formatBoardProfile(MotherboardData motherboardData) {
        if (motherboardData.socket().equalsIgnoreCase("Socket B")) {
            return "E-ATX";
        }
        if (motherboardData.ramSlots() <= 2 && motherboardData.pcieSlots() == 0) {
            return "Micro-ATX";
        }
        if (motherboardData.ramSlots() >= 4 && motherboardData.pcieSlots() == 1) {
            return "ATX";
        }
        return String.format(
            Locale.ROOT,
            "Custom (%s/%d/%d/%d)",
            motherboardData.socket(),
            motherboardData.ramSlots(),
            motherboardData.pcieSlots(),
            motherboardData.sataSlots()
        );
    }
}

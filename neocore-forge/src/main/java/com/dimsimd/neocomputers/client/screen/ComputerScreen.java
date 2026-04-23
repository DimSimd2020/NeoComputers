package com.dimsimd.neocomputers.client.screen;

import com.dimsimd.neocomputers.NeoComputers;
import com.dimsimd.neocomputers.component.data.MotherboardData;
import com.dimsimd.neocomputers.menu.ComputerMenu;
import com.dimsimd.neocomputers.menu.ComputerMenu.SlotType;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.opengl.GL11;

public final class ComputerScreen extends AbstractContainerScreen<ComputerMenu> {
    private static final ResourceLocation BG_EMPTY = texture("computer_empty");
    private static final ResourceLocation BG_MATX = texture("computer_matx");
    private static final ResourceLocation BG_ATX = texture("computer_atx");
    private static final ResourceLocation BG_EATX = texture("computer_eatx");
    private static final ResourceLocation GHOST_ICONS = texture("computer_ghosts");
    private static final ResourceLocation PLAYER_INV_BG = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final int MACHINE_BG_HEIGHT = 198;

    private final List<InfoZone> infoZones = new ArrayList<>();
    private PowerButton powerButton;

    public ComputerScreen(ComputerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 176;
        imageHeight = 294;
        inventoryLabelY = 200;
    }

    @Override
    protected void init() {
        super.init();
        powerButton = addRenderableWidget(new PowerButton(leftPos + 149, topPos + 6));
        rebuildInfoZones();
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        ResourceLocation background = resolveBackground(menu.motherboardData());
        guiGraphics.blit(background, leftPos, topPos, 0, 0, imageWidth, MACHINE_BG_HEIGHT, imageWidth, MACHINE_BG_HEIGHT);
        guiGraphics.blit(PLAYER_INV_BG, leftPos, topPos + MACHINE_BG_HEIGHT, 0, 126, imageWidth, 96, 256, 256);

        drawGhostIcons(guiGraphics);
        drawInfoHints(guiGraphics, mouseX, mouseY);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
        renderInfoZoneTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (powerButton != null) {
            powerButton.active = true;
        }
    }

    private void drawGhostIcons(GuiGraphics guiGraphics) {
        for (int i = 0; i < ComputerMenu.MACHINE_SLOT_COUNT; i++) {
            Slot slot = menu.slots.get(i);
            SlotType slotType = menu.slotTypeForIndex(i);
            if (slotType == SlotType.MOTHERBOARD || !slot.isActive() || slot.hasItem()) {
                continue;
            }

            int u = switch (slotType) {
                case CPU -> 0;
                case RAM -> 16;
                case PCIE -> 32;
                case SATA -> 48;
                default -> -1;
            };
            if (u < 0) {
                continue;
            }

            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            guiGraphics.setColor(1.0F, 1.0F, 1.0F, 0.45F);
            guiGraphics.blit(GHOST_ICONS, leftPos + slot.x, topPos + slot.y, u, 0, 16, 16, 64, 16);
            guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private void drawInfoHints(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        for (InfoZone zone : infoZones) {
            int x0 = leftPos + zone.x;
            int y0 = topPos + zone.y;
            int x1 = x0 + zone.width;
            int y1 = y0 + zone.height;
            boolean hovered = mouseX >= x0 && mouseX < x1 && mouseY >= y0 && mouseY < y1;

            int color = hovered ? 0xFFE0C46C : 0xFF8FA0B8;
            guiGraphics.fill(x0, y0, x1, y1, 0x991A2430);
            guiGraphics.drawCenteredString(font, "?", x0 + (zone.width / 2), y0 - 1, color);
        }
    }

    private void renderInfoZoneTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        for (InfoZone zone : infoZones) {
            int x0 = leftPos + zone.x;
            int y0 = topPos + zone.y;
            int x1 = x0 + zone.width;
            int y1 = y0 + zone.height;
            if (mouseX >= x0 && mouseX < x1 && mouseY >= y0 && mouseY < y1) {
                List<FormattedCharSequence> lines = compatibleParts(zone.slotIndex).stream()
                    .map(Component::getVisualOrderText)
                    .toList();
                guiGraphics.renderTooltip(font, lines, mouseX, mouseY);
                return;
            }
        }
    }

    private List<Component> compatibleParts(int slotIndex) {
        SlotType slotType = menu.slotTypeForIndex(slotIndex);
        MotherboardData motherboardData = menu.motherboardData();

        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Совместимость").withStyle(ChatFormatting.BLUE));

        switch (slotType) {
            case MOTHERBOARD -> lines.add(Component.literal("Сюда устанавливается материнская плата").withStyle(ChatFormatting.GOLD));
            case CPU -> {
                if (motherboardData == null) {
                    lines.add(Component.literal("Сначала установите материнскую плату").withStyle(ChatFormatting.GRAY));
                } else {
                    lines.add(Component.literal("Подходит CPU с сокетом: " + motherboardData.socket()).withStyle(ChatFormatting.GOLD));
                }
            }
            case RAM -> lines.add(Component.literal("Сюда подходят: DDR3 RAM, DDR4 RAM, DDR5 RAM").withStyle(ChatFormatting.GOLD));
            case PCIE -> lines.add(Component.literal("Сюда подходят: GPU, Network Card").withStyle(ChatFormatting.GOLD));
            case SATA -> lines.add(Component.literal("Сюда подходят: HDD, SSD").withStyle(ChatFormatting.GOLD));
            default -> lines.add(Component.literal("Нет данных").withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    private void rebuildInfoZones() {
        infoZones.clear();
        int maxSlots = Mth.clamp(ComputerMenu.MACHINE_SLOT_COUNT, 0, menu.slots.size());
        for (int i = 0; i < maxSlots; i++) {
            Slot slot = menu.slots.get(i);
            if (i == ComputerMenu.MOTHERBOARD_SLOT) {
                infoZones.add(new InfoZone(slot.x + 18, slot.y - 10, 8, 8, i));
                continue;
            }
            infoZones.add(new InfoZone(slot.x + 16, slot.y - 1, 8, 8, i));
        }
    }

    private static ResourceLocation resolveBackground(@Nullable MotherboardData motherboardData) {
        if (motherboardData == null) {
            return BG_EMPTY;
        }
        if (motherboardData.socket().equalsIgnoreCase("Socket B")) {
            return BG_EATX;
        }
        if (motherboardData.pcieSlots() <= 0) {
            return BG_MATX;
        }
        return BG_ATX;
    }

    private static ResourceLocation texture(String id) {
        return ResourceLocation.fromNamespaceAndPath(NeoComputers.MOD_ID, "textures/gui/" + id + ".png");
    }

    private final class PowerButton extends AbstractButton {
        private PowerButton(int x, int y) {
            super(x, y, 20, 20, Component.empty());
        }

        @Override
        public void onPress() {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, ComputerMenu.TOGGLE_POWER_BUTTON_ID);
            }
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            boolean powered = menu.isPowered();
            int background = powered ? 0xCC2A5FB4 : 0xCC8D2F2F;
            int border = powered ? 0xFF69A8FF : 0xFFFF7878;
            int text = powered ? 0xFFE4F1FF : 0xFFFFE7E7;

            guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, border);
            guiGraphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, background);
            guiGraphics.drawCenteredString(font, powered ? "I" : "O", getX() + width / 2, getY() + 6, text);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            defaultButtonNarrationText(narrationElementOutput);
        }
    }

    private record InfoZone(int x, int y, int width, int height, int slotIndex) {
    }
}

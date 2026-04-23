package com.dimsimd.neocomputers.client.screen;

import com.dimsimd.neocomputers.NeoComputers;
import com.dimsimd.neocomputers.block.ComputerBlockEntity;
import com.dimsimd.neocomputers.block.PeripheralBlock;
import com.dimsimd.neocomputers.block.PeripheralCableNetwork;
import com.dimsimd.neocomputers.menu.KeyboardMouseMenu;
import com.dimsimd.neocomputers.network.KeyboardInputPayload;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

public final class KeyboardMouseScreen extends AbstractContainerScreen<KeyboardMouseMenu> {
    private static final int MAX_CAPTURED_CHARS = 160;
    private String capturedInput = "";
    private int cursorPosition = 0;
    private MonitorWall monitorWall = new MonitorWall(1, 1);

    public KeyboardMouseScreen(KeyboardMouseMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 248;
        imageHeight = 188;
        titleLabelX = 10;
        titleLabelY = 8;
        inventoryLabelY = 1000;
    }

    @Override
    protected void init() {
        super.init();
        refreshMonitorWall();
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xEE11151B);
        guiGraphics.fill(leftPos + 8, topPos + 24, leftPos + 240, topPos + 134, 0xFF05080D);

        ComputerBlockEntity computer = findLinkedComputer();
        if (computer == null || !computer.isPowered()) {
            guiGraphics.drawString(font, "", leftPos + 18, topPos + 44, 0xFFFFFFFF, false);
            return;
        }

        guiGraphics.fill(leftPos + 10, topPos + 26, leftPos + 238, topPos + 132, 0xFF101923);

        int panelWidth = 212;
        int panelHeight = 78;
        int tileWidth = Math.max(12, panelWidth / monitorWall.width());
        int tileHeight = Math.max(12, panelHeight / monitorWall.height());
        int wallWidth = tileWidth * monitorWall.width();
        int wallHeight = tileHeight * monitorWall.height();
        int wallX = leftPos + 18 + (panelWidth - wallWidth) / 2;
        int wallY = topPos + 34 + (panelHeight - wallHeight) / 2;

        for (int y = 0; y < monitorWall.height(); y++) {
            for (int x = 0; x < monitorWall.width(); x++) {
                int x0 = wallX + x * tileWidth;
                int y0 = wallY + y * tileHeight;
                guiGraphics.fill(x0, y0, x0 + tileWidth, y0 + tileHeight, 0xFF0A2732);
                guiGraphics.fill(x0 + 1, y0 + 1, x0 + tileWidth - 1, y0 + tileHeight - 1, 0xFF123F55);
            }
        }

        String osName = computer.installedOsName();
        guiGraphics.drawString(font, osName == null ? "NeoBIOS" : osName, leftPos + 18, topPos + 44, osName == null ? 0xFFFFD166 : 0xFF61D9FF, false);
        guiGraphics.drawString(font, osName == null ? "No OS found. Type: install alpine" : "localhost login: root", leftPos + 18, topPos + 58, 0xFFE6F6FF, false);

        int lineY = topPos + 72;
        List<String> terminalLines = computer.terminalLines();
        int firstLine = Math.max(0, terminalLines.size() - 4);
        for (int i = firstLine; i < terminalLines.size(); i++) {
            guiGraphics.drawString(font, terminalLines.get(i), leftPos + 18, lineY, 0xFFE6F6FF, false);
            lineY += 10;
        }

        guiGraphics.drawString(font, "NeoTTY", leftPos + 18, topPos + 113, 0xFF61D9FF, false);
        String visibleInput = computer.prompt() + " " + capturedInput.substring(0, cursorPosition) + "_" + capturedInput.substring(cursorPosition);
        guiGraphics.drawString(font, visibleInput, leftPos + 18, topPos + 144, 0xFFE6F6FF, false);
        guiGraphics.drawString(font, monitorWall.width() + "x" + monitorWall.height() + " monitor wall", leftPos + 160, topPos + 113, 0xFF7C91A4, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title, titleLabelX, titleLabelY, ChatFormatting.WHITE.getColor(), false);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!Character.isISOControl(codePoint)) {
            insertInput(Character.toString(codePoint));
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (Screen.hasControlDown() && keyCode == InputConstants.KEY_C) {
            minecraft.keyboardHandler.setClipboard(capturedInput);
            return true;
        }
        if (Screen.hasControlDown() && keyCode == InputConstants.KEY_V) {
            insertInput(minecraft.keyboardHandler.getClipboard().replace("\r", ""));
            return true;
        }
        if (keyCode == InputConstants.KEY_BACKSPACE && cursorPosition > 0) {
            capturedInput = capturedInput.substring(0, cursorPosition - 1) + capturedInput.substring(cursorPosition);
            cursorPosition--;
            return true;
        }
        if (keyCode == InputConstants.KEY_DELETE && cursorPosition < capturedInput.length()) {
            capturedInput = capturedInput.substring(0, cursorPosition) + capturedInput.substring(cursorPosition + 1);
            return true;
        }
        if (keyCode == InputConstants.KEY_LEFT) {
            cursorPosition = Math.max(0, cursorPosition - 1);
            return true;
        }
        if (keyCode == InputConstants.KEY_RIGHT) {
            cursorPosition = Math.min(capturedInput.length(), cursorPosition + 1);
            return true;
        }
        if (keyCode == InputConstants.KEY_HOME) {
            cursorPosition = 0;
            return true;
        }
        if (keyCode == InputConstants.KEY_END) {
            cursorPosition = capturedInput.length();
            return true;
        }
        if (keyCode == InputConstants.KEY_RETURN) {
            String command = capturedInput.trim();
            if (!command.isEmpty()) {
                PacketDistributor.sendToServer(new KeyboardInputPayload(menu.inputPos(), command));
            }
            capturedInput = "";
            cursorPosition = 0;
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1) {
            insertInput(minecraft.keyboardHandler.getClipboard().replace("\r", ""));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void insertInput(String text) {
        capturedInput = capturedInput.substring(0, cursorPosition) + text + capturedInput.substring(cursorPosition);
        cursorPosition += text.length();
        if (capturedInput.length() > MAX_CAPTURED_CHARS) {
            capturedInput = capturedInput.substring(capturedInput.length() - MAX_CAPTURED_CHARS);
            cursorPosition = Math.min(cursorPosition, capturedInput.length());
        }
    }

    private void refreshMonitorWall() {
        Level level = minecraft == null ? null : minecraft.level;
        if (level == null) {
            return;
        }

        BlockPos monitorPos = findNearestMonitor(level, menu.inputPos());
        if (monitorPos == null) {
            monitorWall = new MonitorWall(1, 1);
            return;
        }

        monitorWall = scanMonitorWall(level, monitorPos);
    }

    private static BlockPos findNearestMonitor(Level level, BlockPos origin) {
        BlockPos bestPos = null;
        double bestDistance = Double.MAX_VALUE;
        int radius = 7;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -radius, -radius), origin.offset(radius, radius, radius))) {
            if (!isMonitor(level, pos)) {
                continue;
            }
            double distance = pos.distSqr(origin);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestPos = pos.immutable();
            }
        }
        return bestPos;
    }

    private static MonitorWall scanMonitorWall(Level level, BlockPos start) {
        BlockState startState = level.getBlockState(start);
        Direction facing = startState.getValue(PeripheralBlock.FACING);
        Direction horizontal = facing.getClockWise();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        visited.add(start);
        queue.add(start);

        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            for (BlockPos neighbor : new BlockPos[] {
                pos.relative(horizontal),
                pos.relative(horizontal.getOpposite()),
                pos.above(),
                pos.below()
            }) {
                if (!visited.contains(neighbor) && isMonitor(level, neighbor) && level.getBlockState(neighbor).getValue(PeripheralBlock.FACING) == facing) {
                    BlockPos immutable = neighbor.immutable();
                    visited.add(immutable);
                    queue.add(immutable);
                }
            }
        }

        int minHorizontal = 0;
        int maxHorizontal = 0;
        int minY = start.getY();
        int maxY = start.getY();
        for (BlockPos pos : visited) {
            int horizontalOffset = horizontal.getAxis() == Direction.Axis.X ? pos.getX() - start.getX() : pos.getZ() - start.getZ();
            minHorizontal = Math.min(minHorizontal, horizontalOffset);
            maxHorizontal = Math.max(maxHorizontal, horizontalOffset);
            minY = Math.min(minY, pos.getY());
            maxY = Math.max(maxY, pos.getY());
        }

        return new MonitorWall(maxHorizontal - minHorizontal + 1, maxY - minY + 1);
    }

    private static boolean isMonitor(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(NeoComputers.MONITOR_BLOCK.get());
    }

    private ComputerBlockEntity findLinkedComputer() {
        Level level = minecraft == null ? null : minecraft.level;
        if (level == null) {
            return null;
        }
        BlockEntity inputBlockEntity = level.getBlockEntity(menu.inputPos());
        if (inputBlockEntity instanceof com.dimsimd.neocomputers.block.PeripheralBlockEntity peripheralBlockEntity) {
            ComputerBlockEntity linked = peripheralBlockEntity.linkedComputer();
            if (linked != null) {
                return linked;
            }
        }

        return PeripheralCableNetwork.findConnectedComputer(level, menu.inputPos());
    }

    private record MonitorWall(int width, int height) {
    }
}

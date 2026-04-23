package com.dimsimd.neocomputers.client.renderer;

import com.dimsimd.neocomputers.block.ComputerBlockEntity;
import com.dimsimd.neocomputers.block.PeripheralBlock;
import com.dimsimd.neocomputers.block.PeripheralBlockEntity;
import com.dimsimd.neocomputers.block.PeripheralCableNetwork;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

public final class PeripheralBlockEntityRenderer implements BlockEntityRenderer<PeripheralBlockEntity> {
    private final Font font;

    public PeripheralBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(
        PeripheralBlockEntity blockEntity,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int packedLight,
        int packedOverlay
    ) {
        if (!(blockEntity.getBlockState().getBlock() instanceof PeripheralBlock peripheralBlock) || peripheralBlock.kind() != PeripheralBlock.PeripheralKind.MONITOR) {
            return;
        }

        ComputerBlockEntity computer = blockEntity.linkedComputer();
        if (computer == null && blockEntity.getLevel() != null) {
            computer = PeripheralCableNetwork.findConnectedComputer(blockEntity.getLevel(), blockEntity.getBlockPos());
        }
        if (computer == null || !computer.isPowered()) {
            return;
        }

        List<String> framebufferLines = computer.framebufferLines();

        Direction facing = blockEntity.getBlockState().getValue(PeripheralBlock.FACING);
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(rotationFor(facing)));
        poseStack.translate(-0.43D, -0.10D, -0.315D);
        poseStack.scale(0.006F, -0.006F, 0.006F);
        int lineY = 0;
        int firstLine = Math.max(0, framebufferLines.size() - 8);
        for (int i = firstLine; i < framebufferLines.size(); i++) {
            int color = i == firstLine ? 0x61D9FF : 0xE6F6FF;
            font.drawInBatch(trimToMonitor(framebufferLines.get(i)), 0.0F, lineY, color, false, poseStack.last().pose(), bufferSource, Font.DisplayMode.POLYGON_OFFSET, 0, packedLight);
            lineY += 10;
        }
        poseStack.popPose();
    }

    private static String trimToMonitor(String line) {
        if (line.length() <= 32) {
            return line;
        }
        return line.substring(0, 29) + "...";
    }

    private static float rotationFor(Direction facing) {
        return switch (facing) {
            case NORTH -> 0.0F;
            case EAST -> 270.0F;
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            default -> 0.0F;
        };
    }

}

package com.dimsimd.neocomputers.block;

import com.dimsimd.neocomputers.NeoComputers;
import com.dimsimd.neocomputers.menu.KeyboardMouseMenu;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class PeripheralBlockEntity extends BlockEntity implements MenuProvider {
    private static final String LIGHTING_COLOR_TAG = "LightingColor";
    private static final String LIGHTING_BRIGHTNESS_TAG = "LightingBrightness";
    private static final String LINKED_COMPUTER_TAG = "LinkedComputer";
    private static final int[] PRESET_COLORS = {
        0x1EA7FF,
        0x8A5CFF,
        0x33E0B0,
        0xFF3B57,
        0xFFD166,
        0xFFFFFF
    };

    private int lightingColor = 0x1EA7FF;
    private int lightingBrightness = 80;
    @Nullable
    private BlockPos linkedComputerPos;

    public PeripheralBlockEntity(BlockPos pos, BlockState blockState) {
        super(NeoComputers.PERIPHERAL_BLOCK_ENTITY.get(), pos, blockState);
    }

    public int lightingColor() {
        return lightingColor;
    }

    public int lightingBrightness() {
        return lightingBrightness;
    }

    public int lightingColorForRender() {
        float scale = Mth.clamp(lightingBrightness, 0, 100) / 100.0F;
        int red = Math.round(((lightingColor >> 16) & 0xFF) * scale);
        int green = Math.round(((lightingColor >> 8) & 0xFF) * scale);
        int blue = Math.round((lightingColor & 0xFF) * scale);
        return (red << 16) | (green << 8) | blue;
    }

    public void applyDefaults(PeripheralBlock.PeripheralKind kind) {
        setLighting(kind.defaultColor(), kind.defaultBrightness());
        refreshWiredLink();
    }

    @Nullable
    public BlockPos linkedComputerPos() {
        return linkedComputerPos;
    }

    @Nullable
    public ComputerBlockEntity linkedComputer() {
        if (level == null || linkedComputerPos == null) {
            return null;
        }
        BlockEntity blockEntity = level.getBlockEntity(linkedComputerPos);
        if (blockEntity instanceof ComputerBlockEntity computerBlockEntity) {
            return computerBlockEntity;
        }
        return null;
    }

    public void linkToNearestComputer(int radius) {
        if (level == null) {
            return;
        }

        ComputerBlockEntity nearest = null;
        BlockPos nearestPos = null;
        double nearestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(worldPosition.offset(-radius, -radius, -radius), worldPosition.offset(radius, radius, radius))) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof ComputerBlockEntity computerBlockEntity) {
                double distance = pos.distSqr(worldPosition);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = computerBlockEntity;
                    nearestPos = pos.immutable();
                }
            }
        }

        if (nearest != null) {
            linkedComputerPos = nearestPos;
            syncLighting();
        }
    }

    public void refreshWiredLink() {
        if (level == null) {
            return;
        }

        BlockPos connectedComputerPos = PeripheralCableNetwork.findConnectedComputerPos(level, worldPosition);
        if (connectedComputerPos == null ? linkedComputerPos == null : connectedComputerPos.equals(linkedComputerPos)) {
            return;
        }

        linkedComputerPos = connectedComputerPos;
        syncLighting();
    }

    public void setLighting(int rgb, int brightness) {
        int normalizedColor = rgb & 0xFFFFFF;
        int normalizedBrightness = Mth.clamp(brightness, 0, 100);
        if (lightingColor == normalizedColor && lightingBrightness == normalizedBrightness) {
            return;
        }

        lightingColor = normalizedColor;
        lightingBrightness = normalizedBrightness;
        syncLighting();
    }

    public void cycleLightingBrightness() {
        int nextBrightness = lightingBrightness >= 100 ? 0 : lightingBrightness + 25;
        setLighting(lightingColor, nextBrightness);
    }

    public void cycleLightingColor() {
        int nextIndex = 0;
        for (int i = 0; i < PRESET_COLORS.length; i++) {
            if (PRESET_COLORS[i] == lightingColor) {
                nextIndex = (i + 1) % PRESET_COLORS.length;
                break;
            }
        }
        setLighting(PRESET_COLORS[nextIndex], lightingBrightness);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putInt(LIGHTING_COLOR_TAG, lightingColor);
        tag.putInt(LIGHTING_BRIGHTNESS_TAG, lightingBrightness);
        if (linkedComputerPos != null) {
            tag.putLong(LINKED_COMPUTER_TAG, linkedComputerPos.asLong());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.contains(LIGHTING_COLOR_TAG)) {
            lightingColor = tag.getInt(LIGHTING_COLOR_TAG) & 0xFFFFFF;
        }
        if (tag.contains(LIGHTING_BRIGHTNESS_TAG)) {
            lightingBrightness = Mth.clamp(tag.getInt(LIGHTING_BRIGHTNESS_TAG), 0, 100);
        }
        if (tag.contains(LINKED_COMPUTER_TAG)) {
            linkedComputerPos = BlockPos.of(tag.getLong(LINKED_COMPUTER_TAG));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public Component getDisplayName() {
        if (getBlockState().getBlock() instanceof PeripheralBlock peripheralBlock) {
            return Component.translatable("block.neocomputers." + peripheralBlock.kind().getSerializedName());
        }
        return Component.translatable("block.neocomputers.keyboard_mouse");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new KeyboardMouseMenu(containerId, playerInventory, worldPosition);
    }

    private void syncLighting() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }
}

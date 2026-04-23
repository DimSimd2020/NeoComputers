package com.dimsimd.neocomputers.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public final class KeyboardMouseMenu extends AbstractContainerMenu {
    private final BlockPos inputPos;

    public KeyboardMouseMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, BlockPos.ZERO);
    }

    public KeyboardMouseMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, extraData.readBlockPos());
    }

    public KeyboardMouseMenu(int containerId, Inventory playerInventory, BlockPos inputPos) {
        super(NeoMenus.KEYBOARD_MOUSE_MENU.get(), containerId);
        this.inputPos = inputPos;
    }

    public BlockPos inputPos() {
        return inputPos;
    }

    @Override
    public boolean stillValid(Player player) {
        return inputPos.equals(BlockPos.ZERO) || player.blockPosition().closerThan(inputPos, 8.0D);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}

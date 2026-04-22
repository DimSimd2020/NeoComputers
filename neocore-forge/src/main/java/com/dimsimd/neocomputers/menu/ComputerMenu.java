package com.dimsimd.neocomputers.menu;

import com.dimsimd.neocomputers.component.NeoComponents;
import com.dimsimd.neocomputers.component.data.CpuData;
import com.dimsimd.neocomputers.component.data.MotherboardData;
import com.dimsimd.neocomputers.component.data.RamData;
import com.dimsimd.neocomputers.item.ItemDeferredRegister;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class ComputerMenu extends AbstractContainerMenu {
    public static final int MOTHERBOARD_SLOT = 0;
    public static final int CPU_SLOT = 1;
    public static final int RAM_SLOT_START = 2;
    public static final int RAM_SLOT_COUNT = 4;
    public static final int PCIE_SLOT_START = RAM_SLOT_START + RAM_SLOT_COUNT;
    public static final int PCIE_SLOT_COUNT = 2;
    public static final int SATA_SLOT_START = PCIE_SLOT_START + PCIE_SLOT_COUNT;
    public static final int SATA_SLOT_COUNT = 2;
    public static final int MACHINE_SLOT_COUNT = SATA_SLOT_START + SATA_SLOT_COUNT;

    private static final int PLAYER_INVENTORY_START = MACHINE_SLOT_COUNT;
    private static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 27;
    private static final int HOTBAR_END = PLAYER_INVENTORY_END + 9;

    private final Container container;

    public ComputerMenu(int containerId, Inventory playerInventory) {
        this(null, containerId, playerInventory, new SimpleContainer(MACHINE_SLOT_COUNT));
    }

    public ComputerMenu(@Nullable MenuType<?> menuType, int containerId, Inventory playerInventory, Container container) {
        super(menuType, containerId);
        checkContainerSize(container, MACHINE_SLOT_COUNT);
        this.container = container;
        this.container.startOpen(playerInventory.player);

        addSlot(new Slot(container, MOTHERBOARD_SLOT, 80, 32) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.get(NeoComponents.MOTHERBOARD_DATA.get()) != null;
            }
        });
        addSlot(new DynamicHardwareSlot(container, CPU_SLOT, 80, 14, SlotKind.CPU, 0));

        for (int i = 0; i < RAM_SLOT_COUNT; i++) {
            addSlot(new DynamicHardwareSlot(container, RAM_SLOT_START + i, 26 + i * 18, 56, SlotKind.RAM, i));
        }

        for (int i = 0; i < PCIE_SLOT_COUNT; i++) {
            addSlot(new DynamicHardwareSlot(container, PCIE_SLOT_START + i, 134, 20 + i * 18, SlotKind.PCIE, i));
        }

        for (int i = 0; i < SATA_SLOT_COUNT; i++) {
            addSlot(new DynamicHardwareSlot(container, SATA_SLOT_START + i, 8, 20 + i * 18, SlotKind.SATA, i));
        }

        addPlayerInventorySlots(playerInventory);
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack sourceStack = slot.getItem();
        ItemStack sourceCopy = sourceStack.copy();

        if (index < MACHINE_SLOT_COUNT) {
            if (!moveItemStackTo(sourceStack, PLAYER_INVENTORY_START, HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveFromPlayerToHardware(sourceStack)) {
            return ItemStack.EMPTY;
        }

        if (sourceStack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        return sourceCopy;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }

    private boolean moveFromPlayerToHardware(ItemStack stack) {
        if (stack.get(NeoComponents.MOTHERBOARD_DATA.get()) != null) {
            return moveItemStackTo(stack, MOTHERBOARD_SLOT, MOTHERBOARD_SLOT + 1, false);
        }
        if (stack.get(NeoComponents.CPU_DATA.get()) != null) {
            return moveItemStackTo(stack, CPU_SLOT, CPU_SLOT + 1, false);
        }
        if (stack.get(NeoComponents.RAM_DATA.get()) != null) {
            return moveItemStackTo(stack, RAM_SLOT_START, RAM_SLOT_START + RAM_SLOT_COUNT, false);
        }
        if (isPcieDevice(stack)) {
            return moveItemStackTo(stack, PCIE_SLOT_START, PCIE_SLOT_START + PCIE_SLOT_COUNT, false);
        }
        return moveItemStackTo(stack, SATA_SLOT_START, SATA_SLOT_START + SATA_SLOT_COUNT, false);
    }

    @Nullable
    private MotherboardData motherboardData() {
        ItemStack motherboardStack = container.getItem(MOTHERBOARD_SLOT);
        if (motherboardStack.isEmpty()) {
            return null;
        }
        return motherboardStack.get(NeoComponents.MOTHERBOARD_DATA.get());
    }

    private boolean isPcieDevice(ItemStack stack) {
        return stack.is(ItemDeferredRegister.GPU_BASIC.get()) || stack.is(ItemDeferredRegister.NETWORK_CARD.get());
    }

    private void addPlayerInventorySlots(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = col + row * 9 + 9;
                int x = 8 + col * 18;
                int y = 84 + row * 18;
                addSlot(new Slot(inventory, slotIndex, x, y));
            }
        }

        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        }
    }

    private enum SlotKind {
        CPU,
        RAM,
        PCIE,
        SATA
    }

    private final class DynamicHardwareSlot extends Slot {
        private final SlotKind slotKind;
        private final int localIndex;

        private DynamicHardwareSlot(Container container, int slot, int x, int y, SlotKind slotKind, int localIndex) {
            super(container, slot, x, y);
            this.slotKind = slotKind;
            this.localIndex = localIndex;
        }

        @Override
        public boolean isActive() {
            MotherboardData motherboardData = motherboardData();
            if (motherboardData == null) {
                return false;
            }

            return switch (slotKind) {
                case CPU -> true;
                case RAM -> localIndex < motherboardData.ramSlots();
                case PCIE -> localIndex < motherboardData.pcieSlots();
                case SATA -> localIndex < motherboardData.sataSlots();
            };
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (!isActive()) {
                return false;
            }

            MotherboardData motherboardData = motherboardData();
            if (motherboardData == null) {
                return false;
            }

            return switch (slotKind) {
                case CPU -> {
                    CpuData cpuData = stack.get(NeoComponents.CPU_DATA.get());
                    yield cpuData != null && cpuData.socket().equalsIgnoreCase(motherboardData.socket());
                }
                case RAM -> stack.get(NeoComponents.RAM_DATA.get()) != null;
                case PCIE -> isPcieDevice(stack);
                case SATA -> true;
            };
        }
    }
}

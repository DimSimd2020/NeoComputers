package com.dimsimd.neocomputers.menu;

import com.dimsimd.neocomputers.NeoComputers;
import com.dimsimd.neocomputers.block.ComputerBlockEntity;
import com.dimsimd.neocomputers.component.NeoComponents;
import com.dimsimd.neocomputers.component.data.CpuData;
import com.dimsimd.neocomputers.component.data.MotherboardData;
import com.dimsimd.neocomputers.component.data.StorageData;
import com.dimsimd.neocomputers.item.ItemDeferredRegister;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class ComputerMenu extends AbstractContainerMenu {
    public static final int TOGGLE_POWER_BUTTON_ID = 0;

    public static final int MOTHERBOARD_SLOT = 0;
    public static final int CPU_SLOT = 1;
    public static final int RAM_SLOT_START = 2;
    public static final int RAM_SLOT_COUNT = 4;
    public static final int PCIE_SLOT_START = RAM_SLOT_START + RAM_SLOT_COUNT;
    public static final int PCIE_SLOT_COUNT = 2;
    public static final int SATA_SLOT_START = PCIE_SLOT_START + PCIE_SLOT_COUNT;
    public static final int SATA_SLOT_COUNT = 4;
    public static final int BIOS_SLOT = SATA_SLOT_START + SATA_SLOT_COUNT;
    public static final int MACHINE_SLOT_COUNT = BIOS_SLOT + 1;

    private static final int PLAYER_INVENTORY_START = MACHINE_SLOT_COUNT;
    private static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 27;
    private static final int HOTBAR_END = PLAYER_INVENTORY_END + 9;

    private final Container container;
    @Nullable
    private final ComputerBlockEntity computerBlockEntity;
    private final ContainerData powerData;

    public ComputerMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(MACHINE_SLOT_COUNT), null, new SimpleContainerData(1));
    }

    public ComputerMenu(int containerId, Inventory playerInventory, ComputerBlockEntity computerBlockEntity) {
        this(containerId, playerInventory, computerBlockEntity, computerBlockEntity, serverData(computerBlockEntity));
    }

    private ComputerMenu(
        int containerId,
        Inventory playerInventory,
        Container container,
        @Nullable ComputerBlockEntity computerBlockEntity,
        ContainerData powerData
    ) {
        super(NeoMenus.COMPUTER_MENU.get(), containerId);
        checkContainerSize(container, MACHINE_SLOT_COUNT);
        checkContainerDataCount(powerData, 1);

        this.container = container;
        this.computerBlockEntity = computerBlockEntity;
        this.powerData = powerData;

        this.container.startOpen(playerInventory.player);
        addDataSlots(this.powerData);

        addSlot(new Slot(container, MOTHERBOARD_SLOT, 98, 58) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return canEditHardware() && stack.get(NeoComponents.MOTHERBOARD_DATA.get()) != null;
            }

            @Override
            public boolean mayPickup(Player player) {
                return canEditHardware();
            }
        });
        addSlot(new DynamicHardwareSlot(container, CPU_SLOT, 82, 56, SlotType.CPU, 0));

        for (int i = 0; i < RAM_SLOT_COUNT; i++) {
            addSlot(new DynamicHardwareSlot(container, RAM_SLOT_START + i, 58 + i * 18, 84, SlotType.RAM, i));
        }

        for (int i = 0; i < PCIE_SLOT_COUNT; i++) {
            addSlot(new DynamicHardwareSlot(container, PCIE_SLOT_START + i, 136, 58 + i * 18, SlotType.PCIE, i));
        }

        for (int i = 0; i < SATA_SLOT_COUNT; i++) {
            addSlot(new DynamicHardwareSlot(container, SATA_SLOT_START + i, 22, 54 + i * 18, SlotType.SATA, i));
        }
        addSlot(new Slot(container, BIOS_SLOT, 154, 22) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return canEditHardware() && stack.get(NeoComponents.FIRMWARE_DATA.get()) != null;
            }

            @Override
            public boolean mayPickup(Player player) {
                return canEditHardware();
            }
        });

        addPlayerInventorySlots(playerInventory);
    }

    @Override
    public boolean stillValid(Player player) {
        if (computerBlockEntity != null) {
            return computerBlockEntity.stillValid(player);
        }
        return true;
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
    public boolean clickMenuButton(Player player, int id) {
        if (id != TOGGLE_POWER_BUTTON_ID || computerBlockEntity == null) {
            return false;
        }

        boolean requestedPower = !computerBlockEntity.isPowered();
        boolean changed = computerBlockEntity.setPowered(requestedPower);
        if (!changed && requestedPower) {
            player.displayClientMessage(computerBlockEntity.powerRequirementMessage().copy().withStyle(ChatFormatting.RED), true);
        }
        broadcastChanges();
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }

    @Nullable
    public MotherboardData motherboardData() {
        ItemStack motherboardStack = container.getItem(MOTHERBOARD_SLOT);
        if (motherboardStack.isEmpty()) {
            return null;
        }
        return motherboardStack.get(NeoComponents.MOTHERBOARD_DATA.get());
    }

    public boolean isPowered() {
        return powerData.get(0) != 0;
    }

    public SlotType slotTypeForIndex(int index) {
        if (index == MOTHERBOARD_SLOT) {
            return SlotType.MOTHERBOARD;
        }
        if (index == CPU_SLOT) {
            return SlotType.CPU;
        }
        if (index >= RAM_SLOT_START && index < RAM_SLOT_START + RAM_SLOT_COUNT) {
            return SlotType.RAM;
        }
        if (index >= PCIE_SLOT_START && index < PCIE_SLOT_START + PCIE_SLOT_COUNT) {
            return SlotType.PCIE;
        }
        if (index >= SATA_SLOT_START && index < SATA_SLOT_START + SATA_SLOT_COUNT) {
            return SlotType.SATA;
        }
        if (index == BIOS_SLOT) {
            return SlotType.BIOS;
        }
        return SlotType.PLAYER_INVENTORY;
    }

    public boolean hasServerBlockEntity() {
        return computerBlockEntity != null;
    }

    private boolean canEditHardware() {
        return !isPowered();
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
        if (stack.get(NeoComponents.FIRMWARE_DATA.get()) != null) {
            return moveItemStackTo(stack, BIOS_SLOT, BIOS_SLOT + 1, false);
        }
        StorageData storageData = stack.get(NeoComponents.STORAGE_DATA.get());
        if (storageData != null) {
            return moveItemStackTo(stack, SATA_SLOT_START, SATA_SLOT_START + SATA_SLOT_COUNT, false);
        }
        return false;
    }

    private boolean isPcieDevice(ItemStack stack) {
        return stack.is(ItemDeferredRegister.GPU_BASIC.get()) || stack.is(ItemDeferredRegister.NETWORK_CARD.get());
    }

    private void addPlayerInventorySlots(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = col + row * 9 + 9;
                int x = 8 + col * 18;
                int y = 212 + row * 18;
                addSlot(new Slot(inventory, slotIndex, x, y));
            }
        }

        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 270));
        }
    }

    private static ContainerData serverData(ComputerBlockEntity computerBlockEntity) {
        return new ContainerData() {
            private int clientValue;

            @Override
            public int get(int index) {
                if (index != 0) {
                    return 0;
                }
                return computerBlockEntity.isPowered() ? 1 : clientValue;
            }

            @Override
            public void set(int index, int value) {
                if (index == 0) {
                    clientValue = value;
                }
            }

            @Override
            public int getCount() {
                return 1;
            }
        };
    }

    public enum SlotType {
        MOTHERBOARD,
        CPU,
        RAM,
        PCIE,
        SATA,
        BIOS,
        PLAYER_INVENTORY
    }

    private final class DynamicHardwareSlot extends Slot {
        private final SlotType slotType;
        private final int localIndex;

        private DynamicHardwareSlot(Container container, int slot, int x, int y, SlotType slotType, int localIndex) {
            super(container, slot, x, y);
            this.slotType = slotType;
            this.localIndex = localIndex;
        }

        @Override
        public boolean isActive() {
            MotherboardData motherboardData = motherboardData();
            if (motherboardData == null) {
                return false;
            }

            return switch (slotType) {
                case CPU -> true;
                case RAM -> localIndex < motherboardData.ramSlots();
                case PCIE -> localIndex < motherboardData.pcieSlots();
                case SATA -> localIndex < motherboardData.sataSlots();
                default -> false;
            };
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (!canEditHardware() || !isActive()) {
                return false;
            }

            MotherboardData motherboardData = motherboardData();
            if (motherboardData == null) {
                return false;
            }

            return switch (slotType) {
                case CPU -> {
                    CpuData cpuData = stack.get(NeoComponents.CPU_DATA.get());
                    yield cpuData != null && cpuData.socket().equalsIgnoreCase(motherboardData.socket());
                }
                case RAM -> stack.get(NeoComponents.RAM_DATA.get()) != null;
                case PCIE -> isPcieDevice(stack);
                case SATA -> stack.get(NeoComponents.STORAGE_DATA.get()) != null;
                default -> false;
            };
        }

        @Override
        public boolean mayPickup(Player player) {
            return canEditHardware();
        }
    }
}

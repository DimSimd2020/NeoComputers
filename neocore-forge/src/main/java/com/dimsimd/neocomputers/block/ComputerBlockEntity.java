package com.dimsimd.neocomputers.block;

import com.dimsimd.neocomputers.NeoComputers;
import com.dimsimd.neocomputers.component.NeoComponents;
import com.dimsimd.neocomputers.component.data.CpuData;
import com.dimsimd.neocomputers.component.data.MotherboardData;
import com.dimsimd.neocomputers.component.data.RamData;
import com.dimsimd.neocomputers.component.data.StorageData;
import com.dimsimd.neocomputers.item.ItemDeferredRegister;
import com.dimsimd.neocomputers.menu.ComputerMenu;
import com.dimsimd.neocomputers.vm.NativeVmRuntime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;

public final class ComputerBlockEntity extends BlockEntity implements Container, MenuProvider {
    private static final int VM_MEMORY_MEGABYTES = 512;
    private static final int MAX_TERMINAL_LINES = 96;
    private static final int VM_SYNC_INTERVAL_TICKS = 5;

    private final NonNullList<ItemStack> items = NonNullList.withSize(ComputerMenu.MACHINE_SLOT_COUNT, ItemStack.EMPTY);
    private long vmHandle = 0L;
    private int vmSyncTicks = 0;

    public ComputerBlockEntity(BlockPos pos, BlockState blockState) {
        super(NeoComputers.COMPUTER_BLOCK_ENTITY.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ComputerBlockEntity blockEntity) {
        blockEntity.tickServer();
    }

    public void destroyVm() {
        if (!hasVm()) {
            return;
        }

        persistVmState();
        long handle = vmHandle;
        vmHandle = 0L;
        NativeVmRuntime.destroyVm(handle);
    }

    public boolean isPowered() {
        return getBlockState().getValue(ComputerBlock.POWERED);
    }

    public boolean hasFirmware() {
        return items.get(ComputerMenu.BIOS_SLOT).get(NeoComponents.FIRMWARE_DATA.get()) != null;
    }

    public boolean hasInstalledOs() {
        return installedOsName() != null;
    }

    public boolean canPowerOn() {
        return powerBlocker() == null;
    }

    public Component powerRequirementMessage() {
        String blocker = powerBlocker();
        if (blocker == null) {
            return Component.literal("Computer is ready");
        }
        return Component.literal(blocker);
    }

    public List<String> terminalLines() {
        if (hasVm()) {
            return splitSnapshotLines(NativeVmRuntime.terminalSnapshot(vmHandle));
        }
        StorageSlot storageSlot = systemStorageSlot();
        if (storageSlot == null) {
            return List.of();
        }
        return storageSlot.data().terminalLog();
    }

    public String prompt() {
        if (hasVm()) {
            return NativeVmRuntime.shellPrompt(vmHandle);
        }
        return hasInstalledOs() ? "root@neocomputer:~#" : "NeoBIOS>";
    }

    public List<String> framebufferLines() {
        if (hasVm()) {
            return splitSnapshotLines(NativeVmRuntime.framebufferSnapshot(vmHandle));
        }
        String osName = installedOsName();
        return List.of(
            "+--------------------------------------+",
            "| " + (osName == null ? "NeoBIOS" : osName),
            "| " + prompt(),
            "+--------------------------------------+"
        );
    }

    @Nullable
    public String installedOsName() {
        if (hasVm()) {
            String installedOs = NativeVmRuntime.installedOs(vmHandle);
            return installedOs.isBlank() ? null : installedOs;
        }
        for (int i = ComputerMenu.SATA_SLOT_START; i < ComputerMenu.SATA_SLOT_START + ComputerMenu.SATA_SLOT_COUNT; i++) {
            StorageData storageData = items.get(i).get(NeoComponents.STORAGE_DATA.get());
            if (storageData != null && storageData.hasInstalledOs()) {
                return storageData.installedOs();
            }
        }
        return null;
    }

    public boolean installOperatingSystem(String osName) {
        if (!hasFirmware() || firstStorageSlot() == null) {
            return false;
        }

        if (!hasVm()) {
            ensureVm();
        }
        NativeVmRuntime.submitCommand(vmHandle, "install tiny");
        persistVmState();
        return true;
    }

    public void handleTerminalCommand(String command) {
        String trimmedCommand = command.trim();
        if (trimmedCommand.isEmpty()) {
            return;
        }

        StorageSlot storageSlot = systemStorageSlot();
        if (storageSlot == null) {
            return;
        }

        if (!isPowered()) {
            writeStorageData(storageSlot.slot(), storageSlot.data().withTerminalLog(appendStoredLine(storageSlot.data().terminalLog(), "Computer is powered off.")));
            return;
        }

        if (!hasVm()) {
            ensureVm();
        }

        NativeVmRuntime.submitCommand(vmHandle, trimmedCommand);
        persistVmState();
        if (NativeVmRuntime.isHalted(vmHandle)) {
            setPowered(false);
        }
    }

    public boolean setPowered(boolean powered) {
        if (level == null) {
            return false;
        }

        BlockState blockState = getBlockState();
        if (blockState.getValue(ComputerBlock.POWERED) == powered) {
            return true;
        }

        if (powered && !canPowerOn()) {
            appendStoredSystemLine("Power-on failed: " + powerBlocker());
            return false;
        }

        level.setBlock(worldPosition, blockState.setValue(ComputerBlock.POWERED, powered), 3);
        setChanged();
        syncToClient();

        if (powered) {
            ensureVm();
            persistVmState();
        } else {
            destroyVm();
            updateConnectedMonitorDisplays(List.of("NO SIGNAL", "Computer is powered off."));
        }
        return true;
    }

    public Component getDisplayName() {
        return Component.translatable("block.neocomputers.computer");
    }

    @Nullable
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ComputerMenu(containerId, playerInventory, this);
    }

    @Override
    public void setRemoved() {
        destroyVm();
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        persistVmState();
        super.saveAdditional(tag, provider);
        ContainerHelper.saveAllItems(tag, items, provider);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        ContainerHelper.loadAllItems(tag, items, provider);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void tickServer() {
        if (!NativeVmRuntime.isAvailable()) {
            return;
        }
        if (!isPowered()) {
            destroyVm();
            return;
        }
        if (!canPowerOn()) {
            setPowered(false);
            return;
        }

        ensureVm();
        NativeVmRuntime.tickVm(vmHandle);
        vmSyncTicks++;
        if (vmSyncTicks >= VM_SYNC_INTERVAL_TICKS) {
            vmSyncTicks = 0;
            persistVmState();
        }
    }

    private void ensureVm() {
        if (hasVm()) {
            return;
        }

        StorageSlot storageSlot = systemStorageSlot();
        if (storageSlot == null) {
            return;
        }
        storageSlot = ensureRuntimeStorage(storageSlot);

        long createdVm = NativeVmRuntime.createVm(VM_MEMORY_MEGABYTES, storageSlot.data().capacityMb(), storageSlot.data().diskImage(), hasNetworkCard());
        if (createdVm == 0L) {
            throw new IllegalStateException("Native create_vm returned NULL for computer at " + worldPosition);
        }

        vmHandle = createdVm;
        vmSyncTicks = 0;
    }

    private boolean hasVm() {
        return vmHandle != 0L;
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack item : items) {
            if (!item.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack itemStack = ContainerHelper.removeItem(items, slot, amount);
        if (!itemStack.isEmpty()) {
            if (isPowered() && slot < ComputerMenu.MACHINE_SLOT_COUNT) {
                setPowered(false);
            }
            setChanged();
            syncToClient();
        }
        return itemStack;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack itemStack = ContainerHelper.takeItem(items, slot);
        if (!itemStack.isEmpty() && isPowered() && slot < ComputerMenu.MACHINE_SLOT_COUNT) {
            setPowered(false);
        }
        return itemStack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        if (isPowered() && slot < ComputerMenu.MACHINE_SLOT_COUNT) {
            setPowered(false);
        }
        setChanged();
        syncToClient();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        if (isPowered()) {
            setPowered(false);
        }
        items.clear();
        setChanged();
        syncToClient();
    }

    private void syncToClient() {
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Nullable
    private String powerBlocker() {
        MotherboardData motherboardData = motherboardData();
        if (motherboardData == null) {
            return "missing motherboard";
        }
        CpuData cpuData = cpuData();
        if (cpuData == null) {
            return "missing CPU";
        }
        if (!cpuData.socket().equalsIgnoreCase(motherboardData.socket())) {
            return "CPU socket mismatch";
        }
        if (ramCount() <= 0) {
            return "missing RAM";
        }
        if (!hasFirmware()) {
            return "missing NeoBIOS firmware card";
        }
        if (firstStorageSlot() == null) {
            return "missing SATA storage";
        }
        if (!NativeVmRuntime.isAvailable()) {
            return "native VM bridge unavailable";
        }
        return null;
    }

    @Nullable
    private MotherboardData motherboardData() {
        return items.get(ComputerMenu.MOTHERBOARD_SLOT).get(NeoComponents.MOTHERBOARD_DATA.get());
    }

    @Nullable
    private CpuData cpuData() {
        return items.get(ComputerMenu.CPU_SLOT).get(NeoComponents.CPU_DATA.get());
    }

    private int ramCount() {
        int count = 0;
        for (int i = ComputerMenu.RAM_SLOT_START; i < ComputerMenu.RAM_SLOT_START + ComputerMenu.RAM_SLOT_COUNT; i++) {
            RamData ramData = items.get(i).get(NeoComponents.RAM_DATA.get());
            if (ramData != null) {
                count++;
            }
        }
        return count;
    }

    private boolean hasNetworkCard() {
        for (int i = ComputerMenu.PCIE_SLOT_START; i < ComputerMenu.PCIE_SLOT_START + ComputerMenu.PCIE_SLOT_COUNT; i++) {
            if (items.get(i).is(ItemDeferredRegister.NETWORK_CARD.get())) {
                return true;
            }
        }
        return false;
    }

    private void writeStorageData(int slot, StorageData storageData) {
        items.get(slot).set(NeoComponents.STORAGE_DATA.get(), storageData);
        setChanged();
        syncToClient();
    }

    private void persistVmState() {
        if (!hasVm()) {
            return;
        }
        StorageSlot storageSlot = systemStorageSlot();
        if (storageSlot == null) {
            return;
        }

        List<String> terminalLines = splitSnapshotLines(NativeVmRuntime.terminalSnapshot(vmHandle));
        List<String> framebufferLines = splitSnapshotLines(NativeVmRuntime.framebufferSnapshot(vmHandle));
        writeStorageData(
            storageSlot.slot(),
            storageSlot.data().withVmState(
                NativeVmRuntime.installedOs(vmHandle),
                terminalLines,
                NativeVmRuntime.bootCount(vmHandle),
                NativeVmRuntime.diskImage(vmHandle)
            )
        );
        updateConnectedMonitorDisplays(framebufferLines);
    }

    private void appendStoredSystemLine(String line) {
        StorageSlot storageSlot = firstStorageSlot();
        if (storageSlot == null) {
            return;
        }
        writeStorageData(storageSlot.slot(), storageSlot.data().withTerminalLog(appendStoredLine(storageSlot.data().terminalLog(), line)));
    }

    private static List<String> appendStoredLine(List<String> lines, String line) {
        List<String> nextLines = new ArrayList<>(lines);
        nextLines.add(line);
        return clampLog(nextLines);
    }

    private static List<String> clampLog(List<String> lines) {
        int first = Math.max(0, lines.size() - MAX_TERMINAL_LINES);
        return List.copyOf(lines.subList(first, lines.size()));
    }

    private static List<String> splitSnapshotLines(String snapshot) {
        if (snapshot == null || snapshot.isBlank()) {
            return List.of();
        }
        return clampLog(Arrays.stream(snapshot.split("\\R"))
            .filter(line -> !line.isBlank())
            .toList());
    }

    private void updateConnectedMonitorDisplays(List<String> lines) {
        if (level == null || level.isClientSide) {
            return;
        }
        for (PeripheralBlockEntity peripheralBlockEntity : PeripheralCableNetwork.findConnectedPeripherals(level, worldPosition, PeripheralBlock.PeripheralKind.MONITOR)) {
            peripheralBlockEntity.setDisplayLines(lines);
        }
    }

    @Nullable
    private StorageSlot systemStorageSlot() {
        for (int i = ComputerMenu.SATA_SLOT_START; i < ComputerMenu.SATA_SLOT_START + ComputerMenu.SATA_SLOT_COUNT; i++) {
            StorageData storageData = items.get(i).get(NeoComponents.STORAGE_DATA.get());
            if (storageData != null && storageData.hasInstalledOs()) {
                return new StorageSlot(i, storageData);
            }
        }
        return firstStorageSlot();
    }

    @Nullable
    private StorageSlot firstStorageSlot() {
        for (int i = ComputerMenu.SATA_SLOT_START; i < ComputerMenu.SATA_SLOT_START + ComputerMenu.SATA_SLOT_COUNT; i++) {
            StorageData storageData = items.get(i).get(NeoComponents.STORAGE_DATA.get());
            if (storageData != null) {
                return new StorageSlot(i, storageData);
            }
        }
        return null;
    }

    private StorageSlot ensureRuntimeStorage(StorageSlot storageSlot) {
        if (!NativeVmRuntime.usesQemuBackend()) {
            return storageSlot;
        }
        String diskImage = storageSlot.data().diskImage();
        if (diskImage.startsWith(NativeVmRuntime.qemuDiskPrefix())) {
            return storageSlot;
        }

        String diskName = UUID.randomUUID() + ".img";
        java.nio.file.Path diskPath = qemuDiskDirectory().resolve(diskName).toAbsolutePath().normalize();
        StorageData updatedData = storageSlot.data().withDiskImage(NativeVmRuntime.qemuDiskPrefix() + diskPath);
        writeStorageData(storageSlot.slot(), updatedData);
        return new StorageSlot(storageSlot.slot(), updatedData);
    }

    private java.nio.file.Path qemuDiskDirectory() {
        if (level != null && level.getServer() != null) {
            return level.getServer().getWorldPath(LevelResource.ROOT).resolve("neocomputers").resolve("disks");
        }
        return java.nio.file.Path.of("neocomputers", "disks");
    }

    private record StorageSlot(int slot, StorageData data) {
    }
}

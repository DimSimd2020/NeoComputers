package com.dimsimd.neocomputers.network;

import com.dimsimd.neocomputers.NeoComputers;
import com.dimsimd.neocomputers.block.ComputerBlockEntity;
import com.dimsimd.neocomputers.block.PeripheralBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public record KeyboardInputPayload(BlockPos inputPos, String command) implements CustomPacketPayload {
    public static final Type<KeyboardInputPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NeoComputers.MOD_ID, "keyboard_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, KeyboardInputPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, payload) -> {
            buffer.writeBlockPos(payload.inputPos());
            buffer.writeUtf(payload.command(), 128);
        },
        buffer -> new KeyboardInputPayload(buffer.readBlockPos(), buffer.readUtf(128))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(KeyboardInputPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer serverPlayer)) {
            return;
        }

        context.enqueueWork(() -> {
            ComputerBlockEntity computer = linkedComputer(serverPlayer.level(), payload.inputPos());
            if (computer == null) {
                return;
            }

            computer.handleTerminalCommand(payload.command());
        });
    }

    private static ComputerBlockEntity linkedComputer(Level level, BlockPos inputPos) {
        BlockEntity blockEntity = level.getBlockEntity(inputPos);
        if (blockEntity instanceof PeripheralBlockEntity peripheralBlockEntity) {
            ComputerBlockEntity linked = peripheralBlockEntity.linkedComputer();
            if (linked != null) {
                return linked;
            }
        }
        return com.dimsimd.neocomputers.block.PeripheralCableNetwork.findConnectedComputer(level, inputPos);
    }
}

package dev.xt9y.projectred.network;

import dev.xt9y.projectred.ProjectRed263;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record GateConfigEditPayload(
        BlockPos pos,
        int slot,
        String data
) implements CustomPacketPayload {
    public static final Type<GateConfigEditPayload> TYPE =
            new Type<>(ProjectRed263.id("gate_config_edit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GateConfigEditPayload> CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC,
                    GateConfigEditPayload::pos,
                    ByteBufCodecs.INT,
                    GateConfigEditPayload::slot,
                    ByteBufCodecs.STRING_UTF8,
                    GateConfigEditPayload::data,
                    GateConfigEditPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

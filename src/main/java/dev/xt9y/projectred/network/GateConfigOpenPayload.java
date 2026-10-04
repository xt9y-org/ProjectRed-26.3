package dev.xt9y.projectred.network;

import dev.xt9y.projectred.ProjectRed263;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record GateConfigOpenPayload(
        BlockPos pos,
        int slot,
        String data
) implements CustomPacketPayload {
    public static final Type<GateConfigOpenPayload> TYPE =
            new Type<>(ProjectRed263.id("gate_config_open"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GateConfigOpenPayload> CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC,
                    GateConfigOpenPayload::pos,
                    ByteBufCodecs.INT,
                    GateConfigOpenPayload::slot,
                    ByteBufCodecs.STRING_UTF8,
                    GateConfigOpenPayload::data,
                    GateConfigOpenPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

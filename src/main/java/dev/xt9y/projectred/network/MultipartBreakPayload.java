package dev.xt9y.projectred.network;

import dev.xt9y.projectred.ProjectRed263;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record MultipartBreakPayload(
        BlockPos pos,
        int slot
) implements CustomPacketPayload {
    public static final Type<MultipartBreakPayload> TYPE =
            new Type<>(ProjectRed263.id("multipart_break"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MultipartBreakPayload> CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC,
                    MultipartBreakPayload::pos,
                    ByteBufCodecs.INT,
                    MultipartBreakPayload::slot,
                    MultipartBreakPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

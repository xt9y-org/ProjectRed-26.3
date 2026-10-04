package dev.xt9y.projectred.network;

import dev.xt9y.projectred.multipart.MultipartBlockEntity;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class PRNetworking {
    private static boolean initialized;

    public static void initialize() {
        if (initialized) return;
        initialized = true;

        PayloadTypeRegistry.serverboundPlay().register(
                MultipartBreakPayload.TYPE,
                MultipartBreakPayload.CODEC
        );

        ServerPlayNetworking.registerGlobalReceiver(
                MultipartBreakPayload.TYPE,
                (payload, context) -> breakPart(context.player(), payload)
        );
    }

    private static void breakPart(
            ServerPlayer player,
            MultipartBreakPayload payload
    ) {
        if (player.blockPosition().distManhattan(payload.pos()) > 8) return;
        if (!(player.level().getBlockEntity(payload.pos())
                instanceof MultipartBlockEntity multipart)) return;
        if (!multipart.hasSlot(payload.slot())) return;

        multipart.removeAndDrop(payload.slot());
    }

    private PRNetworking() {}
}

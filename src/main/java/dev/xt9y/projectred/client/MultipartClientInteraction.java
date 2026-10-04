package dev.xt9y.projectred.client;

import dev.xt9y.projectred.multipart.MultipartBlockEntity;
import dev.xt9y.projectred.network.MultipartBreakPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;

public final class MultipartClientInteraction {
    private static boolean initialized;

    public static void initialize() {
        if (initialized) return;
        initialized = true;

        AttackBlockCallback.EVENT.register(
                (player, level, hand, pos, direction) -> {
                    if (!level.isClientSide()
                            || !(level.getBlockEntity(pos)
                            instanceof MultipartBlockEntity multipart)) {
                        return InteractionResult.PASS;
                    }

                    Minecraft minecraft = Minecraft.getInstance();
                    if (!(minecraft.hitResult instanceof BlockHitResult hit)
                            || !hit.getBlockPos().equals(pos)) {
                        return InteractionResult.PASS;
                    }

                    int slot = multipart.slotFromHit(hit.getLocation());
                    if (slot < 0 || !multipart.hasSlot(slot)) {
                        return InteractionResult.PASS;
                    }

                    ClientPlayNetworking.send(new MultipartBreakPayload(pos, slot));
                    return InteractionResult.SUCCESS;
                }
        );
    }

    private MultipartClientInteraction() {}
}

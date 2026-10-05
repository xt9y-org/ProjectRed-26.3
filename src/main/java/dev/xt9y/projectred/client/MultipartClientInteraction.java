package dev.xt9y.projectred.client;

import dev.xt9y.projectred.content.PRContent;
import dev.xt9y.projectred.multipart.MultipartBlockEntity;
import dev.xt9y.projectred.multipart.Part;
import dev.xt9y.projectred.network.MultipartBreakPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.client.player.ClientPickBlockGatherCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
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

                    // Tell the server which multipart is being mined, but
                    // leave the vanilla attack untouched so normal mining
                    // progress/hardness still applies. The server intercepts
                    // the completed block break and removes only this slot.
                    ClientPlayNetworking.send(new MultipartBreakPayload(pos, slot));
                    return player.isCreative()
                            ? InteractionResult.SUCCESS
                            : InteractionResult.PASS;
                }

        ClientPickBlockGatherCallback.EVENT.register((player, result) -> {
            if (!(result instanceof BlockHitResult hit)
                    || result.getType() != HitResult.Type.BLOCK) {
                return ItemStack.EMPTY;
            }

            if (!(player.level().getBlockEntity(hit.getBlockPos())
                    instanceof MultipartBlockEntity multipart)) {
                return ItemStack.EMPTY;
            }

            int slot = multipart.slotFromHit(hit.getLocation());
            Part part = multipart.part(slot);
            if (part == null) {
                return ItemStack.EMPTY;
            }

            return PRContent.stackFor(part);
        });
        );
    }

    private MultipartClientInteraction() {}
}

package dev.xt9y.projectred.multipart;

import dev.xt9y.projectred.integration.GatePart;
import dev.xt9y.projectred.integration.GateType;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

public final class MultipartInteractions {
    private static boolean initialized;

    public static void initialize() {
        if (initialized) return;
        initialized = true;

        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!(level.getBlockEntity(hit.getBlockPos())
                    instanceof MultipartBlockEntity multipart)) {
                return InteractionResult.PASS;
            }

            int slot = multipart.slotFromHit(hit.getLocation());
            Part part = multipart.part(slot);
            if (!(part instanceof GatePart gate)
                    || gate.type() != GateType.SEGMENT_DISPLAY) {
                return InteractionResult.PASS;
            }

            ItemStack held = player.getItemInHand(hand);
            DyeColor dye = held.get(DataComponents.DYE);
            if (dye == null) {
                return InteractionResult.PASS;
            }

            int color = dye.getId();

            // Upstream ProjectRed reserves black as the unlit display
            // background and does not allow it as the active digit colour.
            if (color == 15) {
                return InteractionResult.PASS;
            }

            if (!level.isClientSide()) {
                gate.setDisplayColor(color);
                multipart.markPartChanged();
            }
            return InteractionResult.SUCCESS;
        });
    }

    private MultipartInteractions() {}
}

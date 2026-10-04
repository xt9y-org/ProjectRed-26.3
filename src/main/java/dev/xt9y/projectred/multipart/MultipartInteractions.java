package dev.xt9y.projectred.multipart;

import dev.xt9y.projectred.integration.GatePart;
import dev.xt9y.projectred.integration.GateType;
import dev.xt9y.projectred.item.ScrewdriverItem;
import dev.xt9y.projectred.network.PRNetworking;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
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
            if (!(part instanceof GatePart gate)) {
                return InteractionResult.PASS;
            }

            ItemStack held = player.getItemInHand(hand);
            if (held.getItem() instanceof ScrewdriverItem) {
                return InteractionResult.PASS;
            }

            if (gate.type() == GateType.SEGMENT_DISPLAY) {
                DyeColor dye = held.get(DataComponents.DYE);
                if (dye == null || dye.getId() == 15) {
                    return InteractionResult.PASS;
                }

                if (!level.isClientSide()) {
                    gate.setDisplayColor(dye.getId());
                    multipart.markPartChanged();
                }
                return InteractionResult.SUCCESS;
            }

            if (gate.type() == GateType.BUS_INPUT_PANEL) {
                if (!level.isClientSide()) {
                    gate.togglePanelBit(
                            multipart.panelBit(gate, hit.getLocation())
                    );
                    multipart.markPartChanged();
                }
                return InteractionResult.SUCCESS;
            }

            if (gate.type() == GateType.TOGGLE_LATCH
                    || gate.type() == GateType.REPEATER) {
                if (!level.isClientSide()) {
                    gate.activate();
                    multipart.markPartChanged();
                }
                return InteractionResult.SUCCESS;
            }

            if (gate.type() == GateType.TIMER
                    || gate.type() == GateType.SEQUENCER
                    || gate.type() == GateType.STATE_CELL
                    || gate.type() == GateType.COUNTER) {
                if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                    PRNetworking.openGateConfig(
                            serverPlayer,
                            multipart,
                            slot,
                            gate
                    );
                }
                return InteractionResult.SUCCESS;
            }

            return InteractionResult.PASS;
        });
    }

    private MultipartInteractions() {}
}

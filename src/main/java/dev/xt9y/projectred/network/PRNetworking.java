package dev.xt9y.projectred.network;

import dev.xt9y.projectred.content.PRContent;
import dev.xt9y.projectred.integration.GatePart;
import dev.xt9y.projectred.integration.GateType;
import dev.xt9y.projectred.multipart.MultipartBlockEntity;
import dev.xt9y.projectred.multipart.Part;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.PlayerPickItemEvents;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class PRNetworking {
    private static boolean initialized;
    private static final Map<UUID, BreakTarget> BREAK_TARGETS = new HashMap<>();

    private record BreakTarget(BlockPos pos, int slot) {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;

        PayloadTypeRegistry.serverboundPlay().register(
                MultipartBreakPayload.TYPE,
                MultipartBreakPayload.CODEC
        );
        PayloadTypeRegistry.serverboundPlay().register(
                GateConfigEditPayload.TYPE,
                GateConfigEditPayload.CODEC
        );
        PayloadTypeRegistry.clientboundPlay().register(
                GateConfigOpenPayload.TYPE,
                GateConfigOpenPayload.CODEC
        );

        ServerPlayNetworking.registerGlobalReceiver(
                MultipartBreakPayload.TYPE,
                (payload, context) -> selectBreakPart(context.player(), payload)
        );

        PlayerBlockBreakEvents.BEFORE.register(
                (level, player, pos, state, blockEntity) ->
                        beforeBlockBreak(player, pos, blockEntity)
        );

        PlayerPickItemEvents.BLOCK.register(
                (player, pos, state, includeData) ->
                        pickMultipartPart(player, pos)
        );
        ServerPlayNetworking.registerGlobalReceiver(
                GateConfigEditPayload.TYPE,
                (payload, context) -> editGate(context.player(), payload)
        );
    }

    private static void selectBreakPart(
            ServerPlayer player,
            MultipartBreakPayload payload
    ) {
        if (player.blockPosition().distManhattan(payload.pos()) > 8) return;
        if (!player.mayBuild()) return;
        if (!(player.level().getBlockEntity(payload.pos())
                instanceof MultipartBlockEntity multipart)) return;
        if (!multipart.hasSlot(payload.slot())) return;

        if (player.isCreative()) {
            BREAK_TARGETS.remove(player.getUUID());
            multipart.remove(payload.slot());
            return;
        }

        BREAK_TARGETS.put(
                player.getUUID(),
                new BreakTarget(payload.pos().immutable(), payload.slot())
        );
    }

    private static boolean beforeBlockBreak(
            net.minecraft.world.entity.player.Player player,
            BlockPos pos,
            net.minecraft.world.level.block.entity.BlockEntity blockEntity
    ) {
        if (!(blockEntity instanceof MultipartBlockEntity multipart)) {
            BREAK_TARGETS.remove(player.getUUID());
            return true;
        }

        // Never allow vanilla to destroy the multipart container directly.
        // A modded client sends the exact selected part when mining starts.
        BreakTarget target = BREAK_TARGETS.get(player.getUUID());
        if (target == null || !target.pos().equals(pos)) {
            return false;
        }

        BREAK_TARGETS.remove(player.getUUID());

        if (!multipart.hasSlot(target.slot())) {
            return false;
        }

        if (player.isCreative()) {
            multipart.remove(target.slot());
        } else {
            multipart.removeAndDrop(target.slot());
        }

        return false;
    }

    private static net.minecraft.world.item.ItemStack pickMultipartPart(
            ServerPlayer player,
            BlockPos pos
    ) {
        if (!(player.level().getBlockEntity(pos)
                instanceof MultipartBlockEntity multipart)) {
            return null;
        }

        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getViewVector(1.0F).scale(8.0D));
        BlockHitResult hit = player.level().clip(
                new ClipContext(
                        start,
                        end,
                        ClipContext.Block.OUTLINE,
                        ClipContext.Fluid.NONE,
                        player
                )
        );

        if (!hit.getBlockPos().equals(pos)) {
            return null;
        }

        int slot = multipart.slotFromHit(hit.getLocation());
        Part part = multipart.part(slot);
        if (part == null) {
            return null;
        }

        return PRContent.stackFor(part);
    }

    public static boolean openGateConfig(
            ServerPlayer player,
            MultipartBlockEntity multipart,
            int slot,
            GatePart gate
    ) {
        if (!isConfigurable(gate.type())) return false;

        String data;
        if (gate.type() == GateType.COUNTER) {
            data = "counter|"
                    + gate.counterMax() + "|"
                    + gate.counterIncrement() + "|"
                    + gate.counterDecrement() + "|"
                    + gate.counterValue();
        } else {
            data = "timer|" + gate.timerPeriod();
        }

        ServerPlayNetworking.send(
                player,
                new GateConfigOpenPayload(
                        multipart.getBlockPos(),
                        slot,
                        data
                )
        );
        return true;
    }

    private static void editGate(
            ServerPlayer player,
            GateConfigEditPayload payload
    ) {
        if (player.blockPosition().distManhattan(payload.pos()) > 8) return;
        if (!(player.level().getBlockEntity(payload.pos())
                instanceof MultipartBlockEntity multipart)) return;

        Part part = multipart.part(payload.slot());
        if (!(part instanceof GatePart gate) || !isConfigurable(gate.type())) {
            return;
        }

        String[] fields = payload.data().split("\\|", -1);
        try {
            if (fields.length == 2
                    && fields[0].equals("timer")
                    && gate.type() != GateType.COUNTER) {
                gate.adjustTimer(Integer.parseInt(fields[1]));
            } else if (fields.length == 3
                    && fields[0].equals("counter")
                    && gate.type() == GateType.COUNTER) {
                int action = Integer.parseInt(fields[1]);
                int delta = Integer.parseInt(fields[2]);
                switch (action) {
                    case 0 -> gate.adjustCounterMax(delta);
                    case 1 -> gate.adjustCounterIncrement(delta);
                    case 2 -> gate.adjustCounterDecrement(delta);
                    default -> {
                        return;
                    }
                }
            } else {
                return;
            }
        } catch (NumberFormatException ignored) {
            return;
        }

        multipart.markPartChanged();
    }

    private static boolean isConfigurable(GateType type) {
        return type == GateType.TIMER
                || type == GateType.SEQUENCER
                || type == GateType.STATE_CELL
                || type == GateType.COUNTER;
    }

    private PRNetworking() {}
}

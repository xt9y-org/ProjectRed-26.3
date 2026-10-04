package dev.xt9y.projectred.network;

import dev.xt9y.projectred.integration.GatePart;
import dev.xt9y.projectred.integration.GateType;
import dev.xt9y.projectred.multipart.MultipartBlockEntity;
import dev.xt9y.projectred.multipart.Part;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

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

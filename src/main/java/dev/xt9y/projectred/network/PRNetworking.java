package dev.xt9y.projectred.network;

import dev.xt9y.projectred.integration.GatePart;
import dev.xt9y.projectred.integration.GateType;
import dev.xt9y.projectred.multipart.MultipartBlockEntity;
import dev.xt9y.projectred.multipart.Part;
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
                (payload, context) -> breakPart(context.player(), payload)
        );
        ServerPlayNetworking.registerGlobalReceiver(
                GateConfigEditPayload.TYPE,
                (payload, context) -> editGate(context.player(), payload)
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

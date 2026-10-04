package dev.xt9y.projectred.client;

import dev.xt9y.projectred.network.GateConfigOpenPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class GateConfigClient {
    private static boolean initialized;

    public static void initialize() {
        if (initialized) return;
        initialized = true;

        ClientPlayNetworking.registerGlobalReceiver(
                GateConfigOpenPayload.TYPE,
                (payload, context) -> context.client().execute(() -> {
                    String[] fields = payload.data().split("\\|", -1);
                    if (fields.length == 2 && fields[0].equals("timer")) {
                        try {
                            int period = Integer.parseInt(fields[1]);
                            context.client().gui.setScreen(
                                    new TimerConfigScreen(
                                            payload.pos(),
                                            payload.slot(),
                                            period
                                    )
                            );
                        } catch (NumberFormatException ignored) {
                        }
                    } else if (fields.length == 5 && fields[0].equals("counter")) {
                        try {
                            context.client().gui.setScreen(
                                    new CounterConfigScreen(
                                            payload.pos(),
                                            payload.slot(),
                                            Integer.parseInt(fields[1]),
                                            Integer.parseInt(fields[2]),
                                            Integer.parseInt(fields[3]),
                                            Integer.parseInt(fields[4])
                                    )
                            );
                        } catch (NumberFormatException ignored) {
                        }
                    }
                })
        );
    }

    private GateConfigClient() {}
}

package dev.xt9y.projectred.client;

import dev.xt9y.projectred.network.GateConfigEditPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public final class TimerConfigScreen extends Screen {
    private static final int[] DELTAS = { -200, -20, -1, 1, 20, 200 };
    private static final String[] LABELS = {
            "-10s", "-1s", "-50ms", "+50ms", "+1s", "+10s"
    };

    private final BlockPos pos;
    private final int slot;
    private int period;

    public TimerConfigScreen(BlockPos pos, int slot, int period) {
        super(Component.literal("ProjectRed Timer"));
        this.pos = pos;
        this.slot = slot;
        this.period = period;
    }

    @Override
    protected void init() {
        super.init();

        int total = 6 * 40 + 5 * 2;
        int startX = (this.width - total) / 2;
        int y = this.height / 2 + 10;

        for (int i = 0; i < DELTAS.length; i++) {
            int delta = DELTAS[i];
            this.addRenderableWidget(
                    Button.builder(
                            Component.literal(LABELS[i]),
                            button -> adjust(delta)
                    ).bounds(startX + i * 42, y, 40, 20).build()
            );
        }
    }

    private void adjust(int delta) {
        period = Math.max(4, Math.min(20 * 60 * 60, period + delta));
        ClientPlayNetworking.send(
                new GateConfigEditPayload(
                        pos,
                        slot,
                        "timer|" + delta
                )
        );
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(
                this.font,
                Component.literal(
                        String.format("Timer interval: %.2fs", period * 0.05)
                ),
                this.width / 2,
                this.height / 2 - 18,
                -1
        );
    }
}

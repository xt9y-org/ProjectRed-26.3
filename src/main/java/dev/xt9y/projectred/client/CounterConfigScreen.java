package dev.xt9y.projectred.client;

import dev.xt9y.projectred.network.GateConfigEditPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public final class CounterConfigScreen extends Screen {
    private static final int[] DELTAS = { -10, -5, -1, 1, 5, 10 };

    private final BlockPos pos;
    private final int slot;
    private int maximum;
    private int increment;
    private int decrement;
    private int value;

    public CounterConfigScreen(
            BlockPos pos,
            int slot,
            int maximum,
            int increment,
            int decrement,
            int value
    ) {
        super(Component.literal("ProjectRed Counter"));
        this.pos = pos;
        this.slot = slot;
        this.maximum = maximum;
        this.increment = increment;
        this.decrement = decrement;
        this.value = value;
    }

    @Override
    protected void init() {
        super.init();

        int total = 6 * 40 + 5 * 2;
        int startX = (this.width - total) / 2;
        int startY = this.height / 2 - 42;

        for (int row = 0; row < 3; row++) {
            final int action = row;
            int y = startY + row * 40;
            for (int i = 0; i < DELTAS.length; i++) {
                int delta = DELTAS[i];
                String label = delta > 0 ? "+" + delta : Integer.toString(delta);
                this.addRenderableWidget(
                        Button.builder(
                                Component.literal(label),
                                button -> adjust(action, delta)
                        ).bounds(startX + i * 42, y + 14, 40, 20).build()
                );
            }
        }
    }

    private void adjust(int action, int delta) {
        switch (action) {
            case 0 -> {
                maximum = Math.max(1, Math.min(32767, maximum + delta));
                value = Math.min(value, maximum);
            }
            case 1 -> increment = Math.max(
                    1,
                    Math.min(maximum, increment + delta)
            );
            case 2 -> decrement = Math.max(
                    1,
                    Math.min(maximum, decrement + delta)
            );
            default -> {
                return;
            }
        }

        ClientPlayNetworking.send(
                new GateConfigEditPayload(
                        pos,
                        slot,
                        "counter|" + action + "|" + delta
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

        int y = this.height / 2 - 42;
        graphics.centeredText(
                this.font,
                Component.literal("Maximum: " + maximum),
                this.width / 2,
                y,
                -1
        );
        graphics.centeredText(
                this.font,
                Component.literal("Increment: " + increment),
                this.width / 2,
                y + 40,
                -1
        );
        graphics.centeredText(
                this.font,
                Component.literal("Decrement: " + decrement),
                this.width / 2,
                y + 80,
                -1
        );
        graphics.centeredText(
                this.font,
                Component.literal("State: " + value),
                this.width / 2,
                y + 120,
                -1
        );
    }
}

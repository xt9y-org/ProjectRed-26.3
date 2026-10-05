package dev.xt9y.projectred.client;

import dev.xt9y.projectred.integration.GatePart;
import dev.xt9y.projectred.integration.GateType;
import dev.xt9y.projectred.multipart.MultipartBlockEntity;
import dev.xt9y.projectred.network.GateConfigEditPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class CounterConfigScreen extends Screen {
    private static final int[] DELTAS = { -10, -5, -1, 1, 5, 10 };
    private static final int[] BUTTON_X = { 5, 46, 87, 129, 170, 211 };
    private static final int GUI_WIDTH = 256;
    private static final int GUI_HEIGHT = 145;
    private static final Identifier BACKGROUND =
            Identifier.fromNamespaceAndPath(
                    "projectred",
                    "textures/integration/gui/counter_gate.png"
            );

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

        int x = (this.width - GUI_WIDTH) / 2;
        int y = (this.height - GUI_HEIGHT) / 2;

        for (int row = 0; row < 3; row++) {
            final int action = row;
            int buttonY = y + 16 + 40 * row;
            for (int i = 0; i < DELTAS.length; i++) {
                int delta = DELTAS[i];
                String label = delta > 0 ? "+" + delta : Integer.toString(delta);
                this.addRenderableWidget(
                        Button.builder(
                                Component.literal(label),
                                button -> adjust(action, delta)
                        ).bounds(x + BUTTON_X[i], buttonY, 40, 20).build()
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
    public void tick() {
        super.tick();

        if (this.minecraft == null
                || this.minecraft.level == null
                || !(this.minecraft.level.getBlockEntity(pos)
                instanceof MultipartBlockEntity multipart)
                || !(multipart.part(slot) instanceof GatePart gate)
                || gate.type() != GateType.COUNTER) {
            this.onClose();
            return;
        }

        maximum = gate.counterMax();
        increment = gate.counterIncrement();
        decrement = gate.counterDecrement();
        value = gate.counterValue();
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        int x = (this.width - GUI_WIDTH) / 2;
        int y = (this.height - GUI_HEIGHT) / 2;

        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                BACKGROUND,
                x,
                y,
                0.0F,
                0.0F,
                GUI_WIDTH,
                GUI_HEIGHT,
                256,
                256
        );

        drawCentered(graphics, "Maximum: " + maximum, y + 5);
        drawCentered(graphics, "Increment: " + increment, y + 45);
        drawCentered(graphics, "Decrement: " + decrement, y + 85);
        drawCentered(graphics, "State: " + value, y + 125);
    }

    private void drawCentered(
            GuiGraphicsExtractor graphics,
            String text,
            int y
    ) {
        graphics.centeredText(
                this.font,
                Component.literal(text),
                this.width / 2,
                y,
                0xFF404040
        );
    }
}

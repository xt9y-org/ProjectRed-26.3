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

public final class TimerConfigScreen extends Screen {
    private static final int[] DELTAS = { -200, -20, -1, 1, 20, 200 };
    private static final String[] LABELS = {
            "-10s", "-1s", "-50ms", "+50ms", "+1s", "+10s"
    };
    private static final int[] BUTTON_X = { 5, 46, 87, 129, 170, 211 };
    private static final int GUI_WIDTH = 256;
    private static final int GUI_HEIGHT = 55;
    private static final Identifier BACKGROUND =
            Identifier.fromNamespaceAndPath(
                    "projectred",
                    "textures/integration/gui/timer_gate.png"
            );

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

        int x = (this.width - GUI_WIDTH) / 2;
        int y = (this.height - GUI_HEIGHT) / 2;

        for (int i = 0; i < DELTAS.length; i++) {
            int delta = DELTAS[i];
            this.addRenderableWidget(
                    Button.builder(
                            Component.literal(LABELS[i]),
                            button -> adjust(delta)
                    ).bounds(x + BUTTON_X[i], y + 25, 40, 20).build()
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
    public void tick() {
        super.tick();

        if (this.minecraft == null
                || this.minecraft.level == null
                || !(this.minecraft.level.getBlockEntity(pos)
                instanceof MultipartBlockEntity multipart)
                || !(multipart.part(slot) instanceof GatePart gate)
                || (gate.type() != GateType.TIMER
                && gate.type() != GateType.SEQUENCER
                && gate.type() != GateType.STATE_CELL)) {
            this.onClose();
            return;
        }

        period = gate.timerPeriod();
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
                GUI_WIDTH,
                GUI_HEIGHT
        );

        graphics.centeredText(
                this.font,
                Component.literal(
                        String.format("Timer interval: %.2fs", period * 0.05)
                ),
                this.width / 2,
                y + 8,
                0xFF404040
        );
    }
}

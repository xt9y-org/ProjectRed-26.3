package dev.xt9y.projectred.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.xt9y.projectred.integration.GatePart;
import dev.xt9y.projectred.integration.GateType;
import dev.xt9y.projectred.multipart.MultipartBlockEntity;
import dev.xt9y.projectred.multipart.Part;
import dev.xt9y.projectred.transmission.WireFamily;
import dev.xt9y.projectred.transmission.WirePart;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class MultipartRenderer implements BlockEntityRenderer<MultipartBlockEntity, MultipartRenderer.State> {
    private static final Identifier GATE_BASE = projectRed("integration/block/base");
    private static final Identifier WIRE_BORDER = projectRed("integration/block/wire_material_border");
    private static final Identifier WIRE_ON = projectRed("integration/block/wire_material_on");
    private static final Identifier SEGMENT_BASE = projectRed("integration/block/segment_display");
    private static final Identifier SEGMENT_DIGIT = projectRed("integration/block/segment_display_digit");

    public MultipartRenderer(BlockEntityRendererProvider.Context context) {}

    public static final class State extends BlockEntityRenderState {
        private List<Visual> parts = List.of();
    }

    private record Visual(
            boolean wire,
            int slot,
            WireFamily family,
            int color,
            boolean powered,
            int connections,
            GateType gateType,
            int gateShape,
            int gateRotation,
            int gateState,
            int bundledMask,
            int panelMask
    ) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(
            MultipartBlockEntity blockEntity,
            State state,
            float partialTick,
            Vec3 cameraPos,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress
    ) {
        BlockEntityRenderState.extractBase(blockEntity, state, breakProgress);
        List<Visual> out = new ArrayList<>();

        for (Part part : blockEntity.parts()) {
            if (part instanceof WirePart wire) {
                boolean powered = wire.spec().family() == WireFamily.BUNDLED
                        ? java.util.Arrays.stream(wire.bundled()).anyMatch(v -> v > 0)
                        : wire.signal() > 0;
                out.add(new Visual(
                        true,
                        part.slot(),
                        wire.spec().family(),
                        wire.spec().color(),
                        powered,
                        blockEntity.visualWireConnections(wire),
                        null,
                        0,
                        0,
                        0,
                        0,
                        0
                ));
            } else if (part instanceof GatePart gate) {
                out.add(new Visual(
                        false,
                        part.slot(),
                        null,
                        -1,
                        (gate.state() & 0xF0) != 0,
                        0,
                        gate.type(),
                        gate.shape(),
                        gate.rotation(),
                        gate.state(),
                        gate.segmentMask(),
                        gate.panelMask()
                ));
            }
        }

        state.parts = out;
    }

    @Override
    public void submit(
            State state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState cameraState
    ) {
        int order = 1;

        for (Visual part : state.parts) {
            if (part.wire) {
                Identifier texture = wireTexture(part);

                collector.order(order++).submitCustomGeometry(
                        poseStack,
                        RenderTypes.entityCutout(texture),
                        (pose, consumer) -> {
                            if (part.slot == Part.CENTER_SLOT) {
                                RenderGeometry.framedWire(
                                        consumer,
                                        pose,
                                        state.lightCoords,
                                        part.connections
                                );
                            } else {
                                float width = part.family == WireFamily.BUNDLED
                                        ? .50F
                                        : part.family == WireFamily.INSULATED
                                                ? .375F
                                                : .25F;
                                RenderGeometry.wireFace(
                                        consumer,
                                        pose,
                                        state.lightCoords,
                                        Direction.values()[part.slot],
                                        width,
                                        .0625F,
                                        part.connections
                                );
                            }
                        }
                );

                if (part.slot == Part.CENTER_SLOT) {
                    collector.order(order++).submitCustomGeometry(
                            poseStack,
                            RenderTypes.entityCutout(WIRE_BORDER),
                            (pose, consumer) -> RenderGeometry.framedWireOverlay(
                                    consumer,
                                    pose,
                                    state.lightCoords,
                                    part.connections
                            )
                    );
                }
                continue;
            }

            Direction attachment = Direction.values()[part.slot];

            collector.order(order++).submitCustomGeometry(
                    poseStack,
                    RenderTypes.entityCutout(GATE_BASE),
                    (pose, consumer) -> RenderGeometry.gateBoard(
                            consumer,
                            pose,
                            state.lightCoords,
                            attachment
                    )
            );

            Identifier overlay = gateOverlay(part.gateType, part.gateShape, part.gateState);
            if (overlay != null) {
                collector.order(order++).submitCustomGeometry(
                        poseStack,
                        RenderTypes.entityCutout(overlay),
                        (pose, consumer) -> RenderGeometry.gateSurface(
                                consumer,
                                pose,
                                state.lightCoords,
                                attachment,
                                part.gateRotation
                        )
                );
            }

            if (part.gateType == GateType.SEGMENT_DISPLAY) {
                collector.order(order++).submitCustomGeometry(
                        poseStack,
                        RenderTypes.entityCutout(SEGMENT_BASE),
                        (pose, consumer) -> RenderGeometry.gateSurface(
                                consumer,
                                pose,
                                state.lightCoords,
                                attachment,
                                part.gateRotation
                        )
                );
                if (part.bundledMask != 0) {
                    collector.order(order++).submitCustomGeometry(
                            poseStack,
                            RenderTypes.entityCutout(SEGMENT_DIGIT),
                            (pose, consumer) -> RenderGeometry.segmentDisplay(
                                    consumer,
                                    pose,
                                    0x00F000F0,
                                    attachment,
                                    part.gateRotation,
                                    part.gateShape,
                                    part.bundledMask
                            )
                    );
                }
            }

            if (part.gateType == GateType.BUS_INPUT_PANEL && part.panelMask != 0) {
                collector.order(order++).submitCustomGeometry(
                        poseStack,
                        RenderTypes.entityCutout(WIRE_ON),
                        (pose, consumer) -> RenderGeometry.panelButtons(
                                consumer,
                                pose,
                                0x00F000F0,
                                attachment,
                                part.gateRotation,
                                part.panelMask
                        )
                );
            }

            if (part.powered) {
                collector.order(order++).submitCustomGeometry(
                        poseStack,
                        RenderTypes.entityCutout(WIRE_ON),
                        (pose, consumer) -> RenderGeometry.gateIndicator(
                                consumer,
                                pose,
                                0x00F000F0,
                                attachment
                        )
                );
            }
        }
    }

    private static Identifier wireTexture(Visual part) {
        if (part.family == WireFamily.RED_ALLOY) {
            return projectRed("transmission/red_alloy_wire");
        }

        String color = colorName(part.color);

        if (part.family == WireFamily.BUNDLED) {
            return projectRed(
                    "transmission/"
                            + (part.color < 0 ? "neutral" : color)
                            + "_bundled_wire"
            );
        }

        return projectRed(
                "transmission/"
                        + color
                        + "_insulated_wire_"
                        + (part.powered ? "on" : "off")
        );
    }

    public static Identifier gateOverlay(GateType type, int shape, int state) {
        if (type == null) return null;

        String name = switch (type) {
            case OR -> variant("or", shape, 4);
            case NOR -> variant("nor", shape, 4);
            case NOT -> variant("not", shape, 4);
            case AND -> variant("and", shape, 4);
            case NAND -> variant("nand", shape, 4);
            case XOR -> variant("xor", shape, 4);
            case XNOR -> variant("xnor", shape, 5);
            case BUFFER -> variant("buffer", shape, 4);
            case MULTIPLEXER -> variant("multiplexer", shape, 6);
            case PULSE -> variant("pulse", shape, 3);
            case REPEATER -> variant("repeater", shape, 2);
            case RANDOMIZER -> variant("rand", shape, 7);
            case SR_LATCH -> (shape & 2) == 0
                    ? variant("rslatch", shape, 2)
                    : variant("rslatch2", shape, 4);
            case TOGGLE_LATCH -> variant("toglatch", shape, 2);
            case TRANSPARENT_LATCH -> variant("translatch", shape, 5);
            case LIGHT_SENSOR -> "lightsensor-0";
            case RAIN_SENSOR -> "rainsensor-0";
            case TIMER, SEQUENCER -> variant("time", shape, 3);
            case COUNTER -> variant("count", shape, 2);
            case STATE_CELL -> variant("statecell", shape, 5);
            case SYNCHRONIZER -> variant("sync", shape, 6);
            case BUS_TRANSCEIVER -> variant("busxcvr", shape, 2);
            case NULL_CELL -> null;
            case INVERT_CELL -> "invcell-0";
            case BUFFER_CELL -> variant("buffcell", shape, 2);
            case COMPARATOR -> variant("comparator", shape, 4);
            case AND_CELL -> variant("andcell", shape, 2);
            case BUS_RANDOMIZER -> variant(shape == 0 ? "busrand1" : "busrand2", shape, 2);
            case BUS_CONVERTER -> variant("busconv", shape, 3);
            case BUS_INPUT_PANEL -> "businput-0";
            case TRANSPARENT_LATCH_CELL -> variant("transparent-latch-cell", shape, 5);
            case SEGMENT_DISPLAY -> null;
            case DEC_RANDOMIZER -> variant("decrand", shape, 6);
        };

        return name == null ? null : projectRed("integration/surface/" + name);
    }

    public static String gateItemSurface(GateType type) {
        Identifier overlay = gateOverlay(type, 0, 0);
        if (overlay == null) {
            return switch (type) {
                case NULL_CELL -> "projectred:integration/block/null_cell";
                case SEGMENT_DISPLAY -> "projectred:integration/block/segment_display";
                default -> "projectred:integration/block/base";
            };
        }
        return "projectred:" + overlay.getPath()
                .replaceFirst("^textures/", "")
                .replaceFirst("\\.png$", "");
    }

    private static String variant(String base, int shape, int count) {
        return base + "-" + Math.floorMod(shape, count);
    }

    private static String colorName(int color) {
        String[] names = {
                "white", "orange", "magenta", "light_blue",
                "yellow", "lime", "pink", "gray",
                "light_gray", "cyan", "purple", "blue",
                "brown", "green", "red", "black"
        };
        if (color < 0 || color >= names.length) return "neutral";
        return names[color];
    }

    private static Identifier projectRed(String path) {
        return Identifier.fromNamespaceAndPath(
                "projectred",
                "textures/" + path + ".png"
        );
    }
}

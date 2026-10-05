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
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class MultipartRenderer implements BlockEntityRenderer<MultipartBlockEntity, MultipartRenderer.State> {
    private static final Identifier GATE_BASE = projectRed("integration/block/base");
    private static final Identifier WIRE_BORDER = projectRed("integration/block/wire_material_border");
    private static final Identifier WIRE_OFF = projectRed("integration/block/wire_material_off");
    private static final Identifier WIRE_ON = projectRed("integration/block/wire_material_on");
    private static final Identifier TORCH_OFF = projectRed("integration/block/redstone_torch_off");
    private static final Identifier TORCH_ON = projectRed("integration/block/redstone_torch");
    private static final Identifier YELLOW_CHIP_OFF = projectRed("integration/block/yellow_chip_off");
    private static final Identifier YELLOW_CHIP_ON = projectRed("integration/block/yellow_chip_on");
    private static final Identifier RED_CHIP_OFF = projectRed("integration/block/red_chip_off");
    private static final Identifier RED_CHIP_ON = projectRed("integration/block/red_chip_on");
    private static final Identifier MINUS_CHIP_OFF = projectRed("integration/block/minus_chip_off");
    private static final Identifier MINUS_CHIP_ON = projectRed("integration/block/minus_chip_on");
    private static final Identifier PLUS_CHIP_OFF = projectRed("integration/block/plus_chip_off");
    private static final Identifier PLUS_CHIP_ON = projectRed("integration/block/plus_chip_on");
    private static final Identifier LEVER = projectRed("integration/block/lever");
    private static final Identifier RAIN_SENSOR = projectRed("integration/block/rain_sensor");
    private static final Identifier SOLAR_BLOCK = projectRed("integration/block/solar_block_mode");
    private static final Identifier SOLAR_SKY = projectRed("integration/block/solar_sky_mode");
    private static final Identifier SOLAR_DUAL = projectRed("integration/block/solar_dual_mode");
    private static final Identifier BUS_XCVR = projectRed("integration/block/bus_xcvr");
    private static final Identifier BUS_RANDOMIZER = projectRed("integration/block/bus_randomizer");
    private static final Identifier BUS_CONVERTER = projectRed("integration/block/bus_converter");
    private static final Identifier BUS_INPUT = projectRed("integration/block/bus_input_panel");
    private static final Identifier SEGMENT_BASE = projectRed("integration/block/segment_display");
    private static final Identifier SEGMENT_DIGIT = projectRed("integration/block/segment_display_digit");
    private static final Identifier POINTER = projectRed("integration/block/pointer");

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
            int gateState2,
            boolean arrayCell,
            int arraySignalA,
            int arraySignalB,
            int arrayTopConnections,
            int bundleInput0,
            int bundleInput2,
            int bundleOutput0,
            int bundleOutput2,
            int bundleMask,
            int panelMask,
            int bundledMask,
            float pointerAngle,
            float pointerOffset
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

        for (int slot = 0; slot <= Part.CENTER_SLOT; slot++) {
            Part part = blockEntity.part(slot);
            if (part == null) continue;

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
                        0, 0, 0, 0,
                        false,
                        0, 0, 0,
                        0, 0, 0, 0, 0, 0, 0,
                        Float.NaN,
                        0.0F
                ));
                continue;
            }

            if (part instanceof GatePart gate) {
                if (blockEntity.getLevel() != null) {
                    gate.restoreWorldTimeBase(blockEntity.getLevel().getGameTime());
                }

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
                        gate.state2(),
                        gate.isArrayCell(),
                        gate.arraySignalA(),
                        gate.arraySignalB(),
                        blockEntity.visualArrayTopConnections(gate),
                        gate.bundleInput0(),
                        gate.bundleInput2(),
                        gate.bundleOutput0(),
                        gate.bundleOutput2(),
                        gate.bundleMask(),
                        gate.panelMask(),
                        gate.segmentMask(),
                        gate.pointerAngle(
                                blockEntity.getLevel() == null
                                        ? 0L
                                        : blockEntity.getLevel().getGameTime(),
                                blockEntity.getLevel() == null
                                        ? 0L
                                        : blockEntity.getLevel().getDefaultClockTime(),
                                partialTick
                        ),
                        gate.pointerLateralOffset()
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
        int[] order = {1};

        for (Visual part : state.parts) {
            if (part.wire) {
                submitTransmissionWire(state, poseStack, collector, order, part);
            } else {
                submitGate(state, poseStack, collector, order, part);
            }
        }
    }

    private static void submitTransmissionWire(
            State state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int[] order,
            Visual part
    ) {
        Identifier texture = wireTexture(part);

        collector.order(order[0]++).submitCustomGeometry(
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
                        float depth = part.family == WireFamily.BUNDLED
                                ? .25F
                                : part.family == WireFamily.INSULATED
                                        ? .1875F
                                        : .125F;
                        RenderGeometry.wireFace(
                                consumer,
                                pose,
                                state.lightCoords,
                                Direction.values()[part.slot],
                                width,
                                depth,
                                part.connections
                        );
                    }
                }
        );

        if (part.slot == Part.CENTER_SLOT) {
            collector.order(order[0]++).submitCustomGeometry(
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
    }

    private static void submitGate(
            State state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int[] order,
            Visual part
    ) {
        Direction attachment = Direction.values()[part.slot];
        GateVisuals.WirePlan wires = GateVisuals.wires(
                part.gateType,
                part.gateShape,
                part.gateState,
                part.gateState2,
                part.arraySignalA,
                part.arraySignalB
        );

        submitGateBase(
                state,
                poseStack,
                collector,
                order,
                part,
                attachment,
                wires.reflect()
        );

        if (wires.count() > 0) {
            int all = (1 << wires.count()) - 1;
            int enabled = all & ~wires.disabledMask();
            int on = enabled & wires.onMask();
            int off = enabled & ~wires.onMask();

            submitGateWireLayer(
                    state, poseStack, collector, order, part, wires,
                    all, WIRE_BORDER, true
            );
            int disabled = all & wires.disabledMask();
            if (disabled != 0) {
                submitGateWireLayer(
                        state, poseStack, collector, order, part, wires,
                        disabled, WIRE_BORDER, false
                );
            }
            if (off != 0) {
                submitGateWireLayer(
                        state, poseStack, collector, order, part, wires,
                        off, WIRE_OFF, false
                );
            }
            if (on != 0) {
                submitGateWireLayer(
                        state, poseStack, collector, order, part, wires,
                        on, WIRE_ON, false
                );
            }
        }

        submitGateComponents(
                state,
                poseStack,
                collector,
                order,
                part,
                attachment,
                wires
        );
    }

    private static void submitGateWireLayer(
            State state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int[] order,
            Visual part,
            GateVisuals.WirePlan wires,
            int selectedMask,
            Identifier texture,
            boolean border
    ) {
        collector.order(order[0]++).submitCustomGeometry(
                poseStack,
                RenderTypes.entityCutout(texture),
                (pose, consumer) -> RenderGeometry.gateWireMask(
                        consumer,
                        pose,
                        state.lightCoords,
                        Direction.values()[part.slot],
                        part.gateRotation,
                        wires.family(),
                        wires.count(),
                        selectedMask,
                        border,
                        wires.reflect(),
                        .125F
                )
        );
    }

    private static void submitGateComponents(
            State state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int[] order,
            Visual p,
            Direction attachment,
            GateVisuals.WirePlan wires
    ) {
        boolean reflect = wires.reflect();

        switch (p.gateType) {
            case OR -> {
                torch(state,poseStack,collector,order,p,attachment,8,9,6,(p.gateState & 0xE) == 0,reflect);
                torch(state,poseStack,collector,order,p,attachment,8,2.5F,8,!wireOn(wires,0),reflect);
            }
            case NOR -> torch(state,poseStack,collector,order,p,attachment,8,9,6,(p.gateState & 0xE) == 0,reflect);
            case NOT -> torch(state,poseStack,collector,order,p,attachment,8,8,6,(p.gateState & 0xF0) != 0,reflect);
            case AND -> {
                torch(state,poseStack,collector,order,p,attachment,4,8,6,!wireOn(wires,2) && !wireDisabled(wires,2),reflect);
                torch(state,poseStack,collector,order,p,attachment,12,8,6,!wireOn(wires,3) && !wireDisabled(wires,3),reflect);
                torch(state,poseStack,collector,order,p,attachment,8,8,6,!wireOn(wires,1) && !wireDisabled(wires,1),reflect);
                torch(state,poseStack,collector,order,p,attachment,8,2,8,!wireOn(wires,0),reflect);
            }
            case NAND -> {
                torch(state,poseStack,collector,order,p,attachment,4,8,6,!wireOn(wires,2) && !wireDisabled(wires,2),reflect);
                torch(state,poseStack,collector,order,p,attachment,12,8,6,!wireOn(wires,3) && !wireDisabled(wires,3),reflect);
                torch(state,poseStack,collector,order,p,attachment,8,8,6,!wireOn(wires,1) && !wireDisabled(wires,1),reflect);
            }
            case XOR -> {
                torch(state,poseStack,collector,order,p,attachment,4.5F,8,6,!wireOn(wires,2) && !wireOn(wires,1),reflect);
                torch(state,poseStack,collector,order,p,attachment,11.5F,8,6,!wireOn(wires,3) && !wireOn(wires,1),reflect);
                torch(state,poseStack,collector,order,p,attachment,8,12,6,wireOn(wires,1),reflect);
            }
            case XNOR -> {
                torch(state,poseStack,collector,order,p,attachment,8,2,8,(p.gateState & 0x11) != 0,reflect);
                torch(state,poseStack,collector,order,p,attachment,4.5F,8,6,!wireOn(wires,4) && (p.gateState & 8) == 0,reflect);
                torch(state,poseStack,collector,order,p,attachment,11.5F,8,6,!wireOn(wires,4) && (p.gateState & 2) == 0,reflect);
                torch(state,poseStack,collector,order,p,attachment,8,12,6,(p.gateState & 0xA) == 0,reflect);
            }
            case BUFFER -> {
                torch(state,poseStack,collector,order,p,attachment,8,3.5F,8,(p.gateState & 4) != 0,reflect);
                torch(state,poseStack,collector,order,p,attachment,8,9,6,(p.gateState & 4) == 0,reflect);
            }
            case MULTIPLEXER -> {
                torch(state,poseStack,collector,order,p,attachment,8,2,8,(p.gateState & 0x10) != 0,reflect);
                torch(state,poseStack,collector,order,p,attachment,9,10.5F,6,!wireOn(wires,3),reflect);
                torch(state,poseStack,collector,order,p,attachment,4.5F,8,6,(p.gateState & 8) == 0 && wireOn(wires,3),reflect);
                torch(state,poseStack,collector,order,p,attachment,11.5F,8,6,(p.gateState & 4) == 0 && !wireOn(wires,5),reflect);
            }
            case PULSE -> {
                torch(state,poseStack,collector,order,p,attachment,4,9.5F,6,wireOn(wires,0),reflect);
                torch(state,poseStack,collector,order,p,attachment,11,9.5F,6,wireOn(wires,1),reflect);
                torch(state,poseStack,collector,order,p,attachment,8,3.5F,p.gateShape == 0 ? 8 : 4,(p.gateState & 0x10) != 0,reflect);
            }
            case REPEATER -> {
                torch(state,poseStack,collector,order,p,attachment,8,2,6,(p.gateState & 0x10) != 0,reflect);
                float z = 12.0F - Math.min(8, p.gateShape);
                torch(state,poseStack,collector,order,p,attachment,12.5F,z,6,(p.gateState & 4) == 0,reflect);
            }
            case RANDOMIZER -> {
                chip(state,poseStack,collector,order,p,attachment,8,5.5F,(p.gateState & 0x10) != 0,YELLOW_CHIP_OFF,YELLOW_CHIP_ON,reflect);
                chip(state,poseStack,collector,order,p,attachment,11.5F,11.5F,(p.gateState & 0x20) != 0,YELLOW_CHIP_OFF,YELLOW_CHIP_ON,reflect);
                chip(state,poseStack,collector,order,p,attachment,4.5F,11.5F,(p.gateState & 0x80) != 0,YELLOW_CHIP_OFF,YELLOW_CHIP_ON,reflect);
            }
            case SR_LATCH -> {
                int s = reflect
                        ? (GateVisuals.flipMaskZ(p.gateState >> 4) << 4)
                                | GateVisuals.flipMaskZ(p.gateState)
                        : p.gateState;
                if ((p.gateShape >> 1) == 0) {
                    torch(state,poseStack,collector,order,p,attachment,8,3,6,(s & 0x10) != 0,reflect);
                    torch(state,poseStack,collector,order,p,attachment,8,13,6,(s & 0x40) != 0,reflect);
                } else {
                    torch(state,poseStack,collector,order,p,attachment,9.5F,3,6,(s & 0x10) != 0,reflect);
                    torch(state,poseStack,collector,order,p,attachment,6.5F,13,6,(s & 0x40) != 0,reflect);
                }
            }
            case TOGGLE_LATCH -> {
                torch(state,poseStack,collector,order,p,attachment,4,4,6,(p.gateState & 0x10) != 0,reflect);
                torch(state,poseStack,collector,order,p,attachment,4,12,6,(p.gateState & 0x40) != 0,reflect);
                lever(state,poseStack,collector,order,p,attachment,11,8,(p.gateState & 0x10) != 0,reflect);
            }
            case TRANSPARENT_LATCH -> {
                boolean high = (p.gateState & 0x10) != 0;
                torch(state,poseStack,collector,order,p,attachment,4,12.5F,6,wireOn(wires,2),reflect);
                torch(state,poseStack,collector,order,p,attachment,4,8,6,!wireOn(wires,2) && !wireOn(wires,4),reflect);
                torch(state,poseStack,collector,order,p,attachment,8,8,6,!wireOn(wires,1) && !wireOn(wires,3),reflect);
                torch(state,poseStack,collector,order,p,attachment,8,2,8,high,reflect);
                torch(state,poseStack,collector,order,p,attachment,14,8,8,high,reflect);
            }
            case LIGHT_SENSOR -> sensor(
                    state,poseStack,collector,order,p,attachment,
                    "solar",8,5.5F,
                    p.gateShape == 1 ? SOLAR_SKY : p.gateShape == 2 ? SOLAR_BLOCK : SOLAR_DUAL,
                    reflect
            );
            case RAIN_SENSOR -> sensor(
                    state,poseStack,collector,order,p,attachment,
                    "rainsensor",8,6,RAIN_SENSOR,reflect
            );
            case TIMER -> {
                torch(state,poseStack,collector,order,p,attachment,8,3,6,(p.gateState & 0x10) != 0,reflect);
                torch(state,poseStack,collector,order,p,attachment,8,8,12,false,reflect);
                pointer(state,poseStack,collector,order,p,attachment,.125F);
            }
            case SEQUENCER -> {
                torch(state,poseStack,collector,order,p,attachment,8,8,12,true,reflect);
                torch(state,poseStack,collector,order,p,attachment,8,3,6,(p.gateState & 0x10) != 0,reflect);
                torch(state,poseStack,collector,order,p,attachment,13,8,6,(p.gateState & 0x20) != 0,reflect);
                torch(state,poseStack,collector,order,p,attachment,8,13,6,(p.gateState & 0x40) != 0,reflect);
                torch(state,poseStack,collector,order,p,attachment,3,8,6,(p.gateState & 0x80) != 0,reflect);
                pointer(state,poseStack,collector,order,p,attachment,.125F);
            }
            case COUNTER -> {
                torch(state,poseStack,collector,order,p,attachment,11,8,12,true,reflect);
                torch(state,poseStack,collector,order,p,attachment,8,3,6,(p.gateState & 0x10) != 0,reflect);
                torch(state,poseStack,collector,order,p,attachment,8,13,6,(p.gateState & 0x40) != 0,reflect);
                pointer(state,poseStack,collector,order,p,attachment,.125F);
            }
            case STATE_CELL -> {
                int s = reflect
                        ? (GateVisuals.flipMaskZ(p.gateState >> 4) << 4)
                                | GateVisuals.flipMaskZ(p.gateState)
                        : p.gateState;
                torch(state,poseStack,collector,order,p,attachment,10,3.5F,6,(s & 0x10) != 0,reflect);
                torch(state,poseStack,collector,order,p,attachment,13,8,12,p.pointerAngle > -(float)Math.PI / 2.0F + .001F,reflect);
                chip(state,poseStack,collector,order,p,attachment,6.5F,10,p.gateState2 != 0,RED_CHIP_OFF,RED_CHIP_ON,reflect);
                pointer(state,poseStack,collector,order,p,attachment,.125F);
            }
            case SYNCHRONIZER -> {
                chip(state,poseStack,collector,order,p,attachment,4.5F,9,(p.gateState2 & 1) != 0,RED_CHIP_OFF,RED_CHIP_ON,reflect);
                chip(state,poseStack,collector,order,p,attachment,11.5F,9,(p.gateState2 & 2) != 0,RED_CHIP_OFF,RED_CHIP_ON,reflect);
                torch(state,poseStack,collector,order,p,attachment,8,3,6,(p.gateState & 0x10) != 0,reflect);
            }
            case BUS_TRANSCEIVER -> {
                mesh(state,poseStack,collector,order,p,attachment,
                        "array/busxcvr",.5F,0,.5F,1,0,BUS_XCVR,reflect);
                panelBase(state,poseStack,collector,order,p,attachment,4,8,false,BUS_XCVR,reflect);
                panelBase(state,poseStack,collector,order,p,attachment,12,8,true,BUS_XCVR,reflect);
                signalPanel(
                        state,poseStack,collector,order,p,attachment,
                        p.bundleInput0 | p.bundleOutput0,
                        0,
                        4,8,
                        reflect,
                        0xEC0000,0x420000,0x808080
                );
                signalPanel(
                        state,poseStack,collector,order,p,attachment,
                        p.bundleInput2 | p.bundleOutput2,
                        0,
                        12,8,
                        reflect,
                        0xEC0000,0x420000,0x808080
                );
            }
            case NULL_CELL -> arrayRail(state,poseStack,collector,order,p,attachment,reflect,.50F);
            case INVERT_CELL -> {
                arrayRail(state,poseStack,collector,order,p,attachment,reflect,.50F);
                torch(state,poseStack,collector,order,p,attachment,8,8,6,p.arraySignalA == 0,reflect);
            }
            case BUFFER_CELL -> {
                arrayRail(state,poseStack,collector,order,p,attachment,reflect,.50F);
                torch(state,poseStack,collector,order,p,attachment,11,13,6,p.arraySignalA == 0,reflect);
                torch(state,poseStack,collector,order,p,attachment,8,8,6,p.arraySignalA != 0,reflect);
            }
            case COMPARATOR -> {
                torch(state,poseStack,collector,order,p,attachment,8,2,6,(p.gateState & 0x10) != 0,reflect);
                boolean active = (p.gateState & 0x10) != 0;
                chip(state,poseStack,collector,order,p,attachment,5,8,active && p.gateShape == 1,MINUS_CHIP_OFF,MINUS_CHIP_ON,reflect);
                chip(state,poseStack,collector,order,p,attachment,11,8,active && p.gateShape != 1,PLUS_CHIP_OFF,PLUS_CHIP_ON,reflect);
            }
            case AND_CELL -> {
                arrayRail(state,poseStack,collector,order,p,attachment,reflect,.50F);
                torch(state,poseStack,collector,order,p,attachment,8,13,6,(p.gateState & 4) == 0,reflect);
                torch(state,poseStack,collector,order,p,attachment,8,2,8,(p.gateState & 0x10) != 0,reflect);
                torch(state,poseStack,collector,order,p,attachment,8,8,6,p.arraySignalB == 0,reflect);
            }
            case BUS_RANDOMIZER -> {
                mesh(state,poseStack,collector,order,p,attachment,
                        "array/busrand",.5F,0,.5F,1,0,BUS_RANDOMIZER,reflect);
                panelBase(state,poseStack,collector,order,p,attachment,8,8,true,BUS_XCVR,reflect);
                signalPanel(
                        state,poseStack,collector,order,p,attachment,
                        p.bundleOutput0,
                        ~p.bundleMask & 0xFFFF,
                        8,8,
                        reflect,
                        0xE1D600,0x756900,0x808080
                );
            }
            case BUS_CONVERTER -> {
                mesh(state,poseStack,collector,order,p,attachment,
                        "array/busconv",.5F,0,.5F,1,0,BUS_CONVERTER,reflect);
                mesh(state,poseStack,collector,order,p,attachment,
                        "array/signalpanel",.5F,0,.5F,1,0,BUS_CONVERTER,reflect);
                int level = p.gateShape == 0 ? p.gateState2 & 15 : highestBit(p.bundleInput0);
                analogBar(state,poseStack,collector,order,p,attachment,level,reflect);
            }
            case BUS_INPUT_PANEL -> {
                mesh(state,poseStack,collector,order,p,attachment,
                        "array/businput",.5F,0,.5F,1,0,BUS_INPUT,reflect);

                collector.order(order[0]++).submitCustomGeometry(
                        poseStack,
                        RenderTypes.entityCutout(GATE_BASE),
                        (pose, consumer) -> RenderGeometry.gateInputButtons(
                                consumer,
                                pose,
                                state.lightCoords,
                                attachment,
                                p.gateRotation,
                                p.panelMask
                        )
                );

                if (p.panelMask != 0) {
                    collector.order(order[0]++).submitCustomGeometry(
                            poseStack,
                            RenderTypes.entityCutout(GATE_BASE),
                            (pose, consumer) -> RenderGeometry.gateInputButtonLights(
                                    consumer,
                                    pose,
                                    0x00F000F0,
                                    attachment,
                                    p.gateRotation,
                                    p.panelMask
                            )
                    );
                }
            }
            case TRANSPARENT_LATCH_CELL -> {
                arrayRail(state,poseStack,collector,order,p,attachment,reflect,.50F);
                boolean high = (p.gateState & 0x10) != 0;
                torch(state,poseStack,collector,order,p,attachment,12.5F,12,6,p.arraySignalB == 0,reflect);
                torch(state,poseStack,collector,order,p,attachment,8,12,6,p.arraySignalB != 0,reflect);
                torch(state,poseStack,collector,order,p,attachment,8,8,6,!high,reflect);
                torch(state,poseStack,collector,order,p,attachment,8,2,8,high,reflect);
            }
            case SEGMENT_DISPLAY -> {
                mesh(state,poseStack,collector,order,p,attachment,
                        "array/segbus",.5F,0,.5F,1,0,SEGMENT_BASE,reflect);
                segmentDisplay(
                        state,
                        poseStack,
                        collector,
                        order,
                        p,
                        attachment,
                        reflect
                );
            }
            case DEC_RANDOMIZER -> {
                int high = p.gateState >> 4;
                chip(state,poseStack,collector,order,p,attachment,5,13,high == 2,YELLOW_CHIP_OFF,YELLOW_CHIP_ON,reflect);
                chip(state,poseStack,collector,order,p,attachment,11,13,high == 1 || high == 2,YELLOW_CHIP_OFF,YELLOW_CHIP_ON,reflect);
                chip(state,poseStack,collector,order,p,attachment,5.5F,8,true,RED_CHIP_OFF,RED_CHIP_ON,reflect);
                torch(state,poseStack,collector,order,p,attachment,8,2.5F,8,high == 1,reflect);
                torch(state,poseStack,collector,order,p,attachment,14,8,8,high == 2,reflect);
                torch(state,poseStack,collector,order,p,attachment,2,8,8,high == 8,reflect);
                torch(state,poseStack,collector,order,p,attachment,9,8,6,!wireOn(wires,4),reflect);
            }
        }
    }

    private static void submitGateBase(
            State state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int[] order,
            Visual p,
            Direction attachment,
            boolean reflect
    ) {
        switch (p.gateType) {
            case NULL_CELL -> mesh(
                    state, poseStack, collector, order, p, attachment,
                    "array/nullcellbase",
                    .5F, 0.0F, .5F,
                    1.0F, 0.0F,
                    arrayCellBase(p.gateType),
                    reflect
            );
            case INVERT_CELL, BUFFER_CELL -> mesh(
                    state, poseStack, collector, order, p, attachment,
                    "array/extendedcellbase",
                    .5F, 0.0F, .5F,
                    1.0F, 0.0F,
                    arrayCellBase(p.gateType),
                    reflect
            );
            default -> mesh(
                    state, poseStack, collector, order, p, attachment,
                    "base",
                    .5F, 0.0F, .5F,
                    1.0F, 0.0F,
                    GATE_BASE,
                    reflect
            );
        }
    }

    private static void segmentDisplay(
            State state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int[] order,
            Visual p,
            Direction attachment,
            boolean reflect
    ) {
        int onColor = DyeColor.byId(p.gateState & 15)
                .getTextureDiffuseColor() & 0xFFFFFF;
        int offColor = 0x000000;

        if (p.gateShape == 0) {
            sevenSegment(
                    state, poseStack, collector, order, p, attachment,
                    11.5F / 16.0F, 8.0F / 16.0F,
                    p.bundledMask & 0xFF,
                    onColor, offColor, reflect
            );
            sevenSegment(
                    state, poseStack, collector, order, p, attachment,
                    4.5F / 16.0F, 8.0F / 16.0F,
                    p.bundledMask >>> 8 & 0xFF,
                    onColor, offColor, reflect
            );
            return;
        }

        correctedGroup(
                state, poseStack, collector, order, p, attachment,
                "array/16seg", "base",
                8.0F / 16.0F, 0.0F, 8.0F / 16.0F,
                SEGMENT_BASE, reflect, 0xFFFFFF
        );
        for (int segment = 0; segment < 16; segment++) {
            correctedGroup(
                    state, poseStack, collector, order, p, attachment,
                    "array/16seg", Integer.toString(segment),
                    8.0F / 16.0F, 0.0F, 8.0F / 16.0F,
                    SEGMENT_DIGIT,
                    reflect,
                    (p.bundledMask & 1 << segment) != 0
                            ? onColor
                            : offColor
            );
        }
    }

    private static void sevenSegment(
            State state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int[] order,
            Visual p,
            Direction attachment,
            float x,
            float z,
            int signal,
            int onColor,
            int offColor,
            boolean reflect
    ) {
        correctedGroup(
                state, poseStack, collector, order, p, attachment,
                "array/7seg", "base",
                x, 0.0F, z,
                SEGMENT_BASE, reflect, 0xFFFFFF
        );
        for (int segment = 0; segment < 8; segment++) {
            correctedGroup(
                    state, poseStack, collector, order, p, attachment,
                    "array/7seg", Integer.toString(segment),
                    x, 0.0F, z,
                    SEGMENT_DIGIT,
                    reflect,
                    (signal & 1 << segment) != 0
                            ? onColor
                            : offColor
            );
        }
    }

    private static void correctedGroup(
            State state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int[] order,
            Visual p,
            Direction attachment,
            String model,
            String group,
            float offsetX,
            float offsetY,
            float offsetZ,
            Identifier texture,
            boolean reflect,
            int rgb
    ) {
        collector.order(order[0]++).submitCustomGeometry(
                poseStack,
                RenderTypes.entityCutout(texture),
                (pose, consumer) -> ProjectRedObjModel.get(model)
                        .renderCorrectedGroup(
                                consumer,
                                pose,
                                state.lightCoords,
                                attachment,
                                p.gateRotation,
                                offsetX,
                                offsetY,
                                offsetZ,
                                1.0F,
                                0.0F,
                                reflect,
                                rgb,
                                group
                        )
        );
    }

    private static void pointer(
            State state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int[] order,
            Visual p,
            Direction attachment,
            float surfaceDepth
    ) {
        if (Float.isNaN(p.pointerAngle)) return;

        float x = .5F;
        float z = .5F;
        float scale = 1.0F;
        boolean reflect = p.gateShape == 1
                && (p.gateType == GateType.COUNTER
                || p.gateType == GateType.STATE_CELL);

        if (p.gateType == GateType.COUNTER) {
            x = 11.0F / 16.0F;
            scale = 1.2F;
        } else if (p.gateType == GateType.STATE_CELL) {
            x = 13.0F / 16.0F;
        }

        mesh(
                state, poseStack, collector, order, p, attachment,
                "pointer",
                x, 7.0F / 16.0F, z,
                scale,
                -(p.pointerAngle) + (float) Math.PI,
                POINTER,
                reflect
        );
    }

    private static void torch(
            State state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int[] order,
            Visual p,
            Direction attachment,
            float x,
            float z,
            int height,
            boolean on,
            boolean reflect
    ) {
        collector.order(order[0]++).submitCustomGeometry(
                poseStack,
                RenderTypes.entityCutout(on ? TORCH_ON : TORCH_OFF),
                (pose, consumer) -> RenderGeometry.gateTorch(
                        consumer, pose, state.lightCoords,
                        attachment, p.gateRotation,
                        x, z, height, reflect
                )
        );
    }

    private static void chip(
            State state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int[] order,
            Visual p,
            Direction attachment,
            float x,
            float z,
            boolean on,
            Identifier offTexture,
            Identifier onTexture,
            boolean reflect
    ) {
        mesh(
                state, poseStack, collector, order, p, attachment,
                "chip",
                x / 16.0F, 0.0F, z / 16.0F,
                1.0F, 0.0F,
                on ? onTexture : offTexture,
                reflect
        );
    }

    private static void lever(
            State state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int[] order,
            Visual p,
            Direction attachment,
            float x,
            float z,
            boolean on,
            boolean reflect
    ) {
        mesh(
                state, poseStack, collector, order, p, attachment,
                on ? "leveron" : "leveroff",
                x / 16.0F, 2.0F / 16.0F, z / 16.0F,
                1.0F, 0.0F,
                LEVER,
                reflect
        );
    }

    private static void sensor(
            State state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int[] order,
            Visual p,
            Direction attachment,
            String model,
            float x,
            float z,
            Identifier texture,
            boolean reflect
    ) {
        mesh(
                state, poseStack, collector, order, p, attachment,
                model,
                x / 16.0F, 0.0F, z / 16.0F,
                1.0F, 0.0F,
                texture,
                reflect
        );
    }

    private static void component(
            State state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int[] order,
            Visual p,
            Direction attachment,
            float x,
            float z,
            float width,
            float depth,
            float height,
            Identifier texture,
            boolean reflect
    ) {
        collector.order(order[0]++).submitCustomGeometry(
                poseStack,
                RenderTypes.entityCutout(texture),
                (pose, consumer) -> RenderGeometry.gateComponentBox(
                        consumer, pose, state.lightCoords,
                        attachment, p.gateRotation,
                        x / 16.0F, z / 16.0F,
                        width, depth,
                        .125F, height,
                        reflect
                )
        );
    }

    private static void arrayRail(
            State state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int[] order,
            Visual p,
            Direction attachment,
            boolean reflect,
            float centerX
    ) {
        Identifier texture = arrayCellBase(p.gateType);

        switch (p.gateType) {
            case NULL_CELL -> {
                meshTinted(
                        state,poseStack,collector,order,p,attachment,
                        "array/nullcellbottomwire",.5F,0,.5F,1,0,
                        texture,reflect,signalColor(p.arraySignalA)
                );
                meshTinted(
                        state,poseStack,collector,order,p,attachment,
                        "array/nullcelltopwire",.5F,0,.5F,1,0,
                        texture,reflect,signalColor(p.arraySignalB)
                );
                arrayTopCaps(
                        state,poseStack,collector,order,p,attachment,
                        texture,reflect,p.arraySignalB
                );
                mesh(
                        state,poseStack,collector,order,p,attachment,
                        "array/cellstand",.5F,0,.5F,1,0,
                        texture,reflect
                );
            }
            case INVERT_CELL, BUFFER_CELL -> {
                meshTinted(
                        state,poseStack,collector,order,p,attachment,
                        "array/extendedcellbottomwire",.5F,0,.5F,1,0,
                        texture,reflect,signalColor(p.arraySignalA)
                );
                meshTinted(
                        state,poseStack,collector,order,p,attachment,
                        "array/extendedcelltopwire",.5F,0,.5F,1,0,
                        texture,reflect,signalColor(p.arraySignalB)
                );
                arrayTopCaps(
                        state,poseStack,collector,order,p,attachment,
                        texture,reflect,p.arraySignalB
                );
                mesh(
                        state,poseStack,collector,order,p,attachment,
                        "array/cellstand",.5F,0,.5F,1,0,
                        texture,reflect
                );
                mesh(
                        state,poseStack,collector,order,p,attachment,
                        "array/cellplate",.5F,0,.5F,1,0,
                        texture,reflect
                );
            }
            case AND_CELL -> {
                meshTinted(
                        state,poseStack,collector,order,p,attachment,
                        "array/nullcelltopwire",.5F,0,.5F,1,0,
                        texture,reflect,signalColor(p.arraySignalB)
                );
                arrayTopCaps(
                        state,poseStack,collector,order,p,attachment,
                        texture,reflect,p.arraySignalB
                );
                mesh(
                        state,poseStack,collector,order,p,attachment,
                        "array/cellstand",.5F,0,.5F,1,0,
                        texture,reflect
                );
            }
            case TRANSPARENT_LATCH_CELL -> {
                meshTinted(
                        state,poseStack,collector,order,p,attachment,
                        "array/stacklatchwire",.5F,0,.5F,1,0,
                        texture,reflect,signalColor(p.arraySignalB)
                );
                mesh(
                        state,poseStack,collector,order,p,attachment,
                        "array/latchstand",
                        3.5F / 16.0F,2.0F / 16.0F,5.0F / 16.0F,
                        1,0,texture,reflect
                );
                mesh(
                        state,poseStack,collector,order,p,attachment,
                        "array/latchstand",
                        12.5F / 16.0F,2.0F / 16.0F,5.0F / 16.0F,
                        1,0,texture,reflect
                );
            }
            default -> {
            }
        }
    }

    private static void arrayTopCaps(
            State state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int[] order,
            Visual p,
            Direction attachment,
            Identifier texture,
            boolean reflect,
            int signal
    ) {
        int color = signalColor(signal);
        if ((p.arrayTopConnections & 2) == 0) {
            meshTinted(
                    state,poseStack,collector,order,p,attachment,
                    "array/cellsidewire",
                    .5F + 7.001F / 16.0F,0,.5F,
                    1,0,texture,reflect,color
            );
        }
        if ((p.arrayTopConnections & 8) == 0) {
            meshTinted(
                    state,poseStack,collector,order,p,attachment,
                    "array/cellsidewire",
                    .5F - 7.001F / 16.0F,0,.5F,
                    1,0,texture,reflect,color
            );
        }
    }

    private static void panelBase(
            State state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int[] order,
            Visual p,
            Direction attachment,
            float x,
            float z,
            boolean rotate,
            Identifier texture,
            boolean reflect
    ) {
        mesh(
                state,poseStack,collector,order,p,attachment,
                "array/lightpanel1",
                x / 16.0F,0,z / 16.0F,
                1.0F,
                rotate ? (float) Math.PI : 0.0F,
                texture,
                reflect
        );
    }

    private static void mesh(
            State state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int[] order,
            Visual p,
            Direction attachment,
            String model,
            float offsetX,
            float offsetY,
            float offsetZ,
            float scaleXZ,
            float angleY,
            Identifier texture,
            boolean reflect
    ) {
        meshTinted(
                state,poseStack,collector,order,p,attachment,
                model,offsetX,offsetY,offsetZ,scaleXZ,angleY,
                texture,reflect,0xFFFFFF
        );
    }

    private static void meshTinted(
            State state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int[] order,
            Visual p,
            Direction attachment,
            String model,
            float offsetX,
            float offsetY,
            float offsetZ,
            float scaleXZ,
            float angleY,
            Identifier texture,
            boolean reflect,
            int rgb
    ) {
        collector.order(order[0]++).submitCustomGeometry(
                poseStack,
                RenderTypes.entityCutout(texture),
                (pose, consumer) -> ProjectRedObjModel.get(model).render(
                        consumer,
                        pose,
                        state.lightCoords,
                        attachment,
                        p.gateRotation,
                        offsetX,
                        offsetY,
                        offsetZ,
                        scaleXZ,
                        angleY,
                        reflect,
                        rgb
                )
        );
    }

    private static int signalColor(int signal) {
        int red = Math.min(255, Math.max(0, signal) / 2 + 60);
        return red << 16;
    }

    private static void signalPanel(
            State state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int[] order,
            Visual p,
            Direction attachment,
            int signalMask,
            int disableMask,
            float x,
            float z,
            boolean reflect,
            int onColor,
            int offColor,
            int disableColor
    ) {
        collector.order(order[0]++).submitCustomGeometry(
                poseStack,
                RenderTypes.entityCutout(SEGMENT_DIGIT),
                (pose, consumer) -> RenderGeometry.gatePanelLights(
                        consumer,
                        pose,
                        0x00F000F0,
                        attachment,
                        p.gateRotation,
                        signalMask,
                        disableMask,
                        x / 16.0F,
                        z / 16.0F,
                        x < 8.0F,
                        reflect,
                        onColor,
                        offColor,
                        disableColor
                )
        );
    }

    private static void analogBar(
            State state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int[] order,
            Visual p,
            Direction attachment,
            int level,
            boolean reflect
    ) {
        if (level <= 0) return;
        collector.order(order[0]++).submitCustomGeometry(
                poseStack,
                RenderTypes.entityCutout(WIRE_ON),
                (pose, consumer) -> RenderGeometry.gateSignalBar(
                        consumer,
                        pose,
                        0x00F000F0,
                        attachment,
                        p.gateRotation,
                        level,
                        p.gateShape != 0,
                        reflect
                )
        );
    }

    private static boolean wireOn(GateVisuals.WirePlan plan, int index) {
        return (plan.onMask() & (1 << index)) != 0;
    }

    private static boolean wireDisabled(GateVisuals.WirePlan plan, int index) {
        return (plan.disabledMask() & (1 << index)) != 0;
    }

    private static int highestBit(int mask) {
        mask &= 0xFFFF;
        return mask == 0 ? 0 : 31 - Integer.numberOfLeadingZeros(mask);
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

    private static Identifier arrayCellBase(GateType type) {
        String path = switch (type) {
            case NULL_CELL -> "integration/block/null_cell";
            case AND_CELL -> "integration/block/and_cell";
            case TRANSPARENT_LATCH_CELL -> "integration/block/transparent_latch_cell";
            case INVERT_CELL, BUFFER_CELL -> "integration/block/logic_cell";
            default -> "integration/block/base";
        };
        return projectRed(path);
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

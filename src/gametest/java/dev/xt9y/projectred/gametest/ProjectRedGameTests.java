package dev.xt9y.projectred.gametest;

import dev.xt9y.projectred.content.PRContent;
import dev.xt9y.projectred.integration.GatePart;
import dev.xt9y.projectred.integration.GateType;
import dev.xt9y.projectred.multipart.MultipartBlockEntity;
import dev.xt9y.projectred.multipart.Part;
import dev.xt9y.projectred.transmission.WirePart;
import dev.xt9y.projectred.transmission.WireSpec;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class ProjectRedGameTests {
    private static final String EMPTY = "fabric-gametest-api-v1:empty";

    @GameTest(structure = EMPTY, maxTicks = 40)
    public void redAlloyWireAttenuatesOneStepPerWire(GameTestHelper helper) {
        Rig rig = line(
                helper,
                "red_alloy_wire",
                "red_alloy_wire"
        );

        rig.first.onNeighborSignalChanged();

        helper.assertTrue(
                rig.firstWire.signal() == 255,
                "source red-alloy wire should resolve vanilla 15 to internal 255"
        );
        helper.assertTrue(
                rig.secondWire.signal() == 254,
                "adjacent ProjectRed wire should attenuate by exactly one internal level"
        );
        helper.succeed();
    }

    @GameTest(structure = EMPTY, maxTicks = 40)
    public void insulatedWireRejectsDifferentColour(GameTestHelper helper) {
        Rig rig = line(
                helper,
                "red_insulated_wire",
                "blue_insulated_wire"
        );

        rig.first.onNeighborSignalChanged();

        helper.assertTrue(
                rig.firstWire.signal() == 255,
                "red insulated source wire should be powered"
        );
        helper.assertTrue(
                rig.secondWire.signal() == 0,
                "blue insulated wire must not accept red insulated signal"
        );
        helper.succeed();
    }

    @GameTest(structure = EMPTY, maxTicks = 40)
    public void insulatedWireCarriesMatchingColour(GameTestHelper helper) {
        Rig rig = line(
                helper,
                "red_insulated_wire",
                "red_insulated_wire"
        );

        rig.first.onNeighborSignalChanged();

        helper.assertTrue(
                rig.secondWire.signal() == 254,
                "matching insulated colours must connect and attenuate by one"
        );
        helper.succeed();
    }


    @GameTest(structure = EMPTY, maxTicks = 40)
    public void bundledCableBridgesMatchingInsulatedChannel(GameTestHelper helper) {
        BlockPos firstRel = new BlockPos(2, 2, 2);
        BlockPos busRel = firstRel.east();
        BlockPos lastRel = busRel.east();
        BlockPos sourceRel = firstRel.west();

        for (BlockPos pos : new BlockPos[] { firstRel, busRel, lastRel }) {
            helper.setBlock(pos.below(), Blocks.STONE.defaultBlockState());
            helper.setBlock(pos, PRContent.MULTIPART.defaultBlockState());
        }
        helper.setBlock(sourceRel, Blocks.REDSTONE_BLOCK.defaultBlockState());

        MultipartBlockEntity first = multipart(helper, firstRel);
        MultipartBlockEntity bus = multipart(helper, busRel);
        MultipartBlockEntity last = multipart(helper, lastRel);

        WirePart firstWire = new WirePart(
                requireWire("red_insulated_wire"),
                Direction.DOWN.ordinal()
        );
        WirePart busWire = new WirePart(
                requireWire("neutral_bundled_wire"),
                Direction.DOWN.ordinal()
        );
        WirePart lastWire = new WirePart(
                requireWire("red_insulated_wire"),
                Direction.DOWN.ordinal()
        );

        helper.assertTrue(first.add(firstWire), "failed to add source insulated wire");
        helper.assertTrue(bus.add(busWire), "failed to add bundled wire");
        helper.assertTrue(last.add(lastWire), "failed to add destination insulated wire");

        first.onNeighborSignalChanged();

        int red = net.minecraft.world.item.DyeColor.RED.getId();
        helper.assertTrue(firstWire.signal() == 255, "source insulated wire should be at full strength");
        helper.assertTrue(
                busWire.bundled()[red] == 254,
                "bundled cable must import only the matching insulated channel with one-step attenuation"
        );
        helper.assertTrue(
                lastWire.signal() == 253,
                "matching insulated wire must recover its channel from bundled cable and attenuate once more"
        );

        int[] bundled = busWire.bundled();
        for (int channel = 0; channel < bundled.length; channel++) {
            if (channel == red) continue;
            helper.assertTrue(
                    bundled[channel] == 0,
                    "unrelated bundled channels must remain zero"
            );
        }

        helper.succeed();
    }

    @GameTest(structure = EMPTY, maxTicks = 40)
    public void notGateKeepsProjectRedTwoTickTransition(GameTestHelper helper) {
        BlockPos gateRel = new BlockPos(3, 2, 3);
        BlockPos inputRel = gateRel.south();

        helper.setBlock(gateRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(gateRel, PRContent.MULTIPART.defaultBlockState());

        MultipartBlockEntity multipart = multipart(helper, gateRel);
        GatePart gate = new GatePart(
                GateType.NOT,
                Direction.DOWN.ordinal(),
                0
        );
        helper.assertTrue(multipart.add(gate), "failed to add NOT gate");

        helper.assertTrue(
                multipart.vanillaSignal(Direction.NORTH) == 15,
                "fresh NOT gate must begin high with no input"
        );

        helper.setBlock(inputRel, Blocks.REDSTONE_BLOCK.defaultBlockState());
        multipart.onNeighborSignalChanged();

        helper.assertTrue(
                multipart.vanillaSignal(Direction.NORTH) == 15,
                "NOT output must remain high until its two-tick scheduled transition"
        );

        helper.runAfterDelay(3, () -> {
            helper.assertTrue(
                    multipart.vanillaSignal(Direction.NORTH) == 0,
                    "NOT output must be low after the two-tick transition"
            );
            helper.succeed();
        });
    }


    @GameTest(structure = EMPTY, maxTicks = 40)
    public void busInputPanelDrivesSelectedBundledChannel(GameTestHelper helper) {
        BlockPos gateRel = new BlockPos(3, 2, 3);
        BlockPos cableRel = gateRel.south();

        helper.setBlock(gateRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(cableRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(gateRel, PRContent.MULTIPART.defaultBlockState());
        helper.setBlock(cableRel, PRContent.MULTIPART.defaultBlockState());

        MultipartBlockEntity gateMultipart = multipart(helper, gateRel);
        MultipartBlockEntity cableMultipart = multipart(helper, cableRel);

        GatePart panel = new GatePart(
                GateType.BUS_INPUT_PANEL,
                Direction.DOWN.ordinal(),
                0
        );
        WirePart cable = new WirePart(
                requireWire("neutral_bundled_wire"),
                Direction.DOWN.ordinal()
        );

        helper.assertTrue(gateMultipart.add(panel), "failed to add bus input panel");
        helper.assertTrue(cableMultipart.add(cable), "failed to add bundled cable");

        panel.togglePanelBit(5);
        gateMultipart.markPartChanged();

        helper.runAfterDelay(3, () -> {
            int[] signal = cable.bundled();
            helper.assertTrue(
                    signal[5] == 255,
                    "pressed bus-input-panel channel must drive bundled cable at full strength"
            );
            for (int channel = 0; channel < signal.length; channel++) {
                if (channel == 5) continue;
                helper.assertTrue(
                        signal[channel] == 0,
                        "unpressed bus-input-panel channels must remain off"
                );
            }
            helper.succeed();
        });
    }

    @GameTest(structure = EMPTY, maxTicks = 40)
    public void busConverterMapsAnalogLevelToBundledChannel(GameTestHelper helper) {
        BlockPos gateRel = new BlockPos(3, 2, 3);
        BlockPos inputRel = gateRel.south();
        BlockPos cableRel = gateRel.north();

        helper.setBlock(gateRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(cableRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(inputRel, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(gateRel, PRContent.MULTIPART.defaultBlockState());
        helper.setBlock(cableRel, PRContent.MULTIPART.defaultBlockState());

        MultipartBlockEntity gateMultipart = multipart(helper, gateRel);
        MultipartBlockEntity cableMultipart = multipart(helper, cableRel);

        GatePart converter = new GatePart(
                GateType.BUS_CONVERTER,
                Direction.DOWN.ordinal(),
                0
        );
        WirePart cable = new WirePart(
                requireWire("neutral_bundled_wire"),
                Direction.DOWN.ordinal()
        );

        helper.assertTrue(gateMultipart.add(converter), "failed to add bus converter");
        helper.assertTrue(cableMultipart.add(cable), "failed to add bundled cable");
        gateMultipart.onNeighborSignalChanged();

        helper.runAfterDelay(3, () -> {
            int[] signal = cable.bundled();
            helper.assertTrue(
                    signal[15] == 255,
                    "analog redstone level 15 must map to bundled channel 15"
            );
            for (int channel = 0; channel < 15; channel++) {
                helper.assertTrue(
                        signal[channel] == 0,
                        "bus converter must emit exactly one bundled channel in analog-to-bus mode"
                );
            }
            helper.succeed();
        });
    }


    @GameTest(structure = EMPTY, maxTicks = 60)
    public void busTransceiverRoutesBundledBusWithSideControl(GameTestHelper helper) {
        BlockPos gateRel = new BlockPos(4, 2, 4);
        BlockPos inputCableRel = gateRel.south();
        BlockPos sourceWireRel = inputCableRel.south();
        BlockPos sourcePowerRel = sourceWireRel.south();
        BlockPos outputCableRel = gateRel.north();
        BlockPos outputWireRel = outputCableRel.north();
        BlockPos controlRel = gateRel.east();

        for (BlockPos pos : new BlockPos[] {
                gateRel, inputCableRel, sourceWireRel,
                outputCableRel, outputWireRel
        }) {
            helper.setBlock(pos.below(), Blocks.STONE.defaultBlockState());
            helper.setBlock(pos, PRContent.MULTIPART.defaultBlockState());
        }

        helper.setBlock(sourcePowerRel, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(controlRel, Blocks.REDSTONE_BLOCK.defaultBlockState());

        MultipartBlockEntity gateMultipart = multipart(helper, gateRel);
        MultipartBlockEntity inputMultipart = multipart(helper, inputCableRel);
        MultipartBlockEntity sourceMultipart = multipart(helper, sourceWireRel);
        MultipartBlockEntity outputMultipart = multipart(helper, outputCableRel);
        MultipartBlockEntity sinkMultipart = multipart(helper, outputWireRel);

        GatePart transceiver = new GatePart(
                GateType.BUS_TRANSCEIVER,
                Direction.DOWN.ordinal(),
                0
        );
        WirePart inputCable = new WirePart(
                requireWire("neutral_bundled_wire"),
                Direction.DOWN.ordinal()
        );
        WirePart sourceWire = new WirePart(
                requireWire("red_insulated_wire"),
                Direction.DOWN.ordinal()
        );
        WirePart outputCable = new WirePart(
                requireWire("neutral_bundled_wire"),
                Direction.DOWN.ordinal()
        );
        WirePart sinkWire = new WirePart(
                requireWire("red_insulated_wire"),
                Direction.DOWN.ordinal()
        );

        helper.assertTrue(gateMultipart.add(transceiver), "failed to add bus transceiver");
        helper.assertTrue(inputMultipart.add(inputCable), "failed to add input bundled cable");
        helper.assertTrue(sourceMultipart.add(sourceWire), "failed to add source insulated wire");
        helper.assertTrue(outputMultipart.add(outputCable), "failed to add output bundled cable");
        helper.assertTrue(sinkMultipart.add(sinkWire), "failed to add sink insulated wire");

        sourceMultipart.onNeighborSignalChanged();
        gateMultipart.onNeighborSignalChanged();

        helper.runAfterDelay(4, () -> {
            int red = net.minecraft.world.item.DyeColor.RED.getId();
            helper.assertTrue(
                    inputCable.bundled()[red] > 0,
                    "input bus must carry the red insulated channel"
            );
            helper.assertTrue(
                    outputCable.bundled()[red] > 0,
                    "east control input must route south bundled input to north output"
            );
            helper.assertTrue(
                    sinkWire.signal() > 0,
                    "routed bundled channel must feed matching insulated wire"
            );
            helper.succeed();
        });
    }

    @GameTest(structure = EMPTY, maxTicks = 60)
    public void comparatorKeepsTwoTickAnalogTransition(GameTestHelper helper) {
        BlockPos gateRel = new BlockPos(3, 2, 3);
        BlockPos backRel = gateRel.south();
        BlockPos sideRel = gateRel.east();

        helper.setBlock(gateRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(gateRel, PRContent.MULTIPART.defaultBlockState());

        MultipartBlockEntity multipart = multipart(helper, gateRel);
        GatePart comparator = new GatePart(
                GateType.COMPARATOR,
                Direction.DOWN.ordinal(),
                0
        );
        helper.assertTrue(multipart.add(comparator), "failed to add comparator");

        helper.setBlock(backRel, Blocks.REDSTONE_BLOCK.defaultBlockState());
        multipart.onNeighborSignalChanged();

        helper.assertTrue(
                multipart.vanillaSignal(Direction.NORTH) == 0,
                "comparator output must not update before its scheduled transition"
        );

        helper.runAfterDelay(3, () -> {
            helper.assertTrue(
                    multipart.vanillaSignal(Direction.NORTH) == 15,
                    "compare-mode comparator must reproduce back analog level 15"
            );

            helper.setBlock(sideRel, Blocks.REDSTONE_BLOCK.defaultBlockState());
            multipart.onNeighborSignalChanged();

            helper.runAfterDelay(3, () -> {
                helper.assertTrue(
                        multipart.vanillaSignal(Direction.NORTH) == 0,
                        "equal side input must suppress compare-mode output"
                );
                helper.succeed();
            });
        });
    }

    @GameTest(structure = EMPTY, maxTicks = 40)
    public void redAlloyWireWrapsAroundOpenOuterCorner(GameTestHelper helper) {
        BlockPos topRel = new BlockPos(3, 3, 3);
        BlockPos supportRel = topRel.below();
        BlockPos cornerRel = topRel.east().below();
        BlockPos sourceRel = topRel.west();

        helper.setBlock(supportRel, Blocks.STONE.defaultBlockState());
        helper.setBlock(sourceRel, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(topRel, PRContent.MULTIPART.defaultBlockState());
        helper.setBlock(cornerRel, PRContent.MULTIPART.defaultBlockState());

        MultipartBlockEntity top = multipart(helper, topRel);
        MultipartBlockEntity corner = multipart(helper, cornerRel);

        WirePart topWire = new WirePart(
                requireWire("red_alloy_wire"),
                Direction.DOWN.ordinal()
        );
        WirePart cornerWire = new WirePart(
                requireWire("red_alloy_wire"),
                Direction.WEST.ordinal()
        );

        helper.assertTrue(top.add(topWire), "failed to add top red-alloy wire");
        helper.assertTrue(corner.add(cornerWire), "failed to add corner red-alloy wire");

        top.onNeighborSignalChanged();

        helper.assertTrue(
                topWire.signal() == 255,
                "top wire should be fully powered by adjacent redstone block"
        );
        helper.assertTrue(
                cornerWire.signal() == 254,
                "outer-corner wire must connect around the support block with one-step attenuation"
        );
        helper.succeed();
    }


    @GameTest(structure = EMPTY, maxTicks = 40)
    public void nullCellKeepsCrossingRedwireTracksIndependent(GameTestHelper helper) {
        BlockPos gateRel = new BlockPos(3, 2, 3);
        BlockPos northRel = gateRel.north();

        helper.setBlock(gateRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(gateRel, PRContent.MULTIPART.defaultBlockState());
        helper.setBlock(northRel, Blocks.REDSTONE_BLOCK.defaultBlockState());

        MultipartBlockEntity multipart = multipart(helper, gateRel);
        GatePart cell = new GatePart(
                GateType.NULL_CELL,
                Direction.DOWN.ordinal(),
                0
        );
        helper.assertTrue(multipart.add(cell), "failed to add null cell");

        multipart.onNeighborSignalChanged();

        helper.assertTrue(
                multipart.vanillaSignal(Direction.SOUTH) == 15,
                "north input must pass through the null-cell north/south track"
        );
        helper.assertTrue(
                multipart.vanillaSignal(Direction.EAST) == 0,
                "north input must not leak into the east/west crossing track"
        );
        helper.assertTrue(
                multipart.vanillaSignal(Direction.WEST) == 0,
                "north input must not leak into the east/west crossing track"
        );

        helper.setBlock(gateRel.east(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        multipart.onNeighborSignalChanged();

        helper.assertTrue(
                multipart.vanillaSignal(Direction.WEST) == 15,
                "east input must independently pass through the east/west track"
        );
        helper.succeed();
    }

    @GameTest(structure = EMPTY, maxTicks = 40)
    public void oppositeNullCellsAutoRotateForCrossingPlacement(GameTestHelper helper) {
        BlockPos multipartRel = new BlockPos(3, 2, 3);

        helper.setBlock(multipartRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(multipartRel.above(), Blocks.STONE.defaultBlockState());
        helper.setBlock(multipartRel, PRContent.MULTIPART.defaultBlockState());

        MultipartBlockEntity multipart = multipart(helper, multipartRel);

        GatePart bottom = new GatePart(
                GateType.NULL_CELL,
                Direction.DOWN.ordinal(),
                0
        );
        helper.assertTrue(multipart.add(bottom), "failed to add first null cell");

        GatePart top = new GatePart(
                GateType.NULL_CELL,
                Direction.UP.ordinal(),
                0
        );
        multipart.preparePlacement(top);

        helper.assertTrue(
                top.rotation() == 1,
                "opposite null cell with matching track must auto-rotate perpendicular"
        );
        helper.assertTrue(
                multipart.canAdd(top),
                "perpendicular opposite null cells must fit in one multipart"
        );
        helper.assertTrue(multipart.add(top), "failed to add crossing null cell");

        WirePart framed = new WirePart(
                requireWire("framed_red_alloy_wire"),
                Part.CENTER_SLOT
        );
        helper.assertTrue(
                !multipart.canAdd(framed),
                "array cells must occlude center/framed wire slot"
        );
        helper.succeed();
    }

    @GameTest(structure = EMPTY, maxTicks = 40)
    public void unsupportedFaceWireDropsWhenSupportIsRemoved(GameTestHelper helper) {
        BlockPos wireRel = new BlockPos(3, 2, 3);
        BlockPos supportRel = wireRel.below();

        helper.setBlock(supportRel, Blocks.STONE.defaultBlockState());
        helper.setBlock(wireRel, PRContent.MULTIPART.defaultBlockState());

        MultipartBlockEntity multipart = multipart(helper, wireRel);
        WirePart wire = new WirePart(
                requireWire("red_alloy_wire"),
                Direction.DOWN.ordinal()
        );
        helper.assertTrue(multipart.add(wire), "failed to add supported wire");

        helper.setBlock(supportRel, Blocks.AIR.defaultBlockState());
        multipart.onNeighborSignalChanged();

        helper.assertTrue(
                helper.getBlockState(wireRel).isAir(),
                "removing support must remove the last unsupported face part"
        );
        helper.succeed();
    }


    @GameTest(structure = EMPTY, maxTicks = 60)
    public void repeaterHonorsConfiguredProjectRedDelay(GameTestHelper helper) {
        BlockPos gateRel = new BlockPos(3, 2, 3);
        BlockPos inputRel = gateRel.south();

        helper.setBlock(gateRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(gateRel, PRContent.MULTIPART.defaultBlockState());

        MultipartBlockEntity multipart = multipart(helper, gateRel);
        GatePart repeater = new GatePart(
                GateType.REPEATER,
                Direction.DOWN.ordinal(),
                0
        );
        repeater.cycleShape(); // ProjectRed shape 1 = 4 ticks.
        helper.assertTrue(multipart.add(repeater), "failed to add repeater");

        helper.setBlock(inputRel, Blocks.REDSTONE_BLOCK.defaultBlockState());
        multipart.onNeighborSignalChanged();

        helper.assertTrue(
                multipart.vanillaSignal(Direction.NORTH) == 0,
                "repeater output must remain low immediately after input"
        );

        helper.runAfterDelay(3, () ->
                helper.assertTrue(
                        multipart.vanillaSignal(Direction.NORTH) == 0,
                        "4-tick repeater must still be low before its scheduled tick"
                )
        );

        helper.runAfterDelay(5, () -> {
            helper.assertTrue(
                    multipart.vanillaSignal(Direction.NORTH) == 15,
                    "4-tick repeater must be high after its configured delay"
            );
            helper.succeed();
        });
    }

    @GameTest(structure = EMPTY, maxTicks = 60)
    public void busConverterReverseModeOutputsHighestBundledChannel(GameTestHelper helper) {
        BlockPos panelRel = new BlockPos(3, 2, 2);
        BlockPos converterRel = panelRel.south();

        helper.setBlock(panelRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(converterRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(panelRel, PRContent.MULTIPART.defaultBlockState());
        helper.setBlock(converterRel, PRContent.MULTIPART.defaultBlockState());

        MultipartBlockEntity panelMultipart = multipart(helper, panelRel);
        MultipartBlockEntity converterMultipart = multipart(helper, converterRel);

        GatePart panel = new GatePart(
                GateType.BUS_INPUT_PANEL,
                Direction.DOWN.ordinal(),
                0
        );
        GatePart converter = new GatePart(
                GateType.BUS_CONVERTER,
                Direction.DOWN.ordinal(),
                0
        );
        converter.cycleShape(); // shape 1 = bundled -> analog

        helper.assertTrue(panelMultipart.add(panel), "failed to add bus input panel");
        helper.assertTrue(converterMultipart.add(converter), "failed to add bus converter");

        panel.togglePanelBit(7);
        panelMultipart.markPartChanged();
        converterMultipart.onNeighborSignalChanged();

        helper.runAfterDelay(5, () -> {
            helper.assertTrue(
                    converterMultipart.vanillaSignal(Direction.SOUTH) == 7,
                    "reverse bus converter must output the highest active bundled channel as analog redstone"
            );
            helper.succeed();
        });
    }


    @GameTest(structure = EMPTY, maxTicks = 40)
    public void framedWireSharesMultipartWithFaceWire(GameTestHelper helper) {
        BlockPos multipartRel = new BlockPos(3, 2, 3);
        BlockPos dustRel = multipartRel.north();
        BlockPos sourceRel = dustRel.north();

        helper.setBlock(multipartRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(dustRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(sourceRel, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(dustRel, Blocks.REDSTONE_WIRE.defaultBlockState());
        helper.setBlock(multipartRel, PRContent.MULTIPART.defaultBlockState());

        MultipartBlockEntity multipart = multipart(helper, multipartRel);

        WirePart face = new WirePart(
                requireWire("red_alloy_wire"),
                Direction.DOWN.ordinal()
        );
        WirePart framed = new WirePart(
                requireWire("framed_red_alloy_wire"),
                Part.CENTER_SLOT
        );

        helper.assertTrue(multipart.add(face), "failed to add face red-alloy wire");
        helper.assertTrue(
                multipart.canAdd(framed),
                "framed center wire must coexist with ordinary face wire"
        );
        helper.assertTrue(multipart.add(framed), "failed to add framed center wire");

        multipart.onNeighborSignalChanged();

        helper.assertTrue(
                face.signal() == 14,
                "face wire must use ProjectRed's POWER-1 vanilla-dust lookup"
        );
        helper.assertTrue(
                framed.signal() == 13,
                "framed center wire must receive the face-wire signal with one additional attenuation"
        );
        helper.assertTrue(
                multipart.hasSlot(Direction.DOWN.ordinal())
                        && multipart.hasSlot(Part.CENTER_SLOT),
                "face and center parts must remain independently addressable"
        );
        helper.succeed();
    }

    @GameTest(structure = EMPTY, maxTicks = 40)
    public void pulseGateEmitsExactlyTwoTickRisingEdgePulse(GameTestHelper helper) {
        BlockPos gateRel = new BlockPos(3, 2, 3);
        BlockPos inputRel = gateRel.south();

        helper.setBlock(gateRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(gateRel, PRContent.MULTIPART.defaultBlockState());

        MultipartBlockEntity multipart = multipart(helper, gateRel);
        GatePart pulse = new GatePart(
                GateType.PULSE,
                Direction.DOWN.ordinal(),
                0
        );
        helper.assertTrue(multipart.add(pulse), "failed to add pulse gate");

        helper.setBlock(inputRel, Blocks.REDSTONE_BLOCK.defaultBlockState());
        multipart.onNeighborSignalChanged();

        helper.assertTrue(
                multipart.vanillaSignal(Direction.NORTH) == 15,
                "pulse gate must go high immediately on the rising edge"
        );

        helper.runAfterDelay(1, () ->
                helper.assertTrue(
                        multipart.vanillaSignal(Direction.NORTH) == 15,
                        "pulse gate must remain high for the full two-tick pulse"
                )
        );

        helper.runAfterDelay(3, () -> {
            helper.assertTrue(
                    multipart.vanillaSignal(Direction.NORTH) == 0,
                    "pulse gate must return low after its two-tick pulse"
            );
            helper.succeed();
        });
    }

    @GameTest(structure = EMPTY, maxTicks = 40)
    public void toggleLatchMovesOutputAfterProjectRedDelay(GameTestHelper helper) {
        BlockPos gateRel = new BlockPos(3, 2, 3);
        BlockPos inputRel = gateRel.east();

        helper.setBlock(gateRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(gateRel, PRContent.MULTIPART.defaultBlockState());

        MultipartBlockEntity multipart = multipart(helper, gateRel);
        GatePart latch = new GatePart(
                GateType.TOGGLE_LATCH,
                Direction.DOWN.ordinal(),
                0
        );
        helper.assertTrue(multipart.add(latch), "failed to add toggle latch");
        helper.assertTrue(
                multipart.vanillaSignal(Direction.NORTH) == 15,
                "fresh toggle latch must begin on its north output"
        );

        helper.setBlock(inputRel, Blocks.REDSTONE_BLOCK.defaultBlockState());
        multipart.onNeighborSignalChanged();

        helper.assertTrue(
                multipart.vanillaSignal(Direction.NORTH) == 15,
                "toggle latch must preserve its old output until the scheduled tick"
        );

        helper.runAfterDelay(3, () -> {
            helper.assertTrue(
                    multipart.vanillaSignal(Direction.NORTH) == 0,
                    "toggle latch north output must switch off after two ticks"
            );
            helper.assertTrue(
                    multipart.vanillaSignal(Direction.SOUTH) == 15,
                    "toggle latch opposite output must switch on after two ticks"
            );
            helper.succeed();
        });
    }

    @GameTest(structure = EMPTY, maxTicks = 60)
    public void counterTracksIncrementAndDecrementEdges(GameTestHelper helper) {
        BlockPos gateRel = new BlockPos(3, 2, 3);
        BlockPos incrementRel = gateRel.east();
        BlockPos decrementRel = gateRel.west();

        helper.setBlock(gateRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(gateRel, PRContent.MULTIPART.defaultBlockState());

        MultipartBlockEntity multipart = multipart(helper, gateRel);
        GatePart counter = new GatePart(
                GateType.COUNTER,
                Direction.DOWN.ordinal(),
                0
        );
        helper.assertTrue(multipart.add(counter), "failed to add counter");

        helper.setBlock(incrementRel, Blocks.REDSTONE_BLOCK.defaultBlockState());
        multipart.onNeighborSignalChanged();

        helper.assertTrue(
                counter.counterValue() == 1,
                "east rising edge must increment the counter"
        );

        helper.runAfterDelay(3, () -> {
            helper.assertTrue(
                    multipart.vanillaSignal(Direction.NORTH) == 0
                            && multipart.vanillaSignal(Direction.SOUTH) == 0,
                    "intermediate counter values must keep both endpoint outputs low"
            );

            helper.setBlock(incrementRel, Blocks.AIR.defaultBlockState());
            multipart.onNeighborSignalChanged();
            helper.setBlock(decrementRel, Blocks.REDSTONE_BLOCK.defaultBlockState());
            multipart.onNeighborSignalChanged();

            helper.assertTrue(
                    counter.counterValue() == 0,
                    "west rising edge must decrement the counter"
            );

            helper.runAfterDelay(3, () -> {
                helper.assertTrue(
                        multipart.vanillaSignal(Direction.SOUTH) == 15,
                        "counter value zero must drive its zero endpoint output"
                );
                helper.succeed();
            });
        });
    }

    @GameTest(structure = EMPTY, maxTicks = 80)
    public void synchronizerWaitsForBothEdgesThenPulses(GameTestHelper helper) {
        BlockPos gateRel = new BlockPos(3, 2, 3);
        BlockPos rightRel = gateRel.east();
        BlockPos leftRel = gateRel.west();

        helper.setBlock(gateRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(gateRel, PRContent.MULTIPART.defaultBlockState());

        MultipartBlockEntity multipart = multipart(helper, gateRel);
        GatePart synchronizer = new GatePart(
                GateType.SYNCHRONIZER,
                Direction.DOWN.ordinal(),
                0
        );
        helper.assertTrue(multipart.add(synchronizer), "failed to add synchronizer");

        helper.setBlock(rightRel, Blocks.REDSTONE_BLOCK.defaultBlockState());
        multipart.onNeighborSignalChanged();

        helper.runAfterDelay(3, () -> {
            helper.assertTrue(
                    multipart.vanillaSignal(Direction.NORTH) == 0,
                    "one synchronizer edge alone must not pulse"
            );

            helper.setBlock(leftRel, Blocks.REDSTONE_BLOCK.defaultBlockState());
            multipart.onNeighborSignalChanged();

            helper.runAfterDelay(3, () ->
                    helper.assertTrue(
                            multipart.vanillaSignal(Direction.NORTH) == 15,
                            "second latched edge must produce the synchronizer pulse"
                    )
            );

            helper.runAfterDelay(6, () -> {
                helper.assertTrue(
                        multipart.vanillaSignal(Direction.NORTH) == 0,
                        "synchronizer pulse must clear after two ticks"
                );
                helper.succeed();
            });
        });
    }

    @GameTest(structure = EMPTY, maxTicks = 80)
    public void busRandomizerRespectsSingleChannelMask(GameTestHelper helper) {
        BlockPos randomizerRel = new BlockPos(4, 2, 4);
        BlockPos panelRel = randomizerRel.south();
        BlockPos outputRel = randomizerRel.north();
        BlockPos controlRel = randomizerRel.east();

        for (BlockPos pos : new BlockPos[] {
                randomizerRel, panelRel, outputRel
        }) {
            helper.setBlock(pos.below(), Blocks.STONE.defaultBlockState());
            helper.setBlock(pos, PRContent.MULTIPART.defaultBlockState());
        }

        MultipartBlockEntity randomizerMultipart = multipart(helper, randomizerRel);
        MultipartBlockEntity panelMultipart = multipart(helper, panelRel);
        MultipartBlockEntity outputMultipart = multipart(helper, outputRel);

        GatePart randomizer = new GatePart(
                GateType.BUS_RANDOMIZER,
                Direction.DOWN.ordinal(),
                0
        );
        GatePart panel = new GatePart(
                GateType.BUS_INPUT_PANEL,
                Direction.DOWN.ordinal(),
                2
        );
        WirePart output = new WirePart(
                requireWire("neutral_bundled_wire"),
                Direction.DOWN.ordinal()
        );

        helper.assertTrue(randomizerMultipart.add(randomizer), "failed to add bus randomizer");
        helper.assertTrue(panelMultipart.add(panel), "failed to add mask panel");
        helper.assertTrue(outputMultipart.add(output), "failed to add randomizer output cable");

        panel.togglePanelBit(5);
        panelMultipart.markPartChanged();

        helper.runAfterDelay(3, () -> {
            helper.setBlock(controlRel, Blocks.REDSTONE_BLOCK.defaultBlockState());
            randomizerMultipart.onNeighborSignalChanged();

            helper.runAfterDelay(3, () -> {
                int[] signal = output.bundled();
                helper.assertTrue(
                        signal[5] == 255,
                        "one-bit randomizer with a one-channel mask must choose that channel"
                );
                for (int channel = 0; channel < 16; channel++) {
                    if (channel == 5) continue;
                    helper.assertTrue(
                            signal[channel] == 0,
                            "bus randomizer must never emit outside its bundled mask"
                    );
                }
                helper.succeed();
            });
        });
    }

    @GameTest(structure = EMPTY, maxTicks = 80)
    public void busInputPanelResetClearsPressedChannels(GameTestHelper helper) {
        BlockPos panelRel = new BlockPos(3, 2, 3);
        BlockPos resetRel = panelRel.north();
        BlockPos outputRel = panelRel.south();

        helper.setBlock(panelRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(outputRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(panelRel, PRContent.MULTIPART.defaultBlockState());
        helper.setBlock(outputRel, PRContent.MULTIPART.defaultBlockState());

        MultipartBlockEntity panelMultipart = multipart(helper, panelRel);
        MultipartBlockEntity outputMultipart = multipart(helper, outputRel);

        GatePart panel = new GatePart(
                GateType.BUS_INPUT_PANEL,
                Direction.DOWN.ordinal(),
                0
        );
        WirePart output = new WirePart(
                requireWire("neutral_bundled_wire"),
                Direction.DOWN.ordinal()
        );

        helper.assertTrue(panelMultipart.add(panel), "failed to add bus input panel");
        helper.assertTrue(outputMultipart.add(output), "failed to add output bundled cable");

        panel.togglePanelBit(9);
        panelMultipart.markPartChanged();

        helper.runAfterDelay(3, () -> {
            helper.assertTrue(
                    output.bundled()[9] == 255,
                    "pressed panel channel must reach bundled output before reset"
            );

            helper.setBlock(resetRel, Blocks.REDSTONE_BLOCK.defaultBlockState());
            panelMultipart.onNeighborSignalChanged();

            helper.runAfterDelay(3, () -> {
                helper.assertTrue(
                        panel.panelMask() == 0,
                        "panel reset input must clear the pressed-channel mask"
                );
                helper.assertTrue(
                        output.bundled()[9] == 0,
                        "cleared panel channel must disappear from bundled output"
                );
                helper.succeed();
            });
        });
    }

    @GameTest(structure = EMPTY, maxTicks = 100)
    public void stateCellHoldsThenEmitsTimedPulse(GameTestHelper helper) {
        BlockPos gateRel = new BlockPos(3, 2, 3);
        BlockPos triggerRel = gateRel.south();

        helper.setBlock(gateRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(gateRel, PRContent.MULTIPART.defaultBlockState());

        MultipartBlockEntity multipart = multipart(helper, gateRel);
        GatePart cell = new GatePart(
                GateType.STATE_CELL,
                Direction.DOWN.ordinal(),
                0
        );
        helper.assertTrue(multipart.add(cell), "failed to add state cell");

        helper.setBlock(triggerRel, Blocks.REDSTONE_BLOCK.defaultBlockState());
        multipart.onNeighborSignalChanged();

        helper.runAfterDelay(3, () -> {
            helper.assertTrue(
                    multipart.vanillaSignal(Direction.WEST) == 15,
                    "armed state cell must assert its hold output after two ticks"
            );

            helper.setBlock(triggerRel, Blocks.AIR.defaultBlockState());
            multipart.onNeighborSignalChanged();

            helper.runAfterDelay(37, () ->
                    helper.assertTrue(
                            multipart.vanillaSignal(Direction.WEST) == 15,
                            "state cell must hold until its configured pointer expires"
                    )
            );

            // Release happens on tick 3. ProjectRed's default State Cell
            // pointer runs for 38 ticks, so its pulse begins on tick 41 and
            // remains high until the scheduled clear on tick 43.
            helper.runAfterDelay(39, () ->
                    helper.assertTrue(
                            multipart.vanillaSignal(Direction.NORTH) == 15,
                            "state cell expiry must emit the ProjectRed two-tick pulse"
                    )
            );

            helper.runAfterDelay(42, () -> {
                helper.assertTrue(
                        multipart.vanillaSignal(Direction.NORTH) == 0,
                        "state cell expiry pulse must clear after two ticks"
                );
                helper.succeed();
            });
        });
    }

    @GameTest(structure = EMPTY, maxTicks = 40)
    public void projectRedGatePowersVanillaRedstoneDust(GameTestHelper helper) {
        BlockPos gateRel = new BlockPos(3, 2, 3);
        BlockPos dustRel = gateRel.north();

        helper.setBlock(gateRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(dustRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(dustRel, Blocks.REDSTONE_WIRE.defaultBlockState());
        helper.setBlock(gateRel, PRContent.MULTIPART.defaultBlockState());

        MultipartBlockEntity multipart = multipart(helper, gateRel);
        GatePart not = new GatePart(
                GateType.NOT,
                Direction.DOWN.ordinal(),
                0
        );
        helper.assertTrue(multipart.add(not), "failed to add NOT gate");

        helper.runAfterDelay(3, () -> {
            int power = helper.getBlockState(dustRel)
                    .getValue(RedstoneWireBlock.POWER);
            helper.assertTrue(
                    power == 15,
                    "normally-high ProjectRed NOT output must drive vanilla dust at full strength"
            );
            helper.succeed();
        });
    }

    @GameTest(structure = EMPTY, maxTicks = 40)
    public void directionalVanillaRepeaterFeedsOnlyTowardProjectRed(GameTestHelper helper) {
        BlockPos wireRel = new BlockPos(3, 2, 3);
        BlockPos repeaterRel = wireRel.west();
        BlockPos sourceRel = repeaterRel.west();

        helper.setBlock(wireRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(repeaterRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(sourceRel, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(
                repeaterRel,
                Blocks.REPEATER.defaultBlockState()
                        .setValue(RepeaterBlock.FACING, Direction.WEST)
        );
        helper.setBlock(wireRel, PRContent.MULTIPART.defaultBlockState());

        MultipartBlockEntity multipart = multipart(helper, wireRel);
        WirePart wire = new WirePart(
                requireWire("red_alloy_wire"),
                Direction.DOWN.ordinal()
        );
        helper.assertTrue(multipart.add(wire), "failed to add red-alloy wire");

        // Let vanilla perform the repeater's own two-tick transition instead
        // of forcing an impossible POWERED blockstate at test tick zero.
        helper.runAfterDelay(4, () -> {
            multipart.onNeighborSignalChanged();
            helper.assertTrue(
                    wire.signal() == 255,
                    "west-input vanilla repeater must feed the ProjectRed wire from its east output"
            );

            helper.setBlock(
                    repeaterRel,
                    Blocks.REPEATER.defaultBlockState()
                            .setValue(RepeaterBlock.FACING, Direction.EAST)
            );

            helper.runAfterDelay(8, () -> {
                multipart.onNeighborSignalChanged();
                helper.assertTrue(
                        wire.signal() == 0,
                        "vanilla repeater facing away must not feed the ProjectRed wire"
                );
                helper.succeed();
            });
        });
    }

    @GameTest(structure = EMPTY, maxTicks = 40)
    public void projectRedGateStrongPowersThroughSolidBlock(GameTestHelper helper) {
        BlockPos gateRel = new BlockPos(3, 2, 3);
        BlockPos conductorRel = gateRel.north();
        BlockPos lampRel = conductorRel.north();

        helper.setBlock(gateRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(conductorRel, Blocks.STONE.defaultBlockState());
        helper.setBlock(lampRel, Blocks.REDSTONE_LAMP.defaultBlockState());
        helper.setBlock(gateRel, PRContent.MULTIPART.defaultBlockState());

        MultipartBlockEntity multipart = multipart(helper, gateRel);
        GatePart not = new GatePart(
                GateType.NOT,
                Direction.DOWN.ordinal(),
                0
        );
        helper.assertTrue(multipart.add(not), "failed to add NOT gate");

        helper.runAfterDelay(3, () -> {
            helper.assertTrue(
                    helper.getBlockState(lampRel)
                            .getValue(BlockStateProperties.LIT),
                    "ProjectRed logic output must strong-power a solid conductor and light the block beyond it"
            );
            helper.succeed();
        });
    }

    @GameTest(structure = EMPTY, maxTicks = 70)
    public void timerEmitsProjectRedTwoTickPulseAfterDefaultPeriod(GameTestHelper helper) {
        BlockPos gateRel = new BlockPos(3, 2, 3);

        helper.setBlock(gateRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(gateRel, PRContent.MULTIPART.defaultBlockState());

        MultipartBlockEntity multipart = multipart(helper, gateRel);
        GatePart timer = new GatePart(
                GateType.TIMER,
                Direction.DOWN.ordinal(),
                0
        );
        helper.assertTrue(multipart.add(timer), "failed to add timer");

        helper.runAfterDelay(36, () ->
                helper.assertTrue(
                        multipart.vanillaSignal(Direction.NORTH) == 0,
                        "timer must remain low before the default 38-tick pointer expires"
                )
        );

        helper.runAfterDelay(39, () ->
                helper.assertTrue(
                        multipart.vanillaSignal(Direction.NORTH) == 15,
                        "timer must emit its pulse when the default pointer expires"
                )
        );

        helper.runAfterDelay(42, () -> {
            helper.assertTrue(
                    multipart.vanillaSignal(Direction.NORTH) == 0,
                    "timer pulse must clear after the two-tick scheduled interval"
            );
            helper.succeed();
        });
    }

    @GameTest(structure = EMPTY, maxTicks = 50)
    public void srLatchStoresTheLastAssertedSide(GameTestHelper helper) {
        BlockPos gateRel = new BlockPos(3, 2, 3);
        BlockPos rightRel = gateRel.east();
        BlockPos leftRel = gateRel.west();

        helper.setBlock(gateRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(gateRel, PRContent.MULTIPART.defaultBlockState());

        MultipartBlockEntity multipart = multipart(helper, gateRel);
        GatePart latch = new GatePart(
                GateType.SR_LATCH,
                Direction.DOWN.ordinal(),
                0
        );
        helper.assertTrue(multipart.add(latch), "failed to add SR latch");
        helper.assertTrue(
                latch.state2() == 2,
                "fresh ProjectRed SR latch must start with local-right state stored"
        );

        helper.setBlock(leftRel, Blocks.REDSTONE_BLOCK.defaultBlockState());
        multipart.onNeighborSignalChanged();
        helper.assertTrue(
                latch.state2() == 8,
                "left rising input must become the stored SR latch side"
        );

        helper.runAfterDelay(3, () -> {
            helper.setBlock(leftRel, Blocks.AIR.defaultBlockState());
            multipart.onNeighborSignalChanged();
            helper.assertTrue(
                    latch.state2() == 8,
                    "SR latch must retain the last asserted side after input release"
            );

            helper.setBlock(rightRel, Blocks.REDSTONE_BLOCK.defaultBlockState());
            multipart.onNeighborSignalChanged();
            helper.assertTrue(
                    latch.state2() == 2,
                    "opposite rising input must replace the stored SR latch side"
            );

            helper.runAfterDelay(6, () -> {
                helper.setBlock(rightRel, Blocks.AIR.defaultBlockState());
                multipart.onNeighborSignalChanged();
                helper.assertTrue(
                        latch.state2() == 2,
                        "SR latch must retain the second stored side after release"
                );
                helper.succeed();
            });
        });
    }

    @GameTest(structure = EMPTY, maxTicks = 30)
    public void sequencerTracksTheMinecraftWorldClock(GameTestHelper helper) {
        BlockPos gateRel = new BlockPos(3, 2, 3);

        helper.setBlock(gateRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(gateRel, PRContent.MULTIPART.defaultBlockState());

        MultipartBlockEntity multipart = multipart(helper, gateRel);
        GatePart sequencer = new GatePart(
                GateType.SEQUENCER,
                Direction.DOWN.ordinal(),
                0
        );
        helper.assertTrue(multipart.add(sequencer), "failed to add sequencer");

        helper.runAfterDelay(3, () -> {
            long clock = helper.getLevel().getDefaultClockTime();
            int step = (int) (Math.floorMod(clock, 160L) / 40L);
            helper.assertTrue(
                    sequencer.state() >> 4 == 1 << step,
                    "sequencer output must match the current ProjectRed 4-phase world-clock step"
            );
            helper.succeed();
        });
    }

    @GameTest(structure = EMPTY, maxTicks = 30)
    public void lightSensorMatchesVanillaSkyAndBlockLight(GameTestHelper helper) {
        BlockPos gateRel = new BlockPos(3, 2, 3);

        helper.setBlock(gateRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(gateRel, PRContent.MULTIPART.defaultBlockState());

        MultipartBlockEntity multipart = multipart(helper, gateRel);
        GatePart sensor = new GatePart(
                GateType.LIGHT_SENSOR,
                Direction.DOWN.ordinal(),
                0
        );
        helper.assertTrue(multipart.add(sensor), "failed to add light sensor");

        helper.runAfterDelay(8, () -> {
            BlockPos absolute = helper.absolutePos(gateRel);
            int sky = helper.getLevel().getBrightness(
                    net.minecraft.world.level.LightLayer.SKY,
                    absolute
            ) - helper.getLevel().getSkyDarken();
            int block = helper.getLevel().getBrightness(
                    net.minecraft.world.level.LightLayer.BLOCK,
                    absolute
            );
            int expected = Math.max(0, Math.min(15, Math.max(sky, block)));

            helper.assertTrue(
                    sensor.outputLocal(2) == expected,
                    "ProjectRed light sensor must expose the same analog level as vanilla lighting"
            );
            helper.succeed();
        });
    }

    @GameTest(structure = EMPTY, maxTicks = 30)
    public void rainSensorTracksActualRainAndSkyVisibility(GameTestHelper helper) {
        BlockPos gateRel = new BlockPos(3, 2, 3);

        helper.setBlock(gateRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(gateRel, PRContent.MULTIPART.defaultBlockState());

        MultipartBlockEntity multipart = multipart(helper, gateRel);
        GatePart sensor = new GatePart(
                GateType.RAIN_SENSOR,
                Direction.DOWN.ordinal(),
                0
        );
        helper.assertTrue(multipart.add(sensor), "failed to add rain sensor");

        helper.runAfterDelay(3, () -> {
            BlockPos absolute = helper.absolutePos(gateRel);
            int expected = helper.getLevel().isRaining()
                    && helper.getLevel().canSeeSky(absolute)
                    ? 15
                    : 0;

            helper.assertTrue(
                    sensor.outputLocal(2) == expected,
                    "ProjectRed rain sensor must follow vanilla rain and sky visibility"
            );

            // RainSensor does not use output-side feedback upstream. External
            // power on local side 2 must not enter its low input nibble.
            helper.setBlock(
                    gateRel.south(),
                    Blocks.REDSTONE_BLOCK.defaultBlockState()
            );
            multipart.onNeighborSignalChanged();

            helper.runAfterDelay(6, () -> {
                helper.assertTrue(
                        (sensor.state() & 0xF) == 0,
                        "rain sensor must not sample feedback from its output side"
                );
                helper.succeed();
            });
        });
    }

    @GameTest(structure = EMPTY, maxTicks = 50)
    public void orAndGatesMatchProjectRedTruthTables(GameTestHelper helper) {
        BlockPos orRel = new BlockPos(2, 2, 2);
        BlockPos andRel = new BlockPos(6, 2, 2);

        for (BlockPos pos : new BlockPos[] { orRel, andRel }) {
            helper.setBlock(pos.below(), Blocks.STONE.defaultBlockState());
            helper.setBlock(pos, PRContent.MULTIPART.defaultBlockState());
        }

        MultipartBlockEntity orMultipart = multipart(helper, orRel);
        MultipartBlockEntity andMultipart = multipart(helper, andRel);
        GatePart orGate = new GatePart(GateType.OR, Direction.DOWN.ordinal(), 0);
        GatePart andGate = new GatePart(GateType.AND, Direction.DOWN.ordinal(), 0);

        helper.assertTrue(orMultipart.add(orGate), "failed to add OR gate");
        helper.assertTrue(andMultipart.add(andGate), "failed to add AND gate");

        helper.setBlock(orRel.east(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        orMultipart.onNeighborSignalChanged();

        helper.setBlock(andRel.east(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(andRel.south(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(andRel.west(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        andMultipart.onNeighborSignalChanged();

        helper.runAfterDelay(3, () -> {
            helper.assertTrue(
                    orMultipart.vanillaSignal(Direction.NORTH) == 15,
                    "OR gate must assert with any enabled input high"
            );
            helper.assertTrue(
                    andMultipart.vanillaSignal(Direction.NORTH) == 15,
                    "AND gate must assert only when all enabled inputs are high"
            );

            helper.setBlock(orRel.east(), Blocks.AIR.defaultBlockState());
            orMultipart.onNeighborSignalChanged();

            helper.setBlock(andRel.west(), Blocks.AIR.defaultBlockState());
            andMultipart.onNeighborSignalChanged();

            helper.runAfterDelay(6, () -> {
                helper.assertTrue(
                        orMultipart.vanillaSignal(Direction.NORTH) == 0,
                        "OR gate must clear when all inputs are low"
                );
                helper.assertTrue(
                        andMultipart.vanillaSignal(Direction.NORTH) == 0,
                        "AND gate must clear when any enabled input goes low"
                );
                helper.succeed();
            });
        });
    }

    @GameTest(structure = EMPTY, maxTicks = 50)
    public void xorAndXnorRemainComplementary(GameTestHelper helper) {
        BlockPos xorRel = new BlockPos(2, 2, 2);
        BlockPos xnorRel = new BlockPos(6, 2, 2);

        for (BlockPos pos : new BlockPos[] { xorRel, xnorRel }) {
            helper.setBlock(pos.below(), Blocks.STONE.defaultBlockState());
            helper.setBlock(pos, PRContent.MULTIPART.defaultBlockState());
        }

        MultipartBlockEntity xorMultipart = multipart(helper, xorRel);
        MultipartBlockEntity xnorMultipart = multipart(helper, xnorRel);
        GatePart xor = new GatePart(GateType.XOR, Direction.DOWN.ordinal(), 0);
        GatePart xnor = new GatePart(GateType.XNOR, Direction.DOWN.ordinal(), 0);

        helper.assertTrue(xorMultipart.add(xor), "failed to add XOR gate");
        helper.assertTrue(xnorMultipart.add(xnor), "failed to add XNOR gate");

        helper.setBlock(xorRel.east(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(xnorRel.east(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        xorMultipart.onNeighborSignalChanged();
        xnorMultipart.onNeighborSignalChanged();

        helper.runAfterDelay(3, () -> {
            helper.assertTrue(
                    xorMultipart.vanillaSignal(Direction.NORTH) == 15,
                    "XOR must assert for exactly one high input"
            );
            helper.assertTrue(
                    xnorMultipart.vanillaSignal(Direction.NORTH) == 0,
                    "XNOR must clear for exactly one high input"
            );

            helper.setBlock(xorRel.west(), Blocks.REDSTONE_BLOCK.defaultBlockState());
            helper.setBlock(xnorRel.west(), Blocks.REDSTONE_BLOCK.defaultBlockState());
            xorMultipart.onNeighborSignalChanged();
            xnorMultipart.onNeighborSignalChanged();

            helper.runAfterDelay(6, () -> {
                helper.assertTrue(
                        xorMultipart.vanillaSignal(Direction.NORTH) == 0,
                        "XOR must clear when both inputs are equal"
                );
                helper.assertTrue(
                        xnorMultipart.vanillaSignal(Direction.NORTH) == 15,
                        "XNOR must assert when both inputs are equal"
                );
                helper.succeed();
            });
        });
    }

    @GameTest(structure = EMPTY, maxTicks = 60)
    public void multiplexerSelectsTheProjectRedSideInput(GameTestHelper helper) {
        BlockPos gateRel = new BlockPos(3, 2, 3);

        helper.setBlock(gateRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(gateRel, PRContent.MULTIPART.defaultBlockState());

        MultipartBlockEntity multipart = multipart(helper, gateRel);
        GatePart mux = new GatePart(GateType.MULTIPLEXER, Direction.DOWN.ordinal(), 0);
        helper.assertTrue(multipart.add(mux), "failed to add multiplexer");

        // Local 1/east is selected while local 2/south (selector) is low.
        helper.setBlock(gateRel.east(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        multipart.onNeighborSignalChanged();

        helper.runAfterDelay(3, () -> {
            helper.assertTrue(
                    multipart.vanillaSignal(Direction.NORTH) == 15,
                    "multiplexer must forward its east input while selector is low"
            );

            // Raising selector chooses local 3/west, which is currently low.
            helper.setBlock(gateRel.south(), Blocks.REDSTONE_BLOCK.defaultBlockState());
            multipart.onNeighborSignalChanged();

            helper.runAfterDelay(6, () -> {
                helper.assertTrue(
                        multipart.vanillaSignal(Direction.NORTH) == 0,
                        "multiplexer must switch to the west input while selector is high"
                );

                helper.setBlock(gateRel.west(), Blocks.REDSTONE_BLOCK.defaultBlockState());
                multipart.onNeighborSignalChanged();

                helper.runAfterDelay(9, () -> {
                    helper.assertTrue(
                            multipart.vanillaSignal(Direction.NORTH) == 15,
                            "selected west input must propagate through the multiplexer"
                    );
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(structure = EMPTY, maxTicks = 70)
    public void transparentLatchTracksThenHolds(GameTestHelper helper) {
        BlockPos gateRel = new BlockPos(3, 2, 3);

        helper.setBlock(gateRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(gateRel, PRContent.MULTIPART.defaultBlockState());

        MultipartBlockEntity multipart = multipart(helper, gateRel);
        GatePart latch = new GatePart(
                GateType.TRANSPARENT_LATCH,
                Direction.DOWN.ordinal(),
                0
        );
        helper.assertTrue(multipart.add(latch), "failed to add transparent latch");

        // Shape 0: local 2/south is enable and local 3/west is data.
        helper.setBlock(gateRel.south(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(gateRel.west(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        multipart.onNeighborSignalChanged();

        helper.runAfterDelay(3, () -> {
            helper.assertTrue(
                    multipart.vanillaSignal(Direction.NORTH) == 15,
                    "transparent latch must pass high data while enabled"
            );

            // Disable first: the current high output should be held.
            helper.setBlock(gateRel.south(), Blocks.AIR.defaultBlockState());
            multipart.onNeighborSignalChanged();

            helper.runAfterDelay(6, () -> {
                helper.setBlock(gateRel.west(), Blocks.AIR.defaultBlockState());
                multipart.onNeighborSignalChanged();

                helper.runAfterDelay(9, () -> {
                    helper.assertTrue(
                            multipart.vanillaSignal(Direction.NORTH) == 15,
                            "transparent latch must retain its value while disabled"
                    );

                    // Re-enable with low data and it must update low.
                    helper.setBlock(gateRel.south(), Blocks.REDSTONE_BLOCK.defaultBlockState());
                    multipart.onNeighborSignalChanged();

                    helper.runAfterDelay(12, () -> {
                        helper.assertTrue(
                                multipart.vanillaSignal(Direction.NORTH) == 0,
                                "transparent latch must resume tracking when enabled"
                        );
                        helper.succeed();
                    });
                });
            });
        });
    }

    @GameTest(structure = EMPTY, maxTicks = 50)
    public void segmentDisplayReceivesAllBundledChannels(GameTestHelper helper) {
        BlockPos panelRel = new BlockPos(3, 2, 2);
        BlockPos displayRel = panelRel.south();

        helper.setBlock(panelRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(displayRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(panelRel, PRContent.MULTIPART.defaultBlockState());
        helper.setBlock(displayRel, PRContent.MULTIPART.defaultBlockState());

        MultipartBlockEntity panelMultipart = multipart(helper, panelRel);
        MultipartBlockEntity displayMultipart = multipart(helper, displayRel);

        GatePart panel = new GatePart(
                GateType.BUS_INPUT_PANEL,
                Direction.DOWN.ordinal(),
                0
        );
        GatePart display = new GatePart(
                GateType.SEGMENT_DISPLAY,
                Direction.DOWN.ordinal(),
                0
        );

        helper.assertTrue(panelMultipart.add(panel), "failed to add bus input panel");
        helper.assertTrue(displayMultipart.add(display), "failed to add segment display");

        panel.togglePanelBit(2);
        panel.togglePanelBit(11);
        panelMultipart.markPartChanged();

        helper.runAfterDelay(4, () -> {
            int mask = display.segmentMask();
            helper.assertTrue(
                    (mask & (1 << 2)) != 0,
                    "segment display must receive bundled channel 2"
            );
            helper.assertTrue(
                    (mask & (1 << 11)) != 0,
                    "segment display must receive bundled channel 11"
            );
            helper.assertTrue(
                    Integer.bitCount(mask) == 2,
                    "segment display must not invent unrelated bundled channels"
            );
            helper.succeed();
        });
    }

    @GameTest(structure = EMPTY, maxTicks = 70)
    public void invertAndBufferCellsDriveTheirCrossingTrack(GameTestHelper helper) {
        BlockPos invertRel = new BlockPos(2, 2, 2);
        BlockPos bufferRel = new BlockPos(6, 2, 2);

        for (BlockPos pos : new BlockPos[] { invertRel, bufferRel }) {
            helper.setBlock(pos.below(), Blocks.STONE.defaultBlockState());
            helper.setBlock(pos.north(), Blocks.REDSTONE_BLOCK.defaultBlockState());
            helper.setBlock(pos, PRContent.MULTIPART.defaultBlockState());
        }

        MultipartBlockEntity invertMultipart = multipart(helper, invertRel);
        MultipartBlockEntity bufferMultipart = multipart(helper, bufferRel);
        GatePart invert = new GatePart(
                GateType.INVERT_CELL,
                Direction.DOWN.ordinal(),
                0
        );
        GatePart buffer = new GatePart(
                GateType.BUFFER_CELL,
                Direction.DOWN.ordinal(),
                0
        );

        helper.assertTrue(invertMultipart.add(invert), "failed to add invert cell");
        helper.assertTrue(bufferMultipart.add(buffer), "failed to add buffer cell");

        invertMultipart.onNeighborSignalChanged();
        bufferMultipart.onNeighborSignalChanged();

        helper.runAfterDelay(3, () -> {
            helper.assertTrue(
                    invertMultipart.vanillaSignal(Direction.EAST) == 0,
                    "invert cell crossing output must be low while its north/south input is high"
            );
            helper.assertTrue(
                    bufferMultipart.vanillaSignal(Direction.EAST) == 15,
                    "buffer cell crossing output must be high while its north/south input is high"
            );

            helper.setBlock(invertRel.north(), Blocks.AIR.defaultBlockState());
            helper.setBlock(bufferRel.north(), Blocks.AIR.defaultBlockState());
            invertMultipart.onNeighborSignalChanged();
            bufferMultipart.onNeighborSignalChanged();

            helper.runAfterDelay(6, () -> {
                helper.assertTrue(
                        invertMultipart.vanillaSignal(Direction.WEST) == 15,
                        "invert cell crossing output must rise after its input goes low"
                );
                helper.assertTrue(
                        bufferMultipart.vanillaSignal(Direction.WEST) == 0,
                        "buffer cell crossing output must clear after its input goes low"
                );
                helper.succeed();
            });
        });
    }

    @GameTest(structure = EMPTY, maxTicks = 60)
    public void andCellCombinesDigitalInputWithRedwireTrack(GameTestHelper helper) {
        BlockPos cellRel = new BlockPos(3, 2, 3);

        helper.setBlock(cellRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(cellRel.east(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(cellRel, PRContent.MULTIPART.defaultBlockState());

        MultipartBlockEntity multipart = multipart(helper, cellRel);
        GatePart cell = new GatePart(
                GateType.AND_CELL,
                Direction.DOWN.ordinal(),
                0
        );
        helper.assertTrue(multipart.add(cell), "failed to add AND cell");

        multipart.onNeighborSignalChanged();

        helper.assertTrue(
                multipart.vanillaSignal(Direction.WEST) == 15,
                "AND cell must pass its east/west redwire track independently"
        );
        helper.assertTrue(
                multipart.vanillaSignal(Direction.NORTH) == 0,
                "AND cell logic output must stay low without its south digital input"
        );

        helper.setBlock(cellRel.south(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        multipart.onNeighborSignalChanged();

        helper.runAfterDelay(3, () -> {
            helper.assertTrue(
                    multipart.vanillaSignal(Direction.NORTH) == 15,
                    "AND cell must assert when redwire track and south input are both high"
            );

            helper.setBlock(cellRel.east(), Blocks.AIR.defaultBlockState());
            multipart.onNeighborSignalChanged();

            helper.runAfterDelay(6, () -> {
                helper.assertTrue(
                        multipart.vanillaSignal(Direction.NORTH) == 0,
                        "AND cell must clear when its redwire track falls"
                );
                helper.succeed();
            });
        });
    }

    @GameTest(structure = EMPTY, maxTicks = 80)
    public void transparentLatchCellHoldsUntilItsRedwireTrackReturns(GameTestHelper helper) {
        BlockPos cellRel = new BlockPos(3, 2, 3);

        helper.setBlock(cellRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(cellRel.east(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(cellRel.south(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(cellRel, PRContent.MULTIPART.defaultBlockState());

        MultipartBlockEntity multipart = multipart(helper, cellRel);
        GatePart cell = new GatePart(
                GateType.TRANSPARENT_LATCH_CELL,
                Direction.DOWN.ordinal(),
                0
        );
        helper.assertTrue(
                multipart.add(cell),
                "failed to add transparent latch cell"
        );

        multipart.onNeighborSignalChanged();

        helper.runAfterDelay(3, () -> {
            helper.assertTrue(
                    multipart.vanillaSignal(Direction.NORTH) == 15,
                    "enabled transparent latch cell must copy its high digital input"
            );

            // Drop the redwire track first. Upstream holds the existing output
            // whenever the 0xA redwire mask is inactive.
            helper.setBlock(cellRel.east(), Blocks.AIR.defaultBlockState());
            multipart.onNeighborSignalChanged();

            helper.runAfterDelay(6, () -> {
                helper.setBlock(cellRel.south(), Blocks.AIR.defaultBlockState());
                multipart.onNeighborSignalChanged();

                helper.runAfterDelay(9, () -> {
                    helper.assertTrue(
                            multipart.vanillaSignal(Direction.NORTH) == 15,
                            "transparent latch cell must hold while its redwire track is inactive"
                    );

                    helper.setBlock(
                            cellRel.east(),
                            Blocks.REDSTONE_BLOCK.defaultBlockState()
                    );
                    multipart.onNeighborSignalChanged();

                    helper.runAfterDelay(12, () -> {
                        helper.assertTrue(
                                multipart.vanillaSignal(Direction.NORTH) == 0,
                                "restoring the redwire track must resume tracking the low digital input"
                        );
                        helper.succeed();
                    });
                });
            });
        });
    }

    @GameTest(structure = EMPTY, maxTicks = 30)
    public void normallyHighGatesInitializeImmediately(GameTestHelper helper) {
        GateType[] types = {
                GateType.NOR,
                GateType.NAND,
                GateType.NOT,
                GateType.XNOR
        };
        BlockPos[] positions = {
                new BlockPos(1, 2, 2),
                new BlockPos(3, 2, 2),
                new BlockPos(5, 2, 2),
                new BlockPos(7, 2, 2)
        };

        for (int i = 0; i < types.length; i++) {
            BlockPos pos = positions[i];
            helper.setBlock(pos.below(), Blocks.STONE.defaultBlockState());
            helper.setBlock(pos, PRContent.MULTIPART.defaultBlockState());

            MultipartBlockEntity multipart = multipart(helper, pos);
            GatePart gate = new GatePart(
                    types[i],
                    Direction.DOWN.ordinal(),
                    0
            );
            helper.assertTrue(
                    multipart.add(gate),
                    "failed to add normally-high gate " + types[i]
            );
            helper.assertTrue(
                    multipart.vanillaSignal(Direction.NORTH) == 15,
                    types[i] + " must expose its initial high output on placement"
            );
        }

        helper.succeed();
    }

    private static Rig line(
            GameTestHelper helper,
            String firstId,
            String secondId
    ) {
        BlockPos firstRel = new BlockPos(2, 2, 2);
        BlockPos secondRel = firstRel.east();
        BlockPos sourceRel = firstRel.west();

        helper.setBlock(firstRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(secondRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(sourceRel, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(firstRel, PRContent.MULTIPART.defaultBlockState());
        helper.setBlock(secondRel, PRContent.MULTIPART.defaultBlockState());

        MultipartBlockEntity first = multipart(helper, firstRel);
        MultipartBlockEntity second = multipart(helper, secondRel);

        WireSpec firstSpec = requireWire(firstId);
        WireSpec secondSpec = requireWire(secondId);

        WirePart firstWire = new WirePart(
                firstSpec,
                Direction.DOWN.ordinal()
        );
        WirePart secondWire = new WirePart(
                secondSpec,
                Direction.DOWN.ordinal()
        );

        helper.assertTrue(first.add(firstWire), "failed to add first wire");
        helper.assertTrue(second.add(secondWire), "failed to add second wire");

        return new Rig(first, second, firstWire, secondWire);
    }

    private static MultipartBlockEntity multipart(
            GameTestHelper helper,
            BlockPos relative
    ) {
        BlockEntity entity = helper.getLevel().getBlockEntity(
                helper.absolutePos(relative)
        );
        if (!(entity instanceof MultipartBlockEntity multipart)) {
            helper.fail("missing ProjectRed multipart block entity at " + relative);
            throw new AssertionError();
        }
        return multipart;
    }

    private static WireSpec requireWire(String id) {
        WireSpec spec = WireSpec.byId(id);
        if (spec == null) {
            throw new AssertionError("missing wire spec " + id);
        }
        return spec;
    }

    private record Rig(
            MultipartBlockEntity first,
            MultipartBlockEntity second,
            WirePart firstWire,
            WirePart secondWire
    ) {}
}

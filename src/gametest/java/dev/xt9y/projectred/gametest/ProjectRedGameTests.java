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
import net.minecraft.world.level.block.entity.BlockEntity;

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

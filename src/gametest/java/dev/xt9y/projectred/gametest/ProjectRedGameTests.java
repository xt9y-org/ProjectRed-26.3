package dev.xt9y.projectred.gametest;

import dev.xt9y.projectred.content.PRContent;
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

package dev.xt9y.projectred.multipart;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class PlacementRules {
    public static boolean canPlaceWireOnSide(
            Level level,
            BlockPos pos,
            Direction side
    ) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        if (block == Blocks.GLOWSTONE
                || block == Blocks.PISTON
                || block == Blocks.STICKY_PISTON
                || block == Blocks.PISTON_HEAD) {
            return true;
        }

        return state.isFaceSturdy(level, pos, side);
    }

    public static boolean canPlaceGateOnSide(
            Level level,
            BlockPos pos,
            Direction side
    ) {
        if (canPlaceWireOnSide(level, pos, side)) {
            return true;
        }
        return level.getBlockState(pos).is(Blocks.GLASS);
    }

    private PlacementRules() {}
}

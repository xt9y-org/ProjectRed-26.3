package dev.xt9y.projectred.item;

import dev.xt9y.projectred.content.PRContent;
import dev.xt9y.projectred.multipart.MultipartBlockEntity;
import dev.xt9y.projectred.multipart.Part;
import dev.xt9y.projectred.multipart.PlacementRules;
import dev.xt9y.projectred.transmission.WirePart;
import dev.xt9y.projectred.transmission.WireSpec;
import dev.xt9y.projectred.integration.GatePart;
import dev.xt9y.projectred.integration.GateType;
import java.util.function.IntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class PartItem extends Item {
    private final WireSpec wire;
    private final GateType gate;

    public PartItem(WireSpec wire, Properties properties) {
        super(properties);
        this.wire = wire;
        this.gate = null;
    }

    public PartItem(GateType gate, Properties properties) {
        super(properties);
        this.wire = null;
        this.gate = gate;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clicked = context.getClickedPos();
        Direction clickedFace = context.getClickedFace();

        if (level.getBlockEntity(clicked) instanceof MultipartBlockEntity existing) {
            int slot = slotFor(clickedFace);
            Part candidate = create(slot, context);
            existing.preparePlacement(candidate);
            if (canSupport(level, clicked, slot)
                    && existing.canAdd(candidate)) {
                if (!level.isClientSide()
                        && existing.add(candidate)) {
                    consume(context.getPlayer(), context);
                }
                return InteractionResult.SUCCESS;
            }
        }

        BlockPos target = clicked.relative(clickedFace);
        int slot = slotFor(clickedFace.getOpposite());
        if (!canSupport(level, target, slot)) return InteractionResult.FAIL;

        if (!level.isClientSide()) {
            if (level.isEmptyBlock(target)) {
                level.setBlock(target, PRContent.MULTIPART.defaultBlockState(), 3);
            }
            if (level.getBlockEntity(target) instanceof MultipartBlockEntity be) {
                Part candidate = create(slot, context);
                be.preparePlacement(candidate);
                if (be.add(candidate)) {
                    consume(context.getPlayer(), context);
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.FAIL;
        }

        return InteractionResult.SUCCESS;
    }

    private int slotFor(Direction attachment) {
        return wire != null && wire.framed() ? Part.CENTER_SLOT : attachment.ordinal();
    }

    private Part create(int slot, UseOnContext context) {
        if (wire != null) {
            return new WirePart(wire, slot);
        }
        return new GatePart(gate, slot, placementRotation(context, slot));
    }

    private static int placementRotation(UseOnContext context, int slot) {
        if (slot == Part.CENTER_SLOT) return 0;

        Player player = context.getPlayer();
        if (player == null) return 0;

        Direction attachment = Direction.values()[slot];
        net.minecraft.world.phys.Vec3 look = player.getViewVector(1.0F);

        // CodeChickenLib Rotation#getSidedRotation projects the player's full
        // look vector onto the four directions in the gate plane. Combined
        // with ProjectRed's toAbsolute(+2) convention, local side 0 ends up
        // pointing along the strongest projected look direction. Selecting
        // the rotation this way reproduces the same result without CCL.
        int bestRotation = 0;
        double bestDot = Double.NEGATIVE_INFINITY;

        for (int rotation = 0; rotation < 4; rotation++) {
            Direction direction = MultipartBlockEntity.localToWorld(
                    attachment,
                    rotation,
                    0
            );
            double dot = look.x * direction.getStepX()
                    + look.y * direction.getStepY()
                    + look.z * direction.getStepZ();

            if (dot > bestDot) {
                bestDot = dot;
                bestRotation = rotation;
            }
        }

        return bestRotation;
    }

    private boolean canSupport(Level level, BlockPos target, int slot) {
        if (slot == Part.CENTER_SLOT) return true;

        Direction attachment = Direction.values()[slot];
        BlockPos support = target.relative(attachment);
        Direction supportFace = attachment.getOpposite();

        return wire != null
                ? PlacementRules.canPlaceWireOnSide(level, support, supportFace)
                : PlacementRules.canPlaceGateOnSide(level, support, supportFace);
    }

    private static void consume(Player player, UseOnContext context) {
        if (player == null || !player.getAbilities().instabuild) context.getItemInHand().shrink(1);
    }
}

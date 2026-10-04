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
            if (!existing.hasSlot(slot)) {
                if (!level.isClientSide()) {
                    existing.add(create(slot, context));
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
            if (level.getBlockEntity(target) instanceof MultipartBlockEntity be && !be.hasSlot(slot)) {
                be.add(create(slot, context));
                consume(context.getPlayer(), context);
                return InteractionResult.SUCCESS;
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

        Direction attachment = Direction.values()[slot];
        Player player = context.getPlayer();
        Direction desired = player == null ? Direction.NORTH : player.getDirection();

        // When placing on a vertical wall, the player's horizontal facing is
        // often perpendicular to the gate plane. ProjectRed-style placement
        // keeps the gate upright in that case.
        if (desired.getAxis() == attachment.getAxis()) {
            desired = attachment.getAxis().isVertical()
                    ? Direction.NORTH
                    : Direction.UP;
        }

        for (int rotation = 0; rotation < 4; rotation++) {
            if (MultipartBlockEntity.localToWorld(
                    attachment,
                    rotation,
                    0
            ) == desired) {
                return rotation;
            }
        }
        return 0;
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

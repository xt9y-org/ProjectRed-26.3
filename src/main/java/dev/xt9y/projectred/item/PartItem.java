package dev.xt9y.projectred.item;

import dev.xt9y.projectred.content.PRContent;
import dev.xt9y.projectred.multipart.MultipartBlockEntity;
import dev.xt9y.projectred.multipart.Part;
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
                    existing.add(create(slot));
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
                be.add(create(slot));
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

    private Part create(int slot) {
        return wire != null ? new WirePart(wire, slot) : new GatePart(gate, slot, 0);
    }

    private static boolean canSupport(Level level, BlockPos target, int slot) {
        if (slot == Part.CENTER_SLOT) return true;
        Direction attachment = Direction.values()[slot];
        BlockPos support = target.relative(attachment);
        return level.getBlockState(support).isFaceSturdy(level, support, attachment.getOpposite());
    }

    private static void consume(Player player, UseOnContext context) {
        if (player == null || !player.getAbilities().instabuild) context.getItemInHand().shrink(1);
    }
}

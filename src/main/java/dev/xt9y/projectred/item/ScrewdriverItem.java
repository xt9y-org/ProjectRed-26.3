package dev.xt9y.projectred.item;

import dev.xt9y.projectred.integration.GatePart;
import dev.xt9y.projectred.multipart.MultipartBlockEntity;
import dev.xt9y.projectred.multipart.Part;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

public final class ScrewdriverItem extends Item {
    public ScrewdriverItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof MultipartBlockEntity be)) return InteractionResult.PASS;
        int slot = be.slotFromHit(context.getClickLocation());
        Part part = be.part(slot);
        if (!(part instanceof GatePart gate)) return InteractionResult.PASS;

        if (!context.getLevel().isClientSide()) {
            if (context.getPlayer() != null && context.getPlayer().isCrouching()) {
                gate.cycleShape();
            } else {
                gate.rotate();
                if (!be.canRemainAfterGeometryChange(gate)) {
                    // ArrayGatePart::rotate() in upstream asks CBMultipart
                    // whether the rotated part still fits. Undo the rotation
                    // when a crossing array cell would become occluded.
                    gate.rotate();
                    gate.rotate();
                    gate.rotate();
                    return InteractionResult.FAIL;
                }
            }

            if (context.getPlayer() != null) {
                context.getItemInHand().hurtAndBreak(
                        1,
                        context.getPlayer(),
                        context.getHand()
                );
            }

            be.setChanged();
            context.getLevel().sendBlockUpdated(context.getClickedPos(), context.getLevel().getBlockState(context.getClickedPos()), context.getLevel().getBlockState(context.getClickedPos()), 3);
        }
        return InteractionResult.SUCCESS;
    }
}

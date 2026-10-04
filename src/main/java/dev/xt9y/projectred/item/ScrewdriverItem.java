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
            if (context.getPlayer() != null && context.getPlayer().isCrouching()) gate.cycleShape();
            else gate.rotate();
            be.setChanged();
            context.getLevel().sendBlockUpdated(context.getClickedPos(), context.getLevel().getBlockState(context.getClickedPos()), context.getLevel().getBlockState(context.getClickedPos()), 3);
        }
        return InteractionResult.SUCCESS;
    }
}

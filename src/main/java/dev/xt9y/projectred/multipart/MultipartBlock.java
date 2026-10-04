package dev.xt9y.projectred.multipart;

import dev.xt9y.projectred.content.PRContent;
import dev.xt9y.projectred.integration.GatePart;
import dev.xt9y.projectred.integration.GateType;
import dev.xt9y.projectred.network.PRNetworking;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;

public final class MultipartBlock extends BaseEntityBlock {
    public MultipartBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return level.getBlockEntity(pos) instanceof MultipartBlockEntity be ? be.shape() : Shapes.block();
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof MultipartBlockEntity be ? be.vanillaSignal(direction) : 0;
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        BlockEntity be = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (!(be instanceof MultipartBlockEntity mp)) return List.of();
        List<ItemStack> drops = new ArrayList<>();
        for (Part p : mp.parts()) {
            ItemStack stack = PRContent.stackFor(p);
            if (!stack.isEmpty()) drops.add(stack);
        }
        return drops;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit
    ) {
        if (!(level.getBlockEntity(pos) instanceof MultipartBlockEntity multipart)) {
            return InteractionResult.PASS;
        }

        int slot = multipart.slotFromHit(hit.getLocation());
        Part part = multipart.part(slot);
        if (!(part instanceof GatePart gate)) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide()) {
            if (gate.type() == GateType.BUS_INPUT_PANEL) {
                gate.togglePanelBit(multipart.panelBit(gate, hit.getLocation()));
            } else if (gate.type() == GateType.TOGGLE_LATCH
                    || gate.type() == GateType.REPEATER) {
                gate.activate();
            } else if (gate.type() == GateType.TIMER
                    || gate.type() == GateType.SEQUENCER
                    || gate.type() == GateType.STATE_CELL
                    || gate.type() == GateType.COUNTER) {
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    PRNetworking.openGateConfig(
                            serverPlayer,
                            multipart,
                            slot,
                            gate
                    );
                }
            } else {
                return InteractionResult.PASS;
            }

            multipart.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
            level.updateNeighborsAt(pos, state.getBlock());
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MultipartBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, PRContent.MULTIPART_BE, MultipartBlockEntity::tick);
    }
}

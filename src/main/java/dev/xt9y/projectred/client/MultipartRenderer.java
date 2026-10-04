package dev.xt9y.projectred.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.xt9y.projectred.integration.GatePart;
import dev.xt9y.projectred.multipart.MultipartBlockEntity;
import dev.xt9y.projectred.multipart.Part;
import dev.xt9y.projectred.transmission.WireFamily;
import dev.xt9y.projectred.transmission.WirePart;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class MultipartRenderer implements BlockEntityRenderer<MultipartBlockEntity, MultipartRenderer.State> {
    private static final Identifier REDSTONE = tex("redstone_block");
    private static final Identifier STONE = tex("smooth_stone");
    private static final Identifier POWERED = tex("redstone_block");

    public MultipartRenderer(BlockEntityRendererProvider.Context context) {}

    public static final class State extends BlockEntityRenderState {
        private List<Visual> parts = List.of();
    }

    private record Visual(
            boolean wire,
            int slot,
            WireFamily family,
            int color,
            boolean powered
    ) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(
            MultipartBlockEntity blockEntity,
            State state,
            float partialTick,
            Vec3 cameraPos,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress
    ) {
        BlockEntityRenderState.extractBase(blockEntity, state, breakProgress);
        List<Visual> out = new ArrayList<>();
        for (Part part : blockEntity.parts()) {
            if (part instanceof WirePart wire) {
                boolean powered = wire.spec().family() == WireFamily.BUNDLED
                        ? java.util.Arrays.stream(wire.bundled()).anyMatch(v -> v > 0)
                        : wire.signal() > 0;
                out.add(new Visual(true, part.slot(), wire.spec().family(), wire.spec().color(), powered));
            } else if (part instanceof GatePart gate) {
                out.add(new Visual(false, part.slot(), null, -1, (gate.state() & 0xF0) != 0));
            }
        }
        state.parts = out;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraState) {
        int order = 1;
        for (Visual part : state.parts) {
            if (part.wire) {
                Identifier texture = wireTexture(part);
                collector.order(order++).submitCustomGeometry(
                        poseStack,
                        RenderTypes.entityCutout(texture),
                        (pose, consumer) -> {
                            if (part.slot == Part.CENTER_SLOT) {
                                RenderGeometry.framedWire(consumer, pose, state.lightCoords);
                            } else {
                                float width = part.family == WireFamily.BUNDLED ? .50F :
                                        part.family == WireFamily.INSULATED ? .375F : .25F;
                                RenderGeometry.facePart(
                                        consumer,
                                        pose,
                                        state.lightCoords,
                                        Direction.values()[part.slot],
                                        width,
                                        .0625F
                                );
                            }
                        }
                );
            } else {
                Direction attachment = Direction.values()[part.slot];
                collector.order(order++).submitCustomGeometry(
                        poseStack,
                        RenderTypes.entityCutout(STONE),
                        (pose, consumer) -> RenderGeometry.gateBoard(consumer, pose, state.lightCoords, attachment)
                );
                if (part.powered) {
                    collector.order(order++).submitCustomGeometry(
                            poseStack,
                            RenderTypes.entityCutout(POWERED),
                            (pose, consumer) -> RenderGeometry.facePart(
                                    consumer, pose, 0x00F000F0, attachment, .25F, .14F)
                    );
                }
            }
        }
    }

    private static Identifier wireTexture(Visual part) {
        if (part.family == WireFamily.RED_ALLOY) return REDSTONE;
        String color = colorName(part.color);
        return tex(color + "_concrete");
    }

    private static String colorName(int color) {
        String[] names = {
                "white","orange","magenta","light_blue","yellow","lime","pink","gray",
                "light_gray","cyan","purple","blue","brown","green","red","black"
        };
        if (color < 0 || color >= names.length) return "gray";
        return names[color];
    }

    private static Identifier tex(String path) {
        return Identifier.fromNamespaceAndPath("minecraft", "textures/block/" + path + ".png");
    }
}

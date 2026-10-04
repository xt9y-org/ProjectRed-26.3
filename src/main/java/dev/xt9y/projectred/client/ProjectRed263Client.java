package dev.xt9y.projectred.client;

import dev.xt9y.projectred.content.PRContent;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;

public final class ProjectRed263Client implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockEntityRendererRegistry.register(
                PRContent.MULTIPART_BE,
                MultipartRenderer::new
        );
    }
}

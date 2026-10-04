package dev.xt9y.projectred;

import dev.xt9y.projectred.content.PRContent;
import dev.xt9y.projectred.network.PRNetworking;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProjectRed263 implements ModInitializer {
    public static final String MOD_ID = "projectred";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        PRContent.initialize();
        PRNetworking.initialize();
        LOGGER.info("ProjectRed 26.3 redstone-only port initialized");
    }
}

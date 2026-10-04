package dev.xt9y.projectred.content;

import dev.xt9y.projectred.ProjectRed263;
import dev.xt9y.projectred.integration.GatePart;
import dev.xt9y.projectred.integration.GateType;
import dev.xt9y.projectred.item.PartItem;
import dev.xt9y.projectred.item.ScrewdriverItem;
import dev.xt9y.projectred.multipart.MultipartBlock;
import dev.xt9y.projectred.multipart.MultipartBlockEntity;
import dev.xt9y.projectred.multipart.Part;
import dev.xt9y.projectred.transmission.WirePart;
import dev.xt9y.projectred.transmission.WireSpec;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class PRContent {
    public static final Map<String, Item> WIRE_ITEMS = new LinkedHashMap<>();
    public static final Map<GateType, Item> GATE_ITEMS = new LinkedHashMap<>();

    public static final MultipartBlock MULTIPART;
    public static final BlockEntityType<MultipartBlockEntity> MULTIPART_BE;
    public static final Item SCREWDRIVER;
    public static final CreativeModeTab TAB;

    static {
        ResourceKey<Block> multipartKey = ResourceKey.create(Registries.BLOCK, ProjectRed263.id("multipart"));
        MULTIPART = new MultipartBlock(BlockBehaviour.Properties.of()
                .noOcclusion().strength(0.2F).sound(SoundType.METAL).setId(multipartKey));
        Registry.register(BuiltInRegistries.BLOCK, multipartKey, MULTIPART);

        MULTIPART_BE = Registry.register(
                BuiltInRegistries.BLOCK_ENTITY_TYPE,
                ProjectRed263.id("multipart"),
                FabricBlockEntityTypeBuilder.create(MultipartBlockEntity::new, MULTIPART).build()
        );

        for (WireSpec spec : WireSpec.all()) {
            WIRE_ITEMS.put(spec.id(), registerPartItem(spec.id(), properties -> new PartItem(spec, properties)));
        }

        for (GateType type : GateType.values()) {
            GATE_ITEMS.put(type, registerPartItem(type.id(), properties -> new PartItem(type, properties)));
        }

        SCREWDRIVER = registerPartItem("screwdriver", properties -> new ScrewdriverItem(properties.stacksTo(1)));

        ResourceKey<CreativeModeTab> key = ResourceKey.create(BuiltInRegistries.CREATIVE_MODE_TAB.key(), ProjectRed263.id("main"));
        TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, key,
                FabricCreativeModeTab.builder()
                        .title(Component.translatable("creativeTab.projectred"))
                        .icon(() -> new ItemStack(WIRE_ITEMS.get("red_alloy_wire")))
                        .displayItems((params, output) -> {
                            WIRE_ITEMS.values().forEach(output::accept);
                            GATE_ITEMS.values().forEach(output::accept);
                            output.accept(SCREWDRIVER);
                        })
                        .build());
    }

    private static Item registerPartItem(String id, java.util.function.Function<Item.Properties, Item> factory) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, ProjectRed263.id(id));
        Item item = factory.apply(new Item.Properties().setId(key));
        Registry.register(BuiltInRegistries.ITEM, key, item);
        return item;
    }

    public static ItemStack stackFor(Part part) {
        if (part instanceof WirePart wire) {
            Item item = WIRE_ITEMS.get(wire.spec().id());
            return item == null ? ItemStack.EMPTY : new ItemStack(item);
        }
        if (part instanceof GatePart gate) {
            Item item = GATE_ITEMS.get(gate.type());
            return item == null ? ItemStack.EMPTY : new ItemStack(item);
        }
        return ItemStack.EMPTY;
    }

    public static void initialize() {
    }

    private PRContent() {}
}

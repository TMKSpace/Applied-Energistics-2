package appeng.datagen.providers.models;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ItemModelOutput;
import net.minecraft.client.data.models.blockstates.BlockModelDefinitionGenerator;
import net.minecraft.client.data.models.model.ModelInstance;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelDispatcher;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
//#if FABRIC
//#else
import net.minecraft.client.data.models.ModelProvider;
//#endif

import appeng.api.implementations.parts.ICablePart;
import appeng.api.parts.IPartItem;
import appeng.client.api.model.parts.ClientPart;
import appeng.client.api.model.parts.PartModel;
import appeng.core.AppEng;
import appeng.datagen.providers.IAE2DataProvider;

//#if FABRIC
/**
 * Collects AE2's part item models and writes them as {@code assets/ae2/ae2/parts/*.json}. The format and the codec
 * are AE2's own, so nothing here depends on NeoForge - vanilla simply has no model provider to hang it off.
 */
final class AE2PartModelCollector implements PartModelOutput {
    private final Map<IPartItem<?>, ClientPart> parts = new LinkedHashMap<>();
    private final Map<IPartItem<?>, IPartItem<?>> copies = new LinkedHashMap<>();
    private final Supplier<Stream<? extends Holder<Item>>> knownItems;

    AE2PartModelCollector(Supplier<Stream<? extends Holder<Item>>> knownItems) {
        this.knownItems = knownItems;
    }

    @Override
    public void accept(IPartItem<?> part, PartModel.Unbaked model) {
        this.register(part, new ClientPart(model, ClientPart.Properties.DEFAULT));
    }

    @Override
    public void accept(IPartItem<?> part, PartModel.Unbaked model, ClientPart.Properties properties) {
        this.register(part, new ClientPart(model, properties));
    }

    @Override
    public void copy(IPartItem<?> from, IPartItem<?> to) {
        this.copies.put(to, from);
    }

    public void register(IPartItem<?> part, ClientPart clientPart) {
        var existingPart = this.parts.put(part, clientPart);
        if (existingPart != null) {
            throw new IllegalStateException("Duplicate part model definition for " + IPartItem.getId(part));
        }
    }

    public void finalizeAndValidate() {
        this.copies.forEach((to, from) -> {
            var clientPart = this.parts.get(from);
            if (clientPart == null) {
                throw new IllegalStateException("Missing donor: " + from + " -> " + to);
            } else {
                this.register(to, clientPart);
            }
        });

        var missingPartModels = knownItems.get()
                .filter(holder -> holder.value() instanceof IPartItem<?> partItem && !isCable(partItem)
                        && !this.parts.containsKey(partItem))
                .map(holder -> holder.unwrapKey().orElseThrow().identifier())
                .toList();
        if (!missingPartModels.isEmpty()) {
            throw new IllegalStateException("Missing part model definitions for: " + missingPartModels);
        }
    }

    private boolean isCable(IPartItem<?> partItem) {
        return ICablePart.class.isAssignableFrom(partItem.getPartClass());
    }

    public CompletableFuture<?> save(CachedOutput output, PackOutput.PathProvider pathProvider) {
        return DataProvider.saveAll(
                output, ClientPart.CODEC,
                partItem -> pathProvider.json(BuiltInRegistries.ITEM.getKey(partItem.asItem())), this.parts);
    }
}
//#endif

//#if FABRIC
/**
 * Fabric flavour of AE2's model datagen.
 * <p>
 * Vanilla's {@link net.minecraft.client.data.models.ModelProvider} is a self-contained provider without the
 * {@code registerModels} hook and the {@code getKnownItems} helper NeoForge patches into it, so this provider drives
 * {@link BlockModelGenerators}/{@link ItemModelGenerators} itself and saves the three vanilla json kinds (blockstates,
 * models, item definitions) plus AE2's own part models.
 * <p>
 * NeoForge-only pieces that have no vanilla equivalent:
 * <ul>
 * <li>custom unbaked blockstate models - a vanilla blockstate file can only reference a model file, so those blocks
 * are replaced by {@link ModelSubProvider#STUB_MODEL} and listed in {@link ModelSubProvider#STUBBED_MODELS};</li>
 * <li>the mod-id scoped model provider (vanilla derives the pack paths from the model id instead).</li>
 * </ul>
 */
public final class AE2ModelProvider implements IAE2DataProvider {
    @FunctionalInterface
    public interface ModelSubProviderFactory {
        ModelSubProvider create(BlockModelGenerators blockModels, ItemModelGenerators itemModels,
                PartModelOutput partModels);
    }

    private final PackOutput.PathProvider blockStatePath;
    private final PackOutput.PathProvider itemInfoPath;
    private final PackOutput.PathProvider modelPath;
    private final PackOutput.PathProvider partModelOutput;
    private final List<ModelSubProviderFactory> subProviders;
    @Nullable
    private AE2PartModelCollector partModelCollector;

    public static Factory<DataProvider> create(String modId, ModelSubProviderFactory... subProviders) {
        var subProviderList = List.of(subProviders);
        return output -> new AE2ModelProvider(output, modId, subProviderList);
    }

    public AE2ModelProvider(PackOutput packOutput, String modid, List<ModelSubProviderFactory> subProviders) {
        // Vanilla has no mod-id parameter for model providers; pack paths are derived from the model id.
        this.subProviders = subProviders;
        this.blockStatePath = packOutput.createPathProvider(PackOutput.Target.RESOURCE_PACK, "blockstates");
        this.itemInfoPath = packOutput.createPathProvider(PackOutput.Target.RESOURCE_PACK, "items");
        this.modelPath = packOutput.createPathProvider(PackOutput.Target.RESOURCE_PACK, "models");
        this.partModelOutput = packOutput.createPathProvider(PackOutput.Target.RESOURCE_PACK, "ae2/parts");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        var itemInfoCollector = new ItemInfoCollector();
        var blockStateCollector = new BlockStateCollector();
        var modelCollector = new ModelCollector();
        var blockModels = new BlockModelGenerators(blockStateCollector, itemInfoCollector, modelCollector);
        var itemModels = new ItemModelGenerators(itemInfoCollector, modelCollector);

        var partModelCollector = new AE2PartModelCollector(BuiltInRegistries.ITEM::listElements);
        this.partModelCollector = partModelCollector;
        try {
            // Unlike vanilla's ModelProvider, only AE2's own models are generated, not the vanilla ones.
            for (var subProvider : subProviders) {
                subProvider.create(blockModels, itemModels, partModelCollector).register();
            }
        } finally {
            this.partModelCollector = null;
        }
        partModelCollector.finalizeAndValidate();
        itemInfoCollector.finalizeAndValidate();

        // Blocks whose model is a custom unbaked blockstate model can only get a placeholder on Fabric.
        if (!ModelSubProvider.STUBBED_MODELS.isEmpty()) {
            ModelTemplates.CUBE_ALL.create(
                    ModelSubProvider.STUB_MODEL,
                    TextureMapping.cube(new Material(AppEng.makeId("block/generics/bottom"))),
                    modelCollector);
            LOGGER.warn("Replaced {} custom blockstate models with {} for Fabric: {}",
                    ModelSubProvider.STUBBED_MODELS.size(), ModelSubProvider.STUB_MODEL,
                    ModelSubProvider.STUBBED_MODELS);
        }
        if (!ModelSubProvider.STUBBED_ITEM_MODELS.isEmpty()) {
            LOGGER.warn("Dropped {} custom item models for Fabric (empty model emitted instead): {}",
                    ModelSubProvider.STUBBED_ITEM_MODELS.size(), ModelSubProvider.STUBBED_ITEM_MODELS);
        }

        return CompletableFuture.allOf(
                blockStateCollector.save(output, blockStatePath),
                modelCollector.save(output, modelPath),
                itemInfoCollector.save(output, itemInfoPath),
                partModelCollector.save(output, partModelOutput));
    }

    @Override
    public String getName() {
        return "AE2 Model Definitions";
    }

    private static final class BlockStateCollector implements Consumer<BlockModelDefinitionGenerator> {
        private final Map<Block, BlockModelDefinitionGenerator> generators = new LinkedHashMap<>();

        @Override
        public void accept(BlockModelDefinitionGenerator generator) {
            var previous = generators.put(generator.block(), generator);
            if (previous != null) {
                throw new IllegalStateException("Duplicate blockstate definition for " + generator.block());
            }
        }

        CompletableFuture<?> save(CachedOutput output, PackOutput.PathProvider pathProvider) {
            // Vanilla writes the serialized dispatcher, not the generator, so the dispatchers are built here.
            var dispatchers = new LinkedHashMap<Identifier, BlockStateModelDispatcher>();
            generators.forEach((block, generator) -> dispatchers.put(BuiltInRegistries.BLOCK.getKey(block),
                    generator.create()));
            return DataProvider.saveAll(output, BlockStateModelDispatcher.CODEC, pathProvider, dispatchers);
        }
    }

    private static final class ModelCollector implements BiConsumer<Identifier, ModelInstance> {
        private final Map<Identifier, ModelInstance> models = new LinkedHashMap<>();

        @Override
        public void accept(Identifier id, ModelInstance model) {
            var previous = models.put(id, model);
            if (previous != null) {
                throw new IllegalStateException("Duplicate model definition for " + id);
            }
        }

        CompletableFuture<?> save(CachedOutput output, PackOutput.PathProvider pathProvider) {
            return DataProvider.saveAll(output, ModelInstance::get, pathProvider::json, models);
        }
    }

    /**
     * Vanilla's own item collector is package-private, so the two things AE2 needs from it (a model per item and
     * donor copies) are re-implemented here. Missing item models are not validated: only AE2's items are generated.
     */
    private static final class ItemInfoCollector implements ItemModelOutput {
        private final Map<Item, ClientItem> itemInfos = new LinkedHashMap<>();
        private final Map<Item, Item> copies = new LinkedHashMap<>();

        @Override
        public void accept(Item item, ItemModel.Unbaked model, ClientItem.Properties properties) {
            var previous = itemInfos.put(item, new ClientItem(model, properties));
            if (previous != null) {
                throw new IllegalStateException("Duplicate item model definition for " + item);
            }
        }

        @Override
        public void copy(Item donor, Item target) {
            copies.put(target, donor);
        }

        void finalizeAndValidate() {
            copies.forEach((target, donor) -> {
                var clientItem = itemInfos.get(donor);
                if (clientItem == null) {
                    throw new IllegalStateException("Missing item model donor " + donor + " for " + target);
                }
                itemInfos.put(target, clientItem);
            });
        }

        CompletableFuture<?> save(CachedOutput output, PackOutput.PathProvider pathProvider) {
            return DataProvider.saveAll(output, ClientItem.CODEC,
                    item -> pathProvider.json(BuiltInRegistries.ITEM.getKey(item)), itemInfos);
        }
    }
}
//#else
public final class AE2ModelProvider extends ModelProvider implements IAE2DataProvider {
    private final PackOutput.PathProvider partModelOutput;
    @Nullable
    private PartModelCollector partModelCollector;

    public static Factory<DataProvider> create(String modId, ModelSubProviderFactory... subProviders) {
        var subProviderList = List.of(subProviders);
        return output -> new AE2ModelProvider(output, modId, subProviderList);
    }

    // This matches the super-class constructor of ModelSubProvider
    @FunctionalInterface
    public interface ModelSubProviderFactory {
        ModelSubProvider create(BlockModelGenerators blockModels, ItemModelGenerators itemModels,
                PartModelOutput partModels);
    }

    private final List<ModelSubProviderFactory> subProviders;

    public AE2ModelProvider(PackOutput packOutput, String modid, List<ModelSubProviderFactory> subProviders) {
        super(packOutput, modid);
        this.subProviders = subProviders;
        this.partModelOutput = packOutput.createPathProvider(PackOutput.Target.RESOURCE_PACK, "ae2/parts");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        var partModelCollector = new PartModelCollector(this::getKnownItems);
        this.partModelCollector = partModelCollector;
        CompletableFuture<?> future;
        try {
            future = super.run(output);
        } finally {
            this.partModelCollector = null;
        }
        partModelCollector.finalizeAndValidate();
        return CompletableFuture.allOf(future, partModelCollector.save(output, partModelOutput));
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        for (var subProvider : subProviders) {
            subProvider.create(blockModels, itemModels, partModelCollector).register();
        }
    }

    @Override
    public String getName() {
        return super.getName() + " " + getClass().getName();
    }

    public static final class PartModelCollector implements PartModelOutput {
        private final Map<IPartItem<?>, ClientPart> parts = new HashMap<>();
        private final Map<IPartItem<?>, IPartItem<?>> copies = new HashMap<>();
        private final Supplier<Stream<? extends Holder<Item>>> knownItems;

        public PartModelCollector(Supplier<Stream<? extends Holder<Item>>> knownItems) {
            this.knownItems = knownItems;
        }

        @Override
        public void accept(IPartItem<?> part, PartModel.Unbaked model) {
            this.register(part, new ClientPart(model, ClientPart.Properties.DEFAULT));
        }

        @Override
        public void accept(IPartItem<?> part, PartModel.Unbaked model, ClientPart.Properties properties) {
            this.register(part, new ClientPart(model, properties));
        }

        @Override
        public void copy(IPartItem<?> from, IPartItem<?> to) {
            this.copies.put(to, from);
        }

        public void register(IPartItem<?> part, ClientPart clientPart) {
            var existingPart = this.parts.put(part, clientPart);
            if (existingPart != null) {
                throw new IllegalStateException("Duplicate part model definition for " + IPartItem.getId(part));
            }
        }

        public void finalizeAndValidate() {
            this.copies.forEach((to, from) -> {
                var clientPart = this.parts.get(from);
                if (clientPart == null) {
                    throw new IllegalStateException("Missing donor: " + from + " -> " + to);
                } else {
                    this.register(to, clientPart);
                }
            });

            var missingPartModels = knownItems.get()
                    .filter(holder -> holder.value() instanceof IPartItem<?> partItem && !isCable(partItem)
                            && !this.parts.containsKey(partItem))
                    .map(holder -> holder.unwrapKey().orElseThrow().identifier())
                    .toList();
            if (!missingPartModels.isEmpty()) {
                throw new IllegalStateException("Missing part model definitions for: " + missingPartModels);
            }
        }

        private boolean isCable(IPartItem<?> partItem) {
            return ICablePart.class.isAssignableFrom(partItem.getPartClass());
        }

        public CompletableFuture<?> save(CachedOutput output, PackOutput.PathProvider pathProvider) {
            return DataProvider.saveAll(
                    output, ClientPart.CODEC,
                    partItem -> pathProvider.json(BuiltInRegistries.ITEM.getKey(partItem.asItem())), this.parts);
        }
    }
}
//#endif

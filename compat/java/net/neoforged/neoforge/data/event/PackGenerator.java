/*
 * Vendored from NeoForge (LGPL-2.1-only), adapted for the Fabric shim.
 * Upstream: net/neoforged/neoforge/data/event/PackGenerator.java
 *
 * SHIM: upstream wraps `DataGenerator#getPackGenerator(boolean, String, PackOutput)`, which does not exist in
 * vanilla MC 26.3; the shim falls back to the vanilla pack generator. Provider prefixes are therefore ignored.
 */

package net.neoforged.neoforge.data.event;

import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class PackGenerator {
    private final GatherDataEvent owner;
    private final PackOutput output;
    private final DataGenerator.PackGenerator internalGenerator;

    PackGenerator(GatherDataEvent owner, PackOutput output, String providerPrefix) {
        this.owner = owner;
        this.output = output;
        // SHIM: providerPrefix is dropped, vanilla has no per-provider pack roots.
        this.internalGenerator = owner.getGenerator().getVanillaPack(true);
    }

    public PackOutput getPackOutput() {
        return output;
    }

    public CompletableFuture<HolderLookup.Provider> getWorldLookupProvider() {
        return owner.getWorldLookupProvider();
    }

    public CompletableFuture<HolderLookup.Provider> getReloadableLookupProvider() {
        return owner.getReloadableLookupProvider();
    }

    public <T extends DataProvider> T addProvider(T provider) {
        return createProvider(_ -> provider);
    }

    public <T extends DataProvider> T createProvider(DataProviderFromOutput<T> builder) {
        return this.internalGenerator.addProvider(builder::create);
    }

    public <T extends DataProvider> T createProvider(DataProviderFromOutputLookup<T> builder) {
        return createProvider(output -> builder.create(output, this.getReloadableLookupProvider()));
    }

    public void createBlockAndItemTags(DataProviderFromOutputLookup<TagsProvider<Block>> blockTagsProvider,
            ItemTagsProvider itemTagsProvider) {
        var blockTags = createProvider(blockTagsProvider);
        createProvider(output -> itemTagsProvider.create(output, this.getReloadableLookupProvider(),
                blockTags.contentsGetter()));
    }

    @FunctionalInterface
    public interface DataProviderFromOutput<T extends DataProvider> {
        T create(PackOutput output);
    }

    @FunctionalInterface
    public interface DataProviderFromOutputLookup<T extends DataProvider> {
        T create(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider);
    }

    @FunctionalInterface
    public interface ItemTagsProvider {
        TagsProvider<Item> create(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
                CompletableFuture<TagsProvider.TagLookup<Block>> contentsGetter);
    }
}

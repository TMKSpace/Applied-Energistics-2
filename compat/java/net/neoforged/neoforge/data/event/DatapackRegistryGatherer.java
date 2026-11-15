/*
 * Vendored from NeoForge (LGPL-2.1-only), adapted for the Fabric shim.
 * Upstream: net/neoforged/neoforge/data/event/DatapackRegistryGatherer.java
 *
 * The `recipe` convenience method of upstream is omitted: it relies on RecipeProvider#asBootstrap, which only
 * exists in the NeoForge-patched RecipeProvider.
 */

package net.neoforged.neoforge.data.event;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.MultiRegistryBootstrap;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.SingleRegistryBootstrap;
import net.minecraft.data.advancements.AdvancementProvider;
import net.minecraft.data.advancements.AdvancementSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.context.ContextKeySet;
import net.minecraft.world.item.crafting.Recipe;

/**
 * Gathers the datapack registry entries during {@link GatherDataRegistryEntriesEvent}.
 */
public interface DatapackRegistryGatherer {
    /**
     * Adds a bootstrap for the provided registry {@link ResourceKey} to register entries for use during
     * data generation.
     *
     * @throws IllegalArgumentException If the {@code registryKey} does not belong to a datapack registry.
     */
    <T> DatapackRegistryGatherer add(ResourceKey<? extends Registry<T>> registryKey, SingleRegistryBootstrap<T> bootstrap) throws IllegalArgumentException;

    /**
     * Adds a bootstrap for the requested registries to register entries for use during data generation.
     */
    DatapackRegistryGatherer add(MultiRegistryBootstrap bootstrap) throws IllegalArgumentException;

    /**
     * Adds the sub providers used to bootstrap the {@link Registries#LOOT_TABLE} entries.
     */
    default DatapackRegistryGatherer lootTable(LootTableProvider.SubProviderEntry subProvider, LootTableProvider.SubProviderEntry... subProviders) {
        this.add(Registries.LOOT_TABLE, new LootTableProvider(java.util.Set.of(), createList(subProvider, subProviders)));
        return this;
    }

    /**
     * Adds the sub providers used to bootstrap the {@link Registries#ADVANCEMENT} entries.
     */
    default DatapackRegistryGatherer advancement(AdvancementSubProvider.Factory subProvider, AdvancementSubProvider.Factory... subProviders) {
        this.add(Registries.ADVANCEMENT, new AdvancementProvider(createList(subProvider, subProviders)));
        return this;
    }

    /**
     * @deprecated present only for API parity with upstream, whose {@code RecipeProvider#asBootstrap} does not exist
     *             in vanilla MC 26.3. Use {@link #add(MultiRegistryBootstrap)} with a custom bootstrap instead.
     */
    @Deprecated
    default DatapackRegistryGatherer recipe(java.util.function.BiFunction<BootstrapContext<Recipe<?>>, BootstrapContext<Advancement>, ?> providerFactory) {
        throw new UnsupportedOperationException("RecipeProvider#asBootstrap is a NeoForge patch");
    }

    /**
     * Internal helper for turning a single element plus varargs into a list.
     */
    private static <T> List<T> createList(T first, T... elements) {
        return Stream.concat(Stream.of(first), Arrays.stream(elements)).toList();
    }
}

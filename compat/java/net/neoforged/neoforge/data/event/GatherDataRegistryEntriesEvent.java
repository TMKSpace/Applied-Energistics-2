/*
 * Vendored from NeoForge (LGPL-2.1-only), adapted for the Fabric shim.
 * Upstream: net/neoforged/neoforge/data/event/GatherDataRegistryEntriesEvent.java
 *
 * SHIM: never posted. Entries are collected into a plain RegistrySetBuilder so that whoever drives the shim's
 * datagen (Fabric's own entrypoint, if it is ever wired up) can build a registry set from them.
 */

package net.neoforged.neoforge.data.event;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.MultiRegistryBootstrap;
import net.minecraft.core.registries.SingleRegistryBootstrap;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.IModBusEvent;

/**
 * Gathers the datapack registry entries used for data generation.
 */
public final class GatherDataRegistryEntriesEvent extends Event implements IModBusEvent, GlobalDatapackRegistryGatherer {
    private final ModContainer modContainer;
    private final GlobalDatapackRegistryGatherer delegate;

    public GatherDataRegistryEntriesEvent(ModContainer modContainer, GlobalDatapackRegistryGatherer delegate) {
        this.modContainer = modContainer;
        this.delegate = delegate;
    }

    /**
     * {@return the container of the mod}
     */
    public ModContainer getModContainer() {
        return this.modContainer;
    }

    @Override
    public DatapackRegistryGatherer gatherFor(String modId, String... modIds) {
        return this.delegate.gatherFor(modId, modIds);
    }

    @Override
    public <T> DatapackRegistryGatherer add(ResourceKey<? extends Registry<T>> registryKey,
            SingleRegistryBootstrap<T> bootstrap) throws IllegalArgumentException {
        return this.delegate.add(registryKey, bootstrap);
    }

    @Override
    public DatapackRegistryGatherer add(MultiRegistryBootstrap bootstrap) throws IllegalArgumentException {
        return this.delegate.add(bootstrap);
    }

    /**
     * {@return a gatherer implementation that simply records every bootstrap into a {@link RegistrySetBuilder}}
     */
    public static GlobalDatapackRegistryGatherer newCollector(RegistrySetBuilder builder) {
        return new Collector(builder);
    }

    private static final class Collector implements GlobalDatapackRegistryGatherer {
        private final RegistrySetBuilder builder;

        private Collector(RegistrySetBuilder builder) {
            this.builder = builder;
        }

        @Override
        public <T> DatapackRegistryGatherer add(ResourceKey<? extends Registry<T>> registryKey,
                SingleRegistryBootstrap<T> bootstrap) {
            builder.add(registryKey, bootstrap);
            return this;
        }

        @Override
        public DatapackRegistryGatherer add(MultiRegistryBootstrap bootstrap) {
            builder.add(bootstrap);
            return this;
        }

        @Override
        public DatapackRegistryGatherer gatherFor(String modId, String... modIds) {
            // SHIM: all entries share one registry set, the mod id split is not modelled.
            return this;
        }
    }
}

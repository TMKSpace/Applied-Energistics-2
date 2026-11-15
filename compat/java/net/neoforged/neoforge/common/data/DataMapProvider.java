/*
 * Vendored from NeoForge (LGPL-2.1-only), adapted for the Fabric shim.
 * Upstream: net/neoforged/neoforge/common/data/DataMapProvider.java
 *
 * SHIM: NeoForge data maps are driven by the `neoforge:data_maps` registry layer, which does not exist on Fabric.
 * The shim keeps the provider surface that mods use (constructor, `#gather`, `#builder`) but the builder is
 * untyped and nothing is written to disk, because there is no data map loader to consume the files. The upstream
 * `DataMapType`/`DataMapFile` types live in `net.neoforged.neoforge.registries.datamaps`, which is outside the
 * scope of this shim package.
 */

package net.neoforged.neoforge.common.data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

/**
 * A provider for data map generation.
 */
public abstract class DataMapProvider implements DataProvider {
    protected final CompletableFuture<HolderLookup.Provider> lookupProvider;
    protected final PackOutput.PathProvider pathProvider;
    private final Map<Object, Builder<?>> builders = new LinkedHashMap<>();

    /**
     * Create a new provider.
     *
     * @param packOutput     the output location
     * @param lookupProvider a {@linkplain CompletableFuture} supplying the registries
     */
    protected DataMapProvider(PackOutput packOutput, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        this.lookupProvider = lookupProvider;
        this.pathProvider = packOutput.createPathProvider(PackOutput.Target.DATA_PACK, "data_maps");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        return lookupProvider.thenCompose(provider -> {
            gather(provider);
            // SHIM: no data map loader exists on Fabric, so collected entries are dropped.
            return CompletableFuture.completedFuture(null);
        });
    }

    /**
     * Generate data map entries.
     */
    protected abstract void gather(HolderLookup.Provider provider);

    /**
     * {@return the builder for the given data map type}
     *
     * <p>Shim note: the parameter is untyped, since {@code DataMapType} is not part of this shim package.
     */
    @SuppressWarnings("unchecked")
    public <D> Builder<D> builder(D type) {
        return (Builder<D>) builders.computeIfAbsent(type, k -> new Builder<>());
    }

    @Override
    public String getName() {
        return "Data Maps";
    }

    public static class Builder<D> {
        private final Map<String, Object> values = new LinkedHashMap<>();

        public Builder<D> add(ResourceKey<?> key, Object value, boolean replace) {
            // MC 26.3 renamed ResourceKey#location to #identifier
            values.put(key.identifier().toString(), value);
            return this;
        }

        public Builder<D> add(Identifier id, Object value, boolean replace) {
            values.put(id.toString(), value);
            return this;
        }

        public Builder<D> add(Holder<?> object, Object value, boolean replace) {
            values.put(object.unwrapKey().map(k -> k.identifier().toString()).orElse(""), value);
            return this;
        }

        public Builder<D> add(TagKey<?> tag, Object value, boolean replace) {
            values.put("#" + tag.location(), value);
            return this;
        }

        public Builder<D> remove(Identifier id) {
            values.remove(id.toString());
            return this;
        }

        public Builder<D> remove(TagKey<?> tag) {
            values.remove("#" + tag.location());
            return this;
        }

        /**
         * {@return the raw entries recorded by this builder}
         */
        public Map<String, Object> build() {
            return Map.copyOf(values);
        }
    }
}

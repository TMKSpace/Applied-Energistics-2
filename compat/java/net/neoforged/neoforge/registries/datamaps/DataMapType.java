/*
 * Vendored from NeoForge (LGPL-2.1-only), adapted for the Fabric shim.
 * Upstream: net/neoforged/neoforge/registries/datamaps/DataMapType.java
 *
 * SHIM: this is only the *descriptor* of a data map (id, target registry, value codec, sync flags).
 * The loading side of NeoForge data maps - `DataMapFile`, `AdvancedDataMapType`, `DataMapValueMerger`,
 * `RegisterDataMapTypesEvent`, `IRegistryExtension#getDataMap` - does not exist here, because it is
 * driven by the `neoforge:data_maps` registry layer and by NeoForge's reload listeners.
 * `net.neoforged.neoforge.common.data.DataMapProvider` keeps its own untyped builder, so nothing is
 * actually written to disk; the type is used purely to name the map that a value belongs to.
 */

package net.neoforged.neoforge.registries.datamaps;

import com.google.common.base.Preconditions;
import com.mojang.serialization.Codec;
import java.util.Objects;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

/**
 * Describes a registry data map: a data-driven object that can be attached to entries of a registry.
 *
 * @param <R> the registry the data is for
 * @param <T> the type of the data
 */
public class DataMapType<R, T> {
    private final ResourceKey<Registry<R>> registryKey;
    private final Identifier id;
    private final Codec<T> codec;
    private final @Nullable Codec<T> networkCodec;
    private final boolean mandatorySync;

    protected DataMapType(ResourceKey<Registry<R>> registryKey, Identifier id, Codec<T> codec,
            @Nullable Codec<T> networkCodec, boolean mandatorySync) {
        Preconditions.checkArgument(networkCodec != null || !mandatorySync,
                "Mandatory sync cannot be enabled when the attachment isn't synchronized");

        this.registryKey = Objects.requireNonNull(registryKey, "registryKey must not be null");
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.codec = Objects.requireNonNull(codec, "codec must not be null");
        this.networkCodec = networkCodec;
        this.mandatorySync = mandatorySync;
    }

    /**
     * {@return a data map type builder}
     *
     * @param id       the ID of the data map
     * @param registry the key of the registry the data map is for
     * @param codec    the codec used to deserialize the values from JSON
     */
    public static <T, R> Builder<T, R> builder(Identifier id, ResourceKey<Registry<R>> registry, Codec<T> codec) {
        return new Builder<>(registry, id, codec);
    }

    /**
     * {@return the key of the registry this data map is for}
     */
    public ResourceKey<Registry<R>> registryKey() {
        return registryKey;
    }

    /**
     * {@return the ID of this data map}
     */
    public Identifier id() {
        return id;
    }

    /**
     * {@return the codec used to decode values}
     */
    public Codec<T> codec() {
        return codec;
    }

    /**
     * {@return the codec used to sync values, or {@code null} if this map is not synced}
     */
    public @Nullable Codec<T> networkCodec() {
        return networkCodec;
    }

    /**
     * {@return {@code true} if this data map must be present on the client}
     */
    public boolean mandatorySync() {
        return mandatorySync;
    }

    /**
     * A builder for {@link DataMapType data map types}.
     */
    public static class Builder<T, R> {
        protected final ResourceKey<Registry<R>> registryKey;
        protected final Identifier id;
        protected final Codec<T> codec;

        protected @Nullable Codec<T> networkCodec;
        protected boolean mandatorySync;

        protected Builder(ResourceKey<Registry<R>> registryKey, Identifier id, Codec<T> codec) {
            this.registryKey = registryKey;
            this.id = id;
            this.codec = codec;
        }

        /**
         * Marks the data map as synced.
         *
         * @param networkCodec a codec used to sync the values
         * @param mandatory    if {@code true}, clients that do not support this data map will not be able to connect
         */
        public Builder<T, R> synced(Codec<T> networkCodec, boolean mandatory) {
            this.mandatorySync = mandatory;
            this.networkCodec = networkCodec;
            return this;
        }

        /**
         * {@return a built data map type}
         */
        public DataMapType<R, T> build() {
            return new DataMapType<>(registryKey, id, codec, networkCodec, mandatorySync);
        }
    }
}

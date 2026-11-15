/*
 * Vendored from NeoForge (LGPL-2.1-only), adapted for the Fabric shim.
 * Upstream: net/neoforged/neoforge/data/event/DatapackRegistrySets.java
 *
 * SHIM: upstream distinguishes several registry layers built with RegistryPatchGenerator. The shim only keeps the
 * two lookup providers that mods can actually ask for.
 */

package net.neoforged.neoforge.data.event;

import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;

/**
 * Holds the registry lookups that are available during data generation.
 */
public final class DatapackRegistrySets {
    private final CompletableFuture<HolderLookup.Provider> worldAndDimension;
    private final CompletableFuture<HolderLookup.Provider> reloadable;

    public DatapackRegistrySets(CompletableFuture<HolderLookup.Provider> worldAndDimension,
            CompletableFuture<HolderLookup.Provider> reloadable) {
        this.worldAndDimension = worldAndDimension;
        this.reloadable = reloadable;
    }

    public CompletableFuture<HolderLookup.Provider> worldAndDimension() {
        return worldAndDimension;
    }

    public CompletableFuture<HolderLookup.Provider> reloadable() {
        return reloadable;
    }
}

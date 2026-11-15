/*
 * Vendored from NeoForge (LGPL-2.1-only), adapted for the Fabric shim.
 * Upstream: net/neoforged/neoforge/common/data/BlockTagsProvider.java
 *
 * SHIM: NeoForge's version extends the patched vanilla TagsProvider, whose constructor takes a mod id. Vanilla
 * MC 26.3 derives the pack directory from the registry key instead, so the mod id is accepted and ignored.
 */

package net.neoforged.neoforge.common.data;

import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.world.level.block.Block;

public abstract class BlockTagsProvider extends TagsProvider<Block> {
    public BlockTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, String modId) {
        super(output, Registries.BLOCK, lookupProvider);
    }
}

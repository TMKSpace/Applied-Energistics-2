/*
 * Vendored from NeoForge (LGPL-2.1-only), adapted for the Fabric shim.
 * Upstream: net/neoforged/neoforge/common/data/BlockTagCopyingItemTagProvider.java
 *
 * SHIM: the NeoForge constructor takes a mod id (which vanilla's patched TagsProvider used for the pack directory)
 * and supports propagating tag removals. Vanilla MC 26.3 has neither, so the mod id is accepted and ignored and
 * removals are skipped.
 */

package net.neoforged.neoforge.common.data;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.tags.BlockItemTagId;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * This item tag provider waits for block tags to be available and allows to declaratively copy
 * block tags over to item tags using the {@link #copy(TagKey, TagKey)} method.
 */
public abstract class BlockTagCopyingItemTagProvider extends TagsProvider<Item> {
    private final CompletableFuture<TagsProvider.TagLookup<Block>> blockTags;
    private final Map<TagKey<Block>, TagKey<Item>> tagsToCopy = new HashMap<>();

    public BlockTagCopyingItemTagProvider(PackOutput output,
            CompletableFuture<HolderLookup.Provider> lookupProvider,
            CompletableFuture<TagLookup<Block>> blockTags,
            String modId) {
        // SHIM: vanilla TagsProvider derives the pack directory from the registry key, so modId is unused.
        super(output, Registries.ITEM, lookupProvider);
        this.blockTags = blockTags;
    }

    protected void copy(BlockItemTagId tag) {
        copy(tag.block(), tag.item());
    }

    protected void copy(TagKey<Block> blockTag, TagKey<Item> itemTag) {
        this.tagsToCopy.put(blockTag, itemTag);
    }

    @Override
    protected CompletableFuture<HolderLookup.Provider> createContentsProvider() {
        return super.createContentsProvider().thenCombine(this.blockTags, (provider, blockTags) -> {
            this.tagsToCopy.forEach((fromBlockTag, toItemTag) -> {
                var toBuilder = this.getOrCreateRawBuilder(toItemTag);
                var fromBuilder = blockTags.apply(fromBlockTag)
                        .orElseThrow(() -> new IllegalStateException("Missing block tag " + fromBlockTag.location()));
                // SHIM: vanilla TagBuilder has no removal entries, so only additions can be copied.
                fromBuilder.build().forEach(toBuilder::add);
            });
            return provider;
        });
    }
}

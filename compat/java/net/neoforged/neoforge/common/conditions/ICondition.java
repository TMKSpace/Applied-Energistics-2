/*
 * Vendored from NeoForge (LGPL-2.1-only), adapted for the Fabric shim.
 * Upstream: net/neoforged/neoforge/common/conditions/ICondition.java
 *
 * SHIM: upstream's `ICondition.CODEC` / `LIST_CODEC` are backed by
 * `NeoForgeRegistries.CONDITION_SERIALIZERS` and rely on the `ConditionalOps` codec layer, which does not
 * exist on Fabric (it is the entry point of NeoForge's conditional recipe/loot/advancement machinery).
 * Only the evaluation half of the interface is vendored: `#test(IContext)` plus a self-contained
 * `IContext` that resolves tags through a `HolderGetter.Provider` instead of the pending-tag snapshot
 * that upstream's `ConditionContext` carries.
 */

package net.neoforged.neoforge.common.conditions;

import java.util.Collection;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.RegistryAccess;
import net.minecraft.tags.TagKey;

/**
 * A condition that can be evaluated against a {@link IContext}.
 */
public interface ICondition {
    /**
     * {@return whether the condition passes for the given context}
     */
    boolean test(IContext context);

    interface IContext {
        /**
         * A context that knows about no tags at all.
         */
        IContext EMPTY = new IContext() {
            @Override
            public <T> boolean isTagLoaded(TagKey<T> key) {
                return false;
            }
        };

        /**
         * A context in which tag-based conditions are not allowed to be evaluated.
         */
        IContext TAGS_INVALID = new IContext() {
            @Override
            public <T> boolean isTagLoaded(TagKey<T> key) {
                throw new UnsupportedOperationException("Usage of tag-based conditions is not permitted in this context!");
            }

            @Override
            public <T> Collection<Holder<T>> getTag(TagKey<T> key) {
                throw new UnsupportedOperationException("Usage of tag-based conditions is not permitted in this context!");
            }
        };

        /**
         * Returns {@code true} if the requested tag is available.
         * This method does not require that the loaded tag have any elements, only that it be present at all.
         */
        <T> boolean isTagLoaded(TagKey<T> key);

        /**
         * Returns the contents of the requested tag, or an empty collection if the tag is not loaded.
         */
        default <T> Collection<Holder<T>> getTag(TagKey<T> key) {
            var tag = registries().get(key);
            if (tag.isEmpty()) {
                return List.of();
            }
            List<Holder<T>> contents = tag.get().stream().toList();
            return contents;
        }

        /**
         * Provides the registries (and through them, the loaded tags) this context evaluates against.
         */
        default HolderGetter.Provider registries() {
            return RegistryAccess.EMPTY;
        }
    }
}

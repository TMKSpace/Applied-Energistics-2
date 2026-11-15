/*
 * Vendored from NeoForge (LGPL-2.1-only), adapted for the Fabric shim.
 * Upstream: net/neoforged/neoforge/common/conditions/TagEmptyCondition.java
 *
 * SHIM: the `CODEC`/`codec()` half is omitted together with `ICondition.CODEC` (see ICondition).
 * Evaluation is unchanged: the tag counts as empty when the context cannot resolve any contents for it.
 */

package net.neoforged.neoforge.common.conditions;

import net.minecraft.tags.TagKey;

/**
 * Passes when the given tag has no entries.
 *
 * @param tag the tag to inspect
 */
public record TagEmptyCondition<T>(TagKey<T> tag) implements ICondition {
    @Override
    public boolean test(ICondition.IContext context) {
        return context.getTag(this.tag).isEmpty();
    }
}

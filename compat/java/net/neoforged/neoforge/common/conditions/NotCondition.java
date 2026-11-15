/*
 * Vendored from NeoForge (LGPL-2.1-only), adapted for the Fabric shim.
 * Upstream: net/neoforged/neoforge/common/conditions/NotCondition.java
 *
 * SHIM: the `CODEC`/`codec()` half is omitted together with `ICondition.CODEC` (see ICondition).
 */

package net.neoforged.neoforge.common.conditions;

/**
 * Inverts another condition.
 */
public record NotCondition(ICondition value) implements ICondition {
    @Override
    public boolean test(IContext context) {
        return !value.test(context);
    }

    @Override
    public String toString() {
        return "!" + value;
    }
}

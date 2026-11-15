/*
 * Vendored from NeoForge (LGPL-2.1-only), adapted for the Fabric shim.
 * Upstream: net/neoforged/neoforge/client/model/generators/blockstate/CustomBlockStateModelBuilder.java
 *
 * SHIM note: the class is a faithful port, but upstream plugs it into datagen through NeoForge's
 * patched `MultiVariant` (a record whose value set NeoForge widens from `Variant` to
 * `Variant | CustomBlockStateModelBuilder`, plus `MultiVariant#of`). Vanilla MC 26.3 `MultiVariant`
 * only holds a `WeightedList<Variant>`, so a builder cannot be handed to `MultiVariant.of(...)` here.
 * `#toUnbaked()` still works, callers just have to obtain the unbaked model themselves.
 */

package net.neoforged.neoforge.client.model.generators.blockstate;

import net.minecraft.client.data.models.blockstates.MultiPartGenerator;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.renderer.block.dispatch.VariantMutator;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;

/**
 * Builder for {@link CustomUnbakedBlockStateModel}s to allow using them with {@link MultiVariantGenerator} and
 * {@link MultiPartGenerator} by plugging them into the blockstate definition.
 */
public abstract class CustomBlockStateModelBuilder {
    protected CustomBlockStateModelBuilder() {
    }

    /**
     * Apply the provided {@link VariantMutator} to this builder.
     */
    public abstract CustomBlockStateModelBuilder with(VariantMutator variantMutator);

    /**
     * Apply the provided {@link UnbakedMutator} to the model being built by this builder and
     * return a new builder with the mutated state.
     */
    public abstract CustomBlockStateModelBuilder with(UnbakedMutator variantMutator);

    /**
     * Convert this builder to the final {@link CustomUnbakedBlockStateModel} for serialization.
     */
    public abstract CustomUnbakedBlockStateModel toUnbaked();

    public static final class Simple extends CustomBlockStateModelBuilder {
        private final CustomUnbakedBlockStateModel blockStateModel;

        public Simple(CustomUnbakedBlockStateModel blockStateModel) {
            this.blockStateModel = blockStateModel;
        }

        @Override
        public Simple with(VariantMutator variantMutator) {
            return this;
        }

        @Override
        public CustomBlockStateModelBuilder with(UnbakedMutator variantMutator) {
            return new Simple(variantMutator.apply(blockStateModel));
        }

        @Override
        public CustomUnbakedBlockStateModel toUnbaked() {
            return this.blockStateModel;
        }
    }
}

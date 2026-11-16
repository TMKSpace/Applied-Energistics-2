package appeng.client.integrations.jei;

import com.google.common.primitives.Ints;

import org.jetbrains.annotations.Nullable;

//#if FABRIC
import mezz.jei.api.fabric.constants.FabricTypes;
import mezz.jei.api.fabric.ingredients.fluids.IJeiFluidIngredient;
import mezz.jei.api.fabric.ingredients.fluids.JeiFluidIngredient;

import nfcompat.bridge.FluidStacks;
import nfcompat.bridge.FluidUnits;
//#else
import net.neoforged.neoforge.fluids.FluidStack;

import mezz.jei.api.neoforge.NeoForgeTypes;
//#endif

import mezz.jei.api.ingredients.IIngredientType;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import appeng.client.api.integrations.jei.IngredientConverter;

public class FluidIngredientConverter implements IngredientConverter<
//#if FABRIC
        IJeiFluidIngredient
//#else
        FluidStack
//#endif
> {
//#if FABRIC
    @Override
    public IIngredientType<IJeiFluidIngredient> getIngredientType() {
        return FabricTypes.FLUID_STACK;
    }

    @Nullable
    @Override
    public IJeiFluidIngredient getIngredientFromStack(GenericStack stack) {
        if (stack.what() instanceof AEFluidKey fluidKey) {
            var nfAmount = Math.max(1, Ints.saturatedCast(stack.amount()));
            // AE2 and the shim count in NeoForge millibuckets; JEI on Fabric counts in
            // Fabric drops (bucketVolume == 81000).
            return new JeiFluidIngredient(FluidStacks.toVariant(fluidKey.toStack(nfAmount)),
                    FluidUnits.toFabric(nfAmount));
        } else {
            return null;
        }
    }

    @Nullable
    @Override
    public GenericStack getStackFromIngredient(IJeiFluidIngredient ingredient) {
        return GenericStack.fromFluidStack(
                FluidStacks.fromVariant(ingredient.getFluidVariant(), ingredient.getAmount()));
    }
//#else
    @Override
    public IIngredientType<FluidStack> getIngredientType() {
        return NeoForgeTypes.FLUID_STACK;
    }

    @Nullable
    @Override
    public FluidStack getIngredientFromStack(GenericStack stack) {
        if (stack.what() instanceof AEFluidKey fluidKey) {
            return fluidKey.toStack(Math.max(1, Ints.saturatedCast(stack.amount())));
        } else {
            return null;
        }
    }

    @Nullable
    @Override
    public GenericStack getStackFromIngredient(FluidStack ingredient) {
        return GenericStack.fromFluidStack(ingredient);
    }
//#endif
}

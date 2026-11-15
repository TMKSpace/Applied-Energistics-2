/*
 * Часть nfcompat: конвертация FluidStack (NeoForge) <-> FluidVariant (Fabric).
 *
 * Ключевой факт: в Fabric нет отдельного типа «стек жидкости». Нефть, пиво и прочее
 * из Fabric-модов — это обычные Fluid в BuiltInRegistries.FLUID, а FluidVariant
 * это Holder<Fluid> + DataComponentPatch. Наш FluidStack хранит ровно то же,
 * поэтому конвертация тотальная и без потерь (кроме количества, см. FluidUnits).
 */
package nfcompat.bridge;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

public final class FluidStacks {
    private FluidStacks() {
    }

    public static FluidVariant toVariant(FluidStack stack) {
        if (stack == null || stack.isEmpty()) {
            return FluidVariant.blank();
        }
        return FluidVariant.of(stack.getFluid(), stack.getComponentsPatch());
    }

    public static FluidVariant toVariant(FluidResource resource) {
        if (resource == null || resource.isEmpty()) {
            return FluidVariant.blank();
        }
        return FluidVariant.of(resource.getFluid(), resource.getComponentsPatch());
    }

    /**
     * @param fabricAmount количество в единицах Fabric (81000 на ведро)
     */
    public static FluidStack fromVariant(FluidVariant variant, long fabricAmount) {
        if (variant == null || variant.isBlank()) {
            return FluidStack.EMPTY;
        }
        int nfAmount = FluidUnits.toNf(fabricAmount);
        if (nfAmount <= 0) {
            return FluidStack.EMPTY;
        }
        return new FluidStack(variant.getFluid(), nfAmount, variant.getComponentsPatch());
    }
}

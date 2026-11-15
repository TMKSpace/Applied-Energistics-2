/*
 * Часть nfcompat. NeoForge-специфичные типы ингредиентов JEI.
 *
 * JEI для NeoForge объявляет здесь `FLUID_STACK` (ингредиент = neoforge FluidStack),
 * и AE2 это использует. В fabric-сборке JEI такого класса нет, поэтому объявляем свой:
 * тип ингредиента — обёртка над нашим шим-`FluidStack`.
 *
 * Плата: JEI не знает этот тип изнутри (у него нет ни рендера, ни тултипа по умолчанию),
 * поэтому жидкости в JEI будут видны только после того, как мы зарегистрируем для него
 * рендерер и хелперы. Это ровно то, чем занят `TransformCategory`.
 */
package mezz.jei.api.neoforge;

import mezz.jei.api.ingredients.IIngredientType;
import net.neoforged.neoforge.fluids.FluidStack;

public final class NeoForgeTypes {
    public static final IIngredientType<FluidStack> FLUID_STACK = () -> FluidStack.class;

    private NeoForgeTypes() {
    }
}

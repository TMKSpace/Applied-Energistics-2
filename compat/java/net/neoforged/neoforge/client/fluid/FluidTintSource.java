/*
 * Часть nfcompat (GuideME). Стаб неофорджевского `FluidTintSource`.
 *
 * NeoForge позволяет моде задать цвет жидкости для рендера: `FluidModel` получает компоненту
 * `fluidTintSource()`, через которую рисуются спрайты жидкостей. GuideME использует её в
 * `FluidBlitter.create`, когда рисует иконку жидкости на странице рецепта:
 *
 *     NoSuchMethodError: FluidModel.fluidTintSource()Lnet/neoforged/neoforge/client/fluid/FluidTintSource;
 *       at guideme.internal.util.FluidBlitter.create
 *       at guideme.render.RenderContext.renderFluid
 *       at appeng.client.guidebook.RecipeTypeContributions$FluidIcon.render
 *
 * У нас цвет жидкости уже умеет `nfcompat.bridge.FluidTint` (через ванильный `BlockTintSource`
 * у `FluidModel`). Поэтому интерфейс оставлен тонким: реализацию даёт
 * `nfcompat.compat.guideme.GuideMEFluidTintSource`, а вызов `fluidTintSource()` подменяется
 * миксином `nfcompat.mixin.guideme.FluidBlitterMixin`.
 */
package net.neoforged.neoforge.client.fluid;

import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.FluidStack;

public interface FluidTintSource {

    /** {@return packed RGB (0xRRGGBB) или -1, если жидкость не тинтуется} */
    default int color(FluidState state) {
        return -1;
    }

    /**
     * {@return packed RGB для конкретного стека} В NeoForge учитывает компоненты стека
     * (например, цвет воды из биома); у нас это делает `FluidTint.colorOf`.
     */
    int colorAsStack(FluidStack stack);
}

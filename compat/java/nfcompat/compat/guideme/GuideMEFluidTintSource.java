/*
 * Часть nfcompat (GuideME). Цвет жидкости для иконок в гайда.
 *
 * GuideME рисует иконку жидкости на страницах рецептов: `FluidBlitter.create` берёт
 * `FluidModel.fluidTintSource()` (компонента NeoForge) и умножает спрайт на её
 * `colorAsStack(FluidStack)`.
 *
 * У нас вся эта логика уже есть в `nfcompat.bridge.FluidTint` (он работает через ванильный
 * `FluidModel.tintSource()` -> `BlockTintSource`, с учётом биома и фолбэком на
 * `FluidVariantAttributes.getAssociatedColor`). Поэтому адаптер просто переносит вызов туда:
 * модель мы запоминаем в момент подмены `fluidTintSource()` (см. `FluidBlitterMixin`).
 */
package nfcompat.compat.guideme;

import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.fluid.FluidTintSource;
import net.neoforged.neoforge.fluids.FluidStack;

import org.jetbrains.annotations.Nullable;

import nfcompat.bridge.FluidTint;

public final class GuideMEFluidTintSource implements FluidTintSource {

    @Nullable
    private final FluidModel model;

    private GuideMEFluidTintSource(@Nullable FluidModel model) {
        this.model = model;
    }

    public static FluidTintSource of(@Nullable FluidModel model) {
        return new GuideMEFluidTintSource(model);
    }

    @Override
    public int color(FluidState state) {
        // В GuideME этот путь для иконок не используется (там только colorAsStack),
        // поэтому отдаём «без тинта» и не притворяемся, что знаем цвет без стека.
        return FluidTint.NO_TINT;
    }

    @Override
    public int colorAsStack(FluidStack stack) {
        return FluidTint.colorOf(model, stack);
    }
}

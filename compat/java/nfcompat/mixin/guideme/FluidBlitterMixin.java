/*
 * Часть nfcompat (GuideME). `FluidModel.fluidTintSource()` для иконок жидкости.
 *
 * GuideME рисует иконку жидкости на страницах рецептов и берёт цвет через неофорджевскую
 * компоненту `FluidModel.fluidTintSource()`:
 *
 *     NoSuchMethodError: FluidModel.fluidTintSource()Lnet/neoforged/.../FluidTintSource;
 *       at guideme.internal.util.FluidBlitter.create
 *
 * В ванили такой компоненты нет — цвет считается ванильным `BlockTintSource` (см.
 * `FluidModel.tintSource()`), и именно его использует наш `nfcompat.bridge.FluidTint`.
 * Поэтому подменяем оба обращения в `FluidBlitter.create` на адаптер, который помнит модель
 * и делегирует цвет в `FluidTint`.
 */
package nfcompat.mixin.guideme;

import net.minecraft.client.renderer.block.FluidModel;
import net.neoforged.neoforge.client.fluid.FluidTintSource;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import nfcompat.compat.guideme.GuideMEFluidTintSource;

@Mixin(targets = "guideme.internal.util.FluidBlitter")
public class FluidBlitterMixin {

    @Redirect(method = "create", require = 0,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/block/FluidModel;fluidTintSource()"
                            + "Lnet/neoforged/neoforge/client/fluid/FluidTintSource;"))
    private static FluidTintSource nfcompat$fluidTintSource(FluidModel model) {
        return GuideMEFluidTintSource.of(model);
    }
}

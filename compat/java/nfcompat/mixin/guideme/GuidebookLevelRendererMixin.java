/*
 * Часть nfcompat (GuideME). Две неофорджевские точки в отрисовке 3D-сцены гайда.
 *
 * 1. `FluidModel.customRenderer()` — компонента NeoForge (подмена рендера жидкости).
 *    В ванили её нет: отдаём `null` (GuideME в этом случае рисует жидкость обычным путём).
 * 2. `BlockStateModel.hasMaterialFlag(level, pos, state, flag)` — неофорджевская перегрузка
 *    с контекстом. В ванили есть `hasMaterialFlag(int)`, который смотрит только флаги модели.
 *    Для сцены гайда этого достаточно (контекстные флаги NeoForge касаются AO/стекла).
 *
 * Оба вызова сидят в `GuidebookLevelRenderer.renderBlocks`, поэтому оба редиректа — в один миксин.
 */
package nfcompat.mixin.guideme;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.fluid.CustomFluidRenderer;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "guideme.scene.GuidebookLevelRenderer")
public class GuidebookLevelRendererMixin {

    @Redirect(method = "renderBlocks", require = 0,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/block/FluidModel;customRenderer()"
                            + "Lnet/neoforged/neoforge/client/fluid/CustomFluidRenderer;"))
    @Nullable
    private static CustomFluidRenderer nfcompat$customFluidRenderer(FluidModel model) {
        return null;
    }

    @Redirect(method = "renderBlocks", require = 0,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;hasMaterialFlag"
                            + "(Lnet/minecraft/client/renderer/block/BlockAndTintGetter;"
                            + "Lnet/minecraft/core/BlockPos;"
                            + "Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private static boolean nfcompat$hasMaterialFlag(BlockStateModel model, BlockAndTintGetter level,
            BlockPos pos, BlockState state, int flag) {
        return model.hasMaterialFlag(flag);
    }
}

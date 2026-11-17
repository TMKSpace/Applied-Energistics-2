/*
 * Часть nfcompat (GuideME). Стаб неофорджевского `CustomFluidRenderer`.
 *
 * NeoForge позволяет моду подменить рендер жидкости: `FluidModel` получает компоненту
 * `customRenderer()` (интерфейс `CustomFluidRenderer`), и `GuidebookLevelRenderer.renderBlocks`
 * проверяет её перед обычным рендером.
 *
 * В ванили такого механизма нет — поэтому у нас это пустой интерфейс: GuideME зовёт
 * `customRenderer()` через миксин-редирект, который всегда отдаёт `null`, и сцена рисует
 * жидкости ванильным путём. Класс нужен, чтобы дескрипторы в байткоде GuideME линковались
 * (иначе `NoClassDefFoundError` на первой же 3D-сцене с жидкостью).
 */
package net.neoforged.neoforge.client.fluid;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public interface CustomFluidRenderer {
    boolean renderFluid(FluidRenderer renderer, FluidState fluidState, BlockAndTintGetter level,
            BlockPos pos, FluidRenderer.Output output, BlockState blockState);
}

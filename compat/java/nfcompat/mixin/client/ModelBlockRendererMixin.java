/*
 * Часть nfcompat. Позиционный `collectParts` в сборке меша чанка.
 *
 * ПОЧЕМУ ЭТОТ МИКСИН НУЖЕН
 * NeoForge патчит `BlockStateModel`, добавляя 5-аргументный
 * `collectParts(level, pos, state, random, parts)` — без мира и позиции динамические
 * модели (кабель-шина AE2, привод, крафт-куб, пилон, кварцевое стекло) не могут
 * достать свои model data и рисуют пустоту.
 *
 * В непатченной ванили у интерфейса есть только 2-аргументный вариант
 * `collectParts(RandomSource, List)`, и сборка меша зовёт именно его:
 *   SectionCompiler.compile -> ModelBlockRenderer.tesselateBlock(...)  (offset 21)
 * Наш шим в дефолтной реализации 2-аргументного подставляет EMPTY-мир, BlockPos.ZERO
 * и воздух — модель отрабатывает вхолостую (в логе это выглядело как вызовы
 * `collectParts BlockPos{0,0,0} state=Block{minecraft:air}`).
 *
 * Мир, позиция и состояние при этом ЕСТЬ в кадре `tesselateBlock` — поэтому редиректим
 * ровно этот вызов и отдаём их модели, если она умеет позиционный вариант.
 *
 * Другие места, где ваниль зовёт 2-аргументный collectParts (не критично для мира):
 *   - LevelRenderer.submitBlockDestroyAnimation — анимация ломания блока;
 *   - BlockStateModelWrapper.update — одиночный рендер модели блока.
 * Там контекста либо нет, либо он приходит позже; при необходимости — отдельные патчи.
 */
package nfcompat.mixin.client;

import java.util.List;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DynamicBlockStateModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ModelBlockRenderer.class)
public abstract class ModelBlockRendererMixin {

    @Redirect(method = "tesselateBlock", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;collectParts(Lnet/minecraft/util/RandomSource;Ljava/util/List;)V"))
    private void nfcompat$collectParts(
            BlockStateModel model,
            RandomSource random,
            List<BlockStateModelPart> parts,
            // дальше — параметры самого tesselateBlock, они нам и нужны
            BlockQuadOutput output,
            float x, float y, float z,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            BlockStateModel modelWithState,
            long seed) {
        if (model instanceof DynamicBlockStateModel dynamic) {
            dynamic.collectParts(level, pos, state, random, parts);
        } else {
            model.collectParts(random, parts);
        }
    }
}

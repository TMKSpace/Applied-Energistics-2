/*
 * Часть nfcompat. Позиционный `collectParts` для Fabric Indigo.
 *
 * ПОЧЕМУ НЕ ХВАТИЛО ModelBlockRendererMixin
 * В мире блоки собирает не ванильный `ModelBlockRenderer`, а Indigo из Fabric API:
 *   SectionCompiler.compile
 *    -> redirect$...$tesselateBlockProxy          (fabric-renderer-api-v1)
 *    -> AltModelBlockRendererImpl.tesselateBlock  (fabric-renderer-indigo)
 *    -> FabricBlockStateModel.emitQuads(...)      (fabric-renderer-api-v1)
 *    -> BlockStateModel.collectParts(random, parts)   <- ванильный 2-аргументный
 *
 * У `FabricBlockStateModel.emitQuads` мир, позиция и состояние ЕСТЬ в параметрах
 * (emitter, level, pos, state, random, cullTest), но дефолтная реализация их не
 * передаёт дальше — зовёт ванильный вариант, у которого контекста нет вовсе.
 *
 * Поэтому редиректим этот вызов внутри `emitQuads` и отдаём контекст моделям,
 * которые умеют NeoForge-овский позиционный вариант (`DynamicBlockStateModel`):
 * кабель-шина, привод, крафт-куб, пилон, кварцевое стекло, краска, QNB.
 *
 * Остальные модели (ванильные и чужие) идут прежним путём — поведение не меняется.
 */
package nfcompat.mixin.client;

import java.util.List;
import java.util.function.Predicate;

import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.model.FabricBlockStateModel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DynamicBlockStateModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FabricBlockStateModel.class)
public interface FabricBlockStateModelMixin {

    @Redirect(method = "emitQuads", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;collectParts(Lnet/minecraft/util/RandomSource;Ljava/util/List;)V"))
    private void nfcompat$collectParts(
            BlockStateModel model,
            RandomSource random,
            List<BlockStateModelPart> parts,
            // дальше — параметры самого emitQuads: в них и лежит нужный контекст
            QuadEmitter emitter,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            RandomSource emitRandom,
            Predicate<Direction> cullTest) {
        if (model instanceof DynamicBlockStateModel dynamic) {
            dynamic.collectParts(level, pos, state, random, parts);
        } else {
            model.collectParts(random, parts);
        }
    }
}

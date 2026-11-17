/*
 * Часть nfcompat. Неофорджевский `SubmitNodeCollector#submitSpecial` — настоящая отправка.
 *
 * GuideME отдаёт геометрию 3D-сцены гайда фазами NeoForge:
 *     collector.submitSpecial(RenderPhaseKeys.SOLID, node)
 *     collector.submitSpecial(RenderPhaseKeys.TRANSLUCENT_BLOCKS_AND_ITEMS, node)
 * В ванили метода нет, но вся машинерия есть: `SubmitNodeStorage.order(n)` отдаёт
 * `SubmitNodeCollection`, у него есть фазы (`solid`, `translucentCustomGeometry`, ...),
 * и каждая `SimpleFeatureRenderPhase.submit(SubmitNode)` раскладывает узел по его
 * `featureType()`. Дальше штатный `FeatureRenderDispatcher` зовёт нужный `FeatureRenderer`
 * (у GuideME это `SceneMeshFeatureRenderer`, регистрацию см. в `FeatureRenderDispatcherMixin`).
 *
 * ПОЧЕМУ ЧЕРЕЗ `instanceof`. Вызов идёт через интерфейс `SubmitNodeCollector`, а конкретный
 * класс в рендер-контексте — `SubmitNodeStorage` (`PictureInPictureRenderer.submitNodeStorage`).
 * Если однажды придёт другая реализация, мы просто ничего не отправим, а не уроним кадр.
 *
 * ЧЕГО НЕ ДЕЛАЕМ. Не эмулируем порядок фаз NeoForge один в один: `TRANSLUCENT_BLOCKS_AND_ITEMS`
 * кладём в `translucentCustomGeometry`, остальное — в `solid`. Внутри узла GuideME сам раздаёт
 * геометрию по `RenderType`, так что прозрачность (вода/стекло в сцене) рисуется корректно;
 * отличается только порядок относительно других прозрачных элементов кадра.
 */
package nfcompat.mixin.client;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.phase.SimpleFeatureRenderPhase;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.neoforged.neoforge.client.submit.RenderPhaseKey;
import net.neoforged.neoforge.client.submit.RenderPhaseKeys;

import org.spongepowered.asm.mixin.Mixin;

@Mixin(SubmitNodeCollector.class)
public interface SubmitNodeCollectorMixin {

    default void submitSpecial(RenderPhaseKey phase, SubmitNode node) {
        if (!(this instanceof SubmitNodeStorage storage)) {
            return;
        }

        SimpleFeatureRenderPhase target = phase == RenderPhaseKeys.TRANSLUCENT_BLOCKS_AND_ITEMS
                ? storage.order(0).translucentCustomGeometry
                : storage.order(0).solid;
        target.submit(node);
    }
}

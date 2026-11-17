/*
 * Часть nfcompat. Неофорджевский `SubmitNodeCollector#submitSpecial` — пока no-op.
 *
 * GuideME отдаёт геометрию 3D-сцены гайда через фазы NeoForge:
 *     collector.submitSpecial(RenderPhaseKeys.SOLID, node)
 * В ванили 26.3 такого метода нет (есть `order(n)` и `submitCustomGeometry(...)`), поэтому
 * вызов падал бы `NoSuchMethodError` при первой же сцене.
 *
 * Пока метод просто ничего не делает: гайд открывается, страницы рисуются, а 3D-сцена
 * остаётся пустой. Это осознанный шаг — чтобы не городить маппинг фаз на `RenderType`
 * вслепую (см. TODO, P2). Метод добавляется в интерфейс, так что вызов линкуется для любого
 * реализации (`OrderedSubmitNodeCollector`), которую ваниль передаёт в рендер.
 */
package nfcompat.mixin.client;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.neoforged.neoforge.client.submit.RenderPhaseKey;

import org.spongepowered.asm.mixin.Mixin;

@Mixin(SubmitNodeCollector.class)
public interface SubmitNodeCollectorMixin {

    default void submitSpecial(RenderPhaseKey phase, SubmitNode node) {
        // no-op: см. шапку файла
    }
}

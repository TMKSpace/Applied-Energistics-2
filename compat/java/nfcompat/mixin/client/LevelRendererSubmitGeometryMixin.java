/*
 * Часть nfcompat. Постинг `SubmitCustomGeometryEvent` (отправка кастомной геометрии).
 *
 * ЗАЧЕМ. Вторая половина моста подсветки области AE2: `AreaOverlayRenderer.submitCustomGeometry`
 * достаёт области из `LevelRenderState`, строит по ним `submitCustomGeometry(...)` в
 * `SubmitNodeCollector` и держит позу от события. Без постинга подсветки нет.
 *
 * ГДЕ. NeoForge постит событие в `LevelRenderer` между отправкой частиц и отправкой блок-аутлайна
 * и прочих фич. В ванили 26.3 за это отвечает приватный
 * `LevelRenderer#submitFeatures(LevelRenderState, SubmitNodeCollector, boolean)`; нужная точка —
 * сразу после вызова
 * `ParticlesRenderState.submit(Lnet/minecraft/client/renderer/SubmitNodeCollector;
 *                               Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V`
 * (дескриптор снят с байткода, `shift = AFTER`). До этого вызова отправлены сущности, BE,
 * разрушение блоков и transient-блоки, после — блок-аутлайн и гизмо, т.е. ровно «между
 * частицами и непрозрачными сабмитами», как в NeoForge.
 *
 * АРГУМЕНТЫ. AE2 читает `getLevelRenderState()`, `getSubmitNodeCollector()` и `getPoseStack()`:
 *  - `levelRenderState`/коллектор — аргументы метода;
 *  - `poseStack` — свежий пустой `PoseStack`, как в NeoForge (ваниль держит свой локальный
 *    стек для остальных сабмитов, отдавать его нельзя);
 *  - `renderableSections` — **пустой список**: в 26.3 видимые секции
 *    (`LevelRenderer#visibleSections`, `SectionRenderDispatcher.RenderSection`) не реализуют
 *    неофорджевский `IRenderableSection`, а `null` сломал бы любому слушателю `forEach`.
 *    AE2 это поле не читает; если понадобится — обернуть `RenderSection` в адаптер.
 *
 * ХРУПКОСТЬ. Имена `submitFeatures` и `ParticlesRenderState.submit` — ванильные; при обновлении
 * MC точка может переехать. `require = 0` НЕ ставим намеренно: пусть падает при запуске.
 */
package nfcompat.mixin.client;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.common.NeoForge;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererSubmitGeometryMixin {

    @Inject(method = "submitFeatures(Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/client/renderer/SubmitNodeCollector;Z)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/state/level/ParticlesRenderState;submit(Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
                    shift = At.Shift.AFTER))
    private void nfcompat$postSubmitCustomGeometry(LevelRenderState levelRenderState, SubmitNodeCollector collector,
            boolean renderBlockOutline, CallbackInfo ci) {
        NeoForge.EVENT_BUS.post(new SubmitCustomGeometryEvent(
                levelRenderState,
                collector,
                new PoseStack(),
                List.of()));
    }
}

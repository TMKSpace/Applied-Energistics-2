/*
 * Часть nfcompat. Отрисовка кастомной обводки блока (per-part outline).
 *
 * ЗАЧЕМ. Вторая половина моста из `LevelExtractorBlockOutlineMixin`: там AE2 наполняет
 * список `CustomBlockOutlineRenderer` (обводка выделенной части, превью установки детали
 * или фасада), здесь этот список нужно вызвать до ванильной обводки. `true` из любого
 * рендерера подавляет ванильную обводку — именно так AE2 убирает подсветку всего
 * кабельного блока и оставляет только обводку части.
 *
 * ГДЕ. NeoForge вставляет вызов рендереров в начало `LevelRenderer#submitBlockOutline`,
 * до `poseStack.translate(pos - cameraPos)` и до `pushPose`. Поза на входе — камеро-центричная
 * (начало координат в камере), и AE2 сам сдвигает её на `pos - camera.position()`, поэтому
 * миксин стоит в `HEAD` и позу не трогает.
 *
 * ОТМЕНА. Повторяем семантику NeoForge: `cancel |= render(...)` по всем рендерерам,
 * и если хоть один попросил подавить — выходим из метода целиком (`ci.cancel()`),
 * то есть ни ванильной обводки, ни её второго прохода (high contrast) не будет.
 *
 * ХРУПКОСТЬ. Имя и приватность `submitBlockOutline(PoseStack, SubmitNodeCollector, LevelRenderState)`
 * — ванильные; при обновлении MC точка может переехать. `require = 0` НЕ ставим:
 * пусть падает при запуске, а не оставляет фичу тихо мёртвой.
 */
package nfcompat.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;

import nfcompat.client.BlockOutlineRenderers;

@Mixin(LevelRenderer.class)
public class LevelRendererBlockOutlineMixin {

    @Inject(method = "submitBlockOutline(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/LevelRenderState;)V",
            at = @At("HEAD"), cancellable = true)
    private void nfcompat$submitCustomBlockOutline(PoseStack poseStack, SubmitNodeCollector collector,
            LevelRenderState levelRenderState, CallbackInfo ci) {
        var state = levelRenderState.blockOutlineRenderState;
        if (state == null) {
            return;
        }

        var renderers = BlockOutlineRenderers.get(state);
        if (renderers.isEmpty()) {
            return;
        }

        boolean cancel = false;
        for (var renderer : renderers) {
            cancel |= renderer.render(state, collector, poseStack, levelRenderState);
        }
        if (cancel) {
            ci.cancel();
        }
    }
}

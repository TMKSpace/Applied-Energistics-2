/*
 * Часть nfcompat. Постинг `ExtractBlockOutlineRenderStateEvent` (извлечение обводки блока).
 *
 * ЗАЧЕМ. AE2 через это событие подменяет ванильную обводку целого блока на per-part:
 * `appeng.client.hooks.RenderBlockOutlineHook` по `BlockEntity`-хосту находит выделенную
 * часть/фасад и складывает в событие `CustomBlockOutlineRenderer`, а заодно превью
 * установки детали/фасада в руке. Без постинга светится весь кабельный блок целиком,
 * и превью не рисуется (молча, без ошибок).
 *
 * ГДЕ. В 26.3 обводка извлекается в `LevelExtractor#extractBlockOutline(Camera, LevelRenderState)`;
 * NeoForge патчит этот же метод и постит событие внутри ветки
 * `!state.isAir() && worldBorder.isWithinBounds(pos)`, ДО создания `BlockOutlineRenderState`,
 * а его список рендереров кладёт в запись. Мы постим в `TAIL`, то есть когда ваниль уже
 * положила ненулевой state в `levelRenderState.blockOutlineRenderState` — набор ситуаций
 * тот же (нулевой state бывает ровно в тех случаях, когда NeoForge до постинга не доходит).
 *
 * ПОЧЕМУ TAIL, А НЕ ЛОКАЛЬНЫЕ ПЕРЕМЕННЫЕ. Всё, что нужно событию, либо есть в аргументах
 * и полях экстрактора, либо берётся из самого state/`Minecraft.hitResult` повторно тем же
 * способом, что и в ванили:
 *  - `level` — приватное поле экстрактора (шадоу);
 *  - `pos`/`isBlockTranslucent`/`highContrast` — из только что созданного state
 *    (это ровно ванильные `blockHitResult.getBlockPos()`, `hasMaterialFlag(1)` и настройка
 *    доступности), так что `@Local`/захват стека не нужен и миксин не зависит от формы байткода;
 *  - `blockHitResult` — `Minecraft.hitResult` (ваниль читает его же строкой выше);
 *  - `collisionContext` — `CollisionContext.of(camera.entity())`, как в ванили;
 *  - `camera` и `levelRenderState` — аргументы метода.
 *
 * ОТМЕНА. Если слушатель отменил событие, NeoForge не создаёт state вовсе — здесь
 * обнуляем уже созданный: обводки не будет, как и в NeoForge.
 *
 * ЧЕГО НЕ ХВАТАЕТ. NeoForge для флага полупрозрачности зовёт позиционный
 * `BlockStateModel#hasMaterialFlag(level, pos, state, 1)`, у нас в шиме такого метода нет
 * (в `DynamicBlockStateModel` позиционный `materialFlags` для части моделей ещё заглушка,
 * см. TODO 26.1 в `SpatialPylonModel`), поэтому берём ванильный `hasMaterialFlag(1)` —
 * worst-case флаги модели. Для кабель-шины (`CableBusModel`) результат совпадает:
 * она в любом случае возвращает `TRANSLUCENT | ANIMATED`.
 *
 * ХРУПКОСТЬ. Имя `LevelExtractor#extractBlockOutline` и его аргументы — ванильные; при
 * обновлении MC точка может переехать. `require = 0` НЕ ставим: пусть падает при запуске,
 * а не оставляет фичу тихо мёртвой.
 */
package nfcompat.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import net.neoforged.neoforge.common.NeoForge;

import nfcompat.client.BlockOutlineRenderers;

@Mixin(LevelExtractor.class)
public class LevelExtractorBlockOutlineMixin {

    @Shadow
    private ClientLevel level;

    @Inject(method = "extractBlockOutline(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/state/level/LevelRenderState;)V",
            at = @At("TAIL"))
    private void nfcompat$postExtractBlockOutline(Camera camera, LevelRenderState levelRenderState, CallbackInfo ci) {
        var state = levelRenderState.blockOutlineRenderState;
        var hitResult = Minecraft.getInstance().hitResult;
        if (state == null || !(hitResult instanceof BlockHitResult blockHitResult)) {
            // Цели нет (воздух, вне границы мира, промах) — прошлый кадр не должен «прилипнуть».
            BlockOutlineRenderers.clear();
            return;
        }

        var pos = state.pos();
        var blockState = this.level.getBlockState(pos);
        // То же самое ваниль считает прямо перед созданием state (в байткоде — CollisionContext.of(camera.entity())).
        var collisionContext = CollisionContext.of(camera.entity());

        var event = new ExtractBlockOutlineRenderStateEvent(
                (LevelExtractor) (Object) this,
                this.level,
                pos,
                blockState,
                blockHitResult,
                collisionContext,
                state.isTranslucent(),
                state.highContrast(),
                camera,
                levelRenderState);
        NeoForge.EVENT_BUS.post(event);

        if (event.isCanceled()) {
            levelRenderState.blockOutlineRenderState = null;
            BlockOutlineRenderers.clear();
            return;
        }

        BlockOutlineRenderers.set(state, event.getCustomRenderers());
    }
}

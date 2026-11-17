/*
 * Часть nfcompat. Постинг `ExtractLevelRenderStateEvent` (извлечение состояния уровня).
 *
 * ЗАЧЕМ. AE2 рисует подсветку области (spatial area overlay) через пару неофорджевских событий:
 * `appeng.client.areaoverlay.AreaOverlayRenderer.extractRenderState` складывает видимые области
 * в `LevelRenderState` (`nfcompat.client.RenderStateData`, аналог `setRenderData`), а
 * `submitCustomGeometry` достаёт их обратно и отдаёт геометрию в `SubmitNodeCollector`.
 * Без этого события первый шаг не происходит, и подсветки нет вообще (молча, без ошибок).
 *
 * ПОЧЕМУ МИКСИН. В 26.3 извлечение состояния вынесено из `LevelRenderer` в
 * `net.minecraft.client.renderer.extract.LevelExtractor#extract(DeltaTracker, Camera, float)`.
 * NeoForge патчит этот метод и постит событие в конце (`TAIL`), когда все ванильные состояния
 * (сущности, BE, погода, сфера и т.д.) уже извлечены. Делаем то же самое.
 *
 * ЗАПОЛНЕНИЕ АРГУМЕНТОВ. AE2 читает только `getLevel()` и `getRenderState()`, остальное —
 * минимум:
 *  - `levelExtractor` — сам экстрактор (`this`), как в NeoForge;
 *  - `renderState`/`level` — приватные поля экстрактора (шадоу);
 *  - `camera`/`deltaTracker` — аргументы метода;
 *  - `frustum` — `camera.getCullFrustum()`: именно этот фрустум ваниль держит локально и
 *    использует при извлечении (см. байткод `extract`); при включённом `captureFrustum`
 *    ваниль может взять захваченный фрустум, тогда здесь окажется обычный — слушателей AE2
 *    это не задевает, они фрустум не читают;
 *  - `optionsRenderState` — **null**: у `LevelExtractor` нет такого поля (он живёт в
 *    `LevelRenderer`), а тащить его через аксессор ради поля, которое никто не читает,
 *    не стоит. Если появится слушатель, которому оно нужно, — сделать аксессор к
 *    `LevelRenderer#optionsRenderState` и подставить сюда.
 *
 * ХРУПКОСТЬ. Имена `LevelExtractor#extract` и набор аргументов — ванильные; при обновлении MC
 * точка входа может переехать/смениться. Поэтому `require = 0` НЕ ставим: если метод не
 * найдётся, миксин громко упадёт при запуске, а не оставит фичу тихо мёртвой.
 */
package nfcompat.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.common.NeoForge;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
public class LevelExtractorMixin {

    @Shadow
    @Final
    private LevelRenderState levelRenderState;

    @Shadow
    private ClientLevel level;

    @Inject(method = "extract(Lnet/minecraft/client/DeltaTracker;Lnet/minecraft/client/Camera;F)V",
            at = @At("TAIL"))
    private void nfcompat$postExtractLevelRenderState(DeltaTracker deltaTracker, Camera camera, float partialTick,
            CallbackInfo ci) {
        NeoForge.EVENT_BUS.post(new ExtractLevelRenderStateEvent(
                (LevelExtractor) (Object) this,
                levelRenderState,
                null, // optionsRenderState: см. шапку файла
                level,
                camera,
                camera.getCullFrustum(),
                deltaTracker));
    }
}

/*
 * Часть nfcompat. Постинг `RenderLevelStageEvent.AfterWeather` на рендере мира.
 *
 * ЗАЧЕМ. `extendedae_plus` рисует превью структуры в `AfterWeather`
 * (`UltimateSuperAssemblerMatrixPreviewRenderer#onRenderLevelStage`, регистрация через
 * `@EventBusSubscriber`): `event.getLevelRenderer().collectPerFrameGizmos()` + `Gizmos.cuboid`.
 * Событие было объявлено (`net.neoforged.neoforge.client.event.RenderLevelStageEvent`), но никто
 * его не постил, поэтому слушатель молчал.
 *
 * ГДЕ. NeoForge 26.3.x постит `AfterWeather` в `LevelRenderer#executeClassicTransparency`
 * (`patches/net/minecraft/client/renderer/LevelRenderer.java.patch`), сразу после
 * `this.weatherEffectRenderer.render(this.levelRenderState.weatherRenderState, renderPass)`
 * и до `this.worldBorderRenderer.render(...)`. Байткод ванили 26.3 совпадает с патчем:
 *  - `executeClassicTransparency(ChunkSectionsToRender, PreparedFrame, RenderPass)`;
 *  - offset 173 — `invokevirtual WeatherEffectRenderer.render(WeatherRenderState, RenderPass)V`;
 *  - offset 176.. — `worldBorderRenderer.render(...)`.
 * Поэтому точка — `@At(INVOKE, target = WeatherEffectRenderer.render, shift = AFTER)` внутри
 * `executeClassicTransparency`, т.е. ровно «после погоды, до бордера», как в NeoForge.
 *
 * ЧЕГО НЕ ПОСТИМ (осознанно, минимальный объём). `AfterSky` (лямбда в `addSkyPass`),
 * `AfterOpaqueBlocks`/`AfterOpaqueFeatures` (`executeSolid`),
 * `AfterTranslucentFeatures`/`AfterTranslucentBlocks`/`AfterTranslucentParticles`
 * (`executeClassicTransparency`), `OitTranslucent` (цикл в `executeOit`, нужен `@Local stage`)
 * и `AfterLevel` (`GameRenderer#renderLevel`) — живые аддоны их не дёргают, а каждый лишний
 * `@Inject` в горячем пути рендера — лишний риск при обновлении MC. Классы событий при этом
 * объявлены все: аддон, который на них подпишется, получит тишину вместо
 * `NoClassDefFoundError`.
 *
 * АРГУМЕНТЫ СОБЫТИЯ (как в NeoForge):
 *  - `levelRenderer` — `this`;
 *  - `levelRenderState` — приватное поле `LevelRenderer#levelRenderState` (`@Shadow @Final`);
 *  - `poseStack` — `null`: NeoForge на этой стадии тоже передаёт `null`, конструктор события
 *    подменяет его свежим пустым `PoseStack` (локальный стек ванили отдавать нельзя);
 *  - `modelViewMatrix` — `levelRenderState.cameraRenderState.viewRotationMatrix` (публичное поле);
 *  - `renderableSections` — **пустой список**: `LevelRenderer#visibleSections` в 26.3 содержит
 *    `SectionRenderDispatcher$RenderSection`, который не реализует неофорджевский
 *    `IRenderableSection` (та же причина, что в `LevelRendererSubmitGeometryMixin`);
 *  - `renderPass` — аргумент метода, тот самый активный пасс NeoForge (закрывать нельзя).
 *
 * ХРУПКОСТЬ. Держится на имени `executeClassicTransparency` и дескрипторе
 * `WeatherEffectRenderer#render`. При обновлении MC миксин падает на старте
 * (`defaultRequire = 1`), а не теряет событие молча.
 */
package nfcompat.mixin.client;

import java.util.List;

import com.mojang.renderpearl.api.commands.RenderPass;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererRenderStageMixin {

    @Shadow
    @Final
    private LevelRenderState levelRenderState;

    @Inject(method = "executeClassicTransparency",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/WeatherEffectRenderer;render(Lnet/minecraft/client/renderer/state/level/WeatherRenderState;Lcom/mojang/renderpearl/api/commands/RenderPass;)V",
                    shift = At.Shift.AFTER))
    private void nfcompat$postAfterWeather(ChunkSectionsToRender sections,
            FeatureRenderDispatcher.PreparedFrame frame, RenderPass renderPass, CallbackInfo ci) {
        NeoForge.EVENT_BUS.post(new RenderLevelStageEvent.AfterWeather(
                (LevelRenderer) (Object) this,
                levelRenderState,
                null,
                levelRenderState.cameraRenderState.viewRotationMatrix,
                List.of(),
                renderPass));
    }
}

/*
 * Часть nfcompat. Кастомные рендереры окружения (небо, облака, погода).
 *
 * NeoForge патчит LevelRenderer так, чтобы вместо ванильных SkyRenderer/CloudRenderer/
 * WeatherEffectRenderer можно было звать рендерер, привязанный к измерению (атрибуты
 * neoforge:custom_skybox/clouds/weather_effects). У нас этих атрибутов нет, поэтому
 * рендерер ищется по измерению через EnvironmentRenderers (id привязывает сам мод).
 *
 * Контракт тот же, что в NeoForge: метод рендерера возвращает true = ванильный рендер
 * подавляется. Отсюда @Redirect на вызовы ванили — если кастомного рендерера нет,
 * всё работает ровно как в ванили.
 *
 * Точки инжекта (MC 26.3, рендер переехал на render state / frame graph):
 *  - небо:     LevelRenderer#lambda$addSkyPass$0 -> SkyRenderer#render(GpuBufferSlice, SkyRenderState)
 *  - облака:   LevelRenderer#prepareTranslucents -> CloudRenderer#prepare(...)
 *              LevelRenderer#executeClassicTransparency -> CloudRenderer#render(CloudStatus, RenderPass)
 *              LevelRenderer#executeOit -> CloudRenderer#renderOit(...)
 *  - погода:   LevelRenderer#executeClassicTransparency -> WeatherEffectRenderer#render(WeatherRenderState, RenderPass)
 *              LevelRenderer#executeOit -> WeatherEffectRenderer#renderOit(...)
 *
 * Состояние рендера готовить не нужно: рендерерам AE2 хватает LevelRenderState, который ваниль
 * и так заполняет (ExtractLevelRenderStateEvent для них не требуется — своего render data они не просят).
 */
package nfcompat.mixin.client;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuTextureView;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.oit.OitRenderPassProvider;
import net.minecraft.client.renderer.oit.OitStage;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.world.phys.Vec3;

import nfcompat.client.EnvironmentRenderers;

@Mixin(LevelRenderer.class)
public class LevelRendererEnvironmentMixin {

    @Shadow
    @Final
    private LevelRenderState levelRenderState;

    @Redirect(method = "lambda$addSkyPass$0",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/SkyRenderer;render(Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lnet/minecraft/client/renderer/state/level/SkyRenderState;)V"))
    private void nfcompat$renderSky(SkyRenderer vanillaSkyRenderer, GpuBufferSlice skyFog, SkyRenderState skyRenderState) {
        var custom = EnvironmentRenderers.skybox(Minecraft.getInstance().level);
        if (custom != null && custom.renderSky(levelRenderState, skyRenderState,
                levelRenderState.cameraRenderState.viewRotationMatrix, skyFog)) {
            return;
        }
        vanillaSkyRenderer.render(skyFog, skyRenderState);
    }

    @Redirect(method = "prepareTranslucents",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/CloudRenderer;prepare(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V"))
    private void nfcompat$prepareClouds(CloudRenderer vanillaCloudRenderer, int cloudColor, CloudStatus cloudStatus,
            float cloudHeight, int cloudRange, Vec3 camPos, long gameTime, float partialTicks) {
        var custom = EnvironmentRenderers.clouds(Minecraft.getInstance().level);
        if (custom != null) {
            custom.prepare(levelRenderState, camPos, cloudStatus, cloudColor, cloudHeight, cloudRange,
                    levelRenderState.cameraRenderState.viewRotationMatrix);
            return;
        }
        vanillaCloudRenderer.prepare(cloudColor, cloudStatus, cloudHeight, cloudRange, camPos, gameTime, partialTicks);
    }

    @Redirect(method = "executeClassicTransparency",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/CloudRenderer;render(Lnet/minecraft/client/CloudStatus;Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
    private void nfcompat$renderClouds(CloudRenderer vanillaCloudRenderer, CloudStatus cloudStatus,
            RenderPass renderPass) {
        var custom = EnvironmentRenderers.clouds(Minecraft.getInstance().level);
        if (custom != null && custom.renderClouds(levelRenderState, cloudStatus,
                levelRenderState.cameraRenderState.viewRotationMatrix, renderPass)) {
            return;
        }
        vanillaCloudRenderer.render(cloudStatus, renderPass);
    }

    @Redirect(method = "executeOit",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/CloudRenderer;renderOit(Lnet/minecraft/client/CloudStatus;Lnet/minecraft/client/renderer/oit/OitStage;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Lnet/minecraft/client/renderer/oit/OitRenderPassProvider$Parameters;)V"))
    private void nfcompat$renderCloudsOit(CloudRenderer vanillaCloudRenderer, CloudStatus cloudStatus,
            OitStage stage, GpuTextureView mainDepthTextureView, OitRenderPassProvider.Parameters params) {
        var custom = EnvironmentRenderers.clouds(Minecraft.getInstance().level);
        if (custom != null && custom.renderCloudsOit(levelRenderState, cloudStatus,
                levelRenderState.cameraRenderState.viewRotationMatrix, stage, mainDepthTextureView, params)) {
            return;
        }
        vanillaCloudRenderer.renderOit(cloudStatus, stage, mainDepthTextureView, params);
    }

    @Redirect(method = "executeClassicTransparency",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/WeatherEffectRenderer;render(Lnet/minecraft/client/renderer/state/level/WeatherRenderState;Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
    private void nfcompat$renderWeather(WeatherEffectRenderer vanillaWeatherRenderer, WeatherRenderState weatherRenderState,
            RenderPass renderPass) {
        var custom = EnvironmentRenderers.weather(Minecraft.getInstance().level);
        if (custom != null && custom.renderSnowAndRain(levelRenderState, weatherRenderState,
                levelRenderState.cameraRenderState.pos, renderPass)) {
            return;
        }
        vanillaWeatherRenderer.render(weatherRenderState, renderPass);
    }

    @Redirect(method = "executeOit",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/WeatherEffectRenderer;renderOit(Lnet/minecraft/client/renderer/oit/OitStage;Lnet/minecraft/client/renderer/state/level/WeatherRenderState;Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
    private void nfcompat$renderWeatherOit(WeatherEffectRenderer vanillaWeatherRenderer, OitStage stage,
            WeatherRenderState weatherRenderState, RenderPass renderPass) {
        var custom = EnvironmentRenderers.weather(Minecraft.getInstance().level);
        if (custom != null && custom.renderSnowAndRainOit(levelRenderState, weatherRenderState,
                levelRenderState.cameraRenderState.pos, stage, renderPass)) {
            return;
        }
        vanillaWeatherRenderer.renderOit(stage, weatherRenderState, renderPass);
    }
}

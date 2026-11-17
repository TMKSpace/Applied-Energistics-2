/*
 * Часть nfcompat. NeoForge-механизм кастомных feature-renderer'ов.
 *
 * В NeoForge мод может добавить свой `FeatureRenderer` для собственного типа `SubmitNode`
 * (событие `RegisterFeatureRenderersEvent`, оно приходит с готовой `FeatureRendererMap`).
 * GuideME этим пользуется: `SceneMeshFeatureRenderer` рисует 3D-сцену гайда, а его тип
 * (`SceneMeshFeatureRenderer.TYPE`) регистрируется именно так.
 *
 * В ванили такого события нет, но вся машинерия есть: `FeatureRenderDispatcher` в конструкторе
 * создаёт `FeatureRendererMap` и складывает туда штатные рендереры. Поэтому цепляемся в конец
 * конструктора и постим неофорджевское событие с его картой — GuideME добавляет свой рендерер,
 * и дальше `SubmitNode`-ы сцены рисуются штатным `FeatureRenderDispatcher`-ом.
 *
 * Без этого сцены в гайде оставались пустыми: `submitSpecial` складывал узлы в фазу, но
 * рендерера для `SceneMeshFeatureRenderer.TYPE` в карте не было.
 */
package nfcompat.mixin.client;

import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.feature.FeatureRendererMap;
import net.neoforged.neoforge.client.event.RegisterFeatureRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FeatureRenderDispatcher.class)
public class FeatureRenderDispatcherMixin {

    @Shadow
    @Final
    private FeatureRendererMap featureRenderers;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void nfcompat$postRegisterFeatureRenderers(CallbackInfo ci) {
        NeoForge.EVENT_BUS.post(new RegisterFeatureRenderersEvent(this.featureRenderers));
    }
}

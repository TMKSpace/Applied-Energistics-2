/*
 * Часть nfcompat. Хук регистрации слоёв моделей сущностей.
 *
 * NeoForge патчит `LayerDefinitions#createRoots`: перед сборкой карты он
 * докладывает туда определения слоёв из `EntityRenderersEvent.RegisterLayerDefinitions`.
 * У нас событие надо разослать в начале метода (тогда моды успеют сложить свои
 * определения в ClientHooks), а применить результат - в конце, заменив
 * immutable-карту на свою (в ванили она собирается в ImmutableMap).
 */
package nfcompat.mixin.client;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.model.geom.LayerDefinitions;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;

@Mixin(LayerDefinitions.class)
public class LayerDefinitionsMixin {

    @Inject(method = "createRoots", at = @At("HEAD"))
    private static void nfcompat$postRegisterLayerDefinitions(
            CallbackInfoReturnable<Map<ModelLayerLocation, LayerDefinition>> cir) {
        nfcompat.ClientEventBridge.postRegisterLayerDefinitions();
    }

    @Inject(method = "createRoots", at = @At("RETURN"), cancellable = true)
    private static void nfcompat$mergeLayerDefinitions(
            CallbackInfoReturnable<Map<ModelLayerLocation, LayerDefinition>> cir) {
        cir.setReturnValue(nfcompat.ClientEventBridge.withModdedLayerDefinitions(cir.getReturnValue()));
    }
}

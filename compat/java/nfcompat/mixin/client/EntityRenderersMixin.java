/*
 * Часть nfcompat. Хук регистрации рендереров сущностей.
 *
 * NeoForge постит `EntityRenderersEvent.RegisterRenderers` незадолго до создания
 * карт рендереров, а моды в нём зовут `EntityRenderers#register` и
 * `BlockEntityRenderers#register` (оба открыты через accesswidener).
 * Ваниль же собирает эти карты в `createEntityRenderers`, куда мод залезть не
 * может. Поэтому постим событие в начале сборки: обработчик AE2 наполнит обе
 * статические карты провайдеров, и обе сборки увидят его регистрации.
 *
 * Событие постится один раз на игру - повторная регистрация в те же карты
 * бессмысленна.
 */
package nfcompat.mixin.client;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.world.entity.EntityType;

@Mixin(EntityRenderers.class)
public class EntityRenderersMixin {

    @Inject(method = "createEntityRenderers", at = @At("HEAD"))
    private static void nfcompat$postRegisterRenderers(EntityRendererProvider.Context context,
            CallbackInfoReturnable<Map<EntityType<?>, EntityRenderer<?, ?>>> cir) {
        nfcompat.ClientEventBridge.postRegisterRenderers();
    }
}

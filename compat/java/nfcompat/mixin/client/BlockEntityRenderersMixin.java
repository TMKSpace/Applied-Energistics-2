/*
 * Часть nfcompat. Хук регистрации рендереров блок-сущностей.
 *
 * Страховка на случай, если `BlockEntityRenderers#createEntityRenderers`
 * вызовется раньше `EntityRenderers#createEntityRenderers`: тогда событие
 * разошлется отсюда, и AE2 успеет наполнить обе карты до их сборки.
 * Повторный вызов безопасен - `ClientEventBridge` постит событие один раз.
 */
package nfcompat.mixin.client;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.world.level.block.entity.BlockEntityType;

@Mixin(BlockEntityRenderers.class)
public class BlockEntityRenderersMixin {

    @Inject(method = "createEntityRenderers", at = @At("HEAD"))
    private static void nfcompat$postRegisterRenderers(BlockEntityRendererProvider.Context context,
            CallbackInfoReturnable<Map<BlockEntityType<?>, BlockEntityRenderer<?, ?>>> cir) {
        nfcompat.ClientEventBridge.postRegisterRenderers();
    }
}

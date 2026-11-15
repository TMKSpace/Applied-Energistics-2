/*
 * Часть nfcompat. Хук регистрации блочных тинтов.
 *
 * NeoForge патчит `BlockColors#createDefault` и в конце постит
 * `RegisterColorHandlersEvent.BlockTintSources`, а моды в нём зовут
 * `BlockColors#register`. У нас событие есть, значит достаточно разослать его
 * на уже созданном экземпляре - сам обработчик AE2 зарегистрирует тинты через
 * публичный метод.
 */
package nfcompat.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.color.block.BlockColors;

@Mixin(BlockColors.class)
public class BlockColorsMixin {

    @Inject(method = "createDefault", at = @At("RETURN"))
    private static void nfcompat$postBlockTintSources(CallbackInfoReturnable<BlockColors> cir) {
        nfcompat.ClientEventBridge.postBlockTintSources(cir.getReturnValue());
    }
}

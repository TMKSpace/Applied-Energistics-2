/*
 * Часть nfcompat. Точка поста `PlayerInteractEvent.RightClickBlock` на клиенте.
 *
 * В NeoForge событие постится из патченного `MultiPlayerGameMode#useItemOn`. В 26.3 сам
 * `useItemOn` только оборачивает `performUseItemOn` в клиентское предсказание
 * (`startPrediction`) и отправляет пакет с его результатом, поэтому инъекция стоит в HEAD
 * `performUseItemOn`: так локальные изменения блока попадают в предсказание, а
 * `ServerboundUseItemOnPacket` всё равно уходит на сервер (тот сделает то же самое).
 *
 * Нужно `appeng.hooks.WrenchHook` — мгновенная реакция на ключ и корректная анимация.
 *
 * Если событие отменено — возвращаем его `cancellationResult`; иначе PASS и ваниль
 * продолжает штатно.
 */
package nfcompat.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.event.EventHooks;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeUseItemOnMixin {

    @Inject(method = "performUseItemOn", at = @At("HEAD"), cancellable = true)
    private void nfcompat$postRightClickBlock(LocalPlayer player, InteractionHand hand, BlockHitResult hitResult,
            CallbackInfoReturnable<InteractionResult> cir) {
        var result = EventHooks.onRightClickBlock(player, player.level(), hand, hitResult);
        if (result != InteractionResult.PASS) {
            cir.setReturnValue(result);
        }
    }
}

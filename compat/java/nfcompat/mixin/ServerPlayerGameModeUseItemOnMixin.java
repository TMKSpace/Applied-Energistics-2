/*
 * Часть nfcompat. Точка поста `PlayerInteractEvent.RightClickBlock` на сервере.
 *
 * В NeoForge событие постится из патченного `ServerPlayerGameMode#useItemOn` — до того,
 * как ваниль попробует `BlockState#useItemOn` / `ItemStack#useOn`. У нас ваниль не
 * патчена, поэтому ставим инъекцию в HEAD того же метода.
 *
 * Нужно `appeng.hooks.WrenchHook` (гаечные ключи AE2: разбор с шифтом, поворот без
 * шифта) — без этого события слушатель молчал и ключ ничего не делал.
 *
 * Если событие отменено — возвращаем его `cancellationResult` и ваниль до блока/предмета
 * не доходит; если нет — отдаём управление ванили как обычно (PASS).
 */
package nfcompat.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.event.EventHooks;

@Mixin(ServerPlayerGameMode.class)
public class ServerPlayerGameModeUseItemOnMixin {

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void nfcompat$postRightClickBlock(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand,
            BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        var result = EventHooks.onRightClickBlock(player, level, hand, hitResult);
        if (result != InteractionResult.PASS) {
            cir.setReturnValue(result);
        }
    }
}

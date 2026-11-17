/*
 * Часть nfcompat. Точка поста `PlayerEvent.BreakSpeed`.
 *
 * В NeoForge событие постится из патченного `Player#getDestroySpeed(BlockState)` — его
 * результат и есть скорость копания, которую видит `BlockBehaviour#getDestroyProgress`.
 * У нас ваниль не патчена, поэтому единственный способ вернуть AE2 бонус — вернуть
 * значение из конца того же метода.
 *
 * Нужно ровно для `appeng.hooks.SkyStoneBreakSpeed`: скайстоун имеет destroy time 50
 * (как камень для железной кирки), и событие умножает скорость на 10 для инструментов
 * лучше железного. Позиция блока не передаётся (как и в NeoForge, когда она неизвестна).
 */
package nfcompat.mixin.common;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.EventHooks;

@Mixin(Player.class)
public class PlayerDestroySpeedMixin {

    @Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
    private void nfcompat$postBreakSpeed(BlockState state, CallbackInfoReturnable<Float> cir) {
        var player = (Player) (Object) this;
        cir.setReturnValue(EventHooks.getBreakSpeed(player, state, cir.getReturnValue(), null));
    }
}

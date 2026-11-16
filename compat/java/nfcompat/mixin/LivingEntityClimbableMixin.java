/*
 * Часть nfcompat. Лазание по кабелю AE2.
 *
 * В 26.3 у ванильного `LivingEntity#onClimbable()` больше нет хука `isLadder`: она знает
 * только тег `#climbable` и люки. В 1.20.1 это делал `appeng.mixins.DynamicLadderMixin`
 * вместе с `IDynamicLadder` — оба файла при переезде потерялись.
 *
 * Возвращаем поведение коротким путём, без интерфейса: единственный "динамический"
 * лазаемый блок — `CableBusBlock` (цепочка CableBusBlock#isLadder -> CableBusContainer ->
 * IPart.isLadder -> CableAnchorPart). Если под ногами кабель, спрашиваем его напрямую.
 *
 * Спектаторов и флай-скольжение не трогаем: у них свои правила, а ваниль уже отдала
 * корректный ответ.
 */
package nfcompat.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.LivingEntity;

import appeng.block.networking.CableBusBlock;

@Mixin(LivingEntity.class)
public class LivingEntityClimbableMixin {

    @Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true)
    private void nfcompat$cableLadder(CallbackInfoReturnable<Boolean> cir) {
        var self = (LivingEntity) (Object) this;
        if (self.isSpectator() || self.isFallFlying()) {
            return;
        }

        // В 26.3 getFeetBlockState() называется getInBlockState().
        var state = self.getInBlockState();
        if (state.getBlock() instanceof CableBusBlock cableBus) {
            cir.setReturnValue(cableBus.isLadder(state, self.level(), self.blockPosition(), self));
        }
    }
}

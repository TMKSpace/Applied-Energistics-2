/*
 * Часть nfcompat. `LivingEntityUseItemEvent.Finish` — "предмет доиспользован".
 *
 * На NeoForge это патч ванили (`patches/net/minecraft/world/entity/LivingEntity.java.patch`,
 * ветка 26.3.x): внутри `completeUsingItem()` вызов
 *
 *     ItemStack result = this.useItem.finishUsingItem(this.level(), this);
 *
 * заменён на
 *
 *     ItemStack copy = this.useItem.copy();
 *     ItemStack result = EventHooks.onItemUseFinish(this, copy, this.getUseItemRemainingTicks(),
 *                                                   this.useItem.finishUsingItem(this.level(), this));
 *
 * а `EventHooks.onItemUseFinish` = пост `Finish` в шину + `event.getResultStack()`.
 * Повторяем ровно это, включая порядок вычислений: копия стека и остаток тиков
 * снимаются ДО `finishUsingItem` (иначе зелье уже съедено и `getItem()` врал бы).
 *
 * ПОЧЕМУ `@WrapOperation`, А НЕ `@Redirect`. В `completeUsingItem` есть ещё
 * `ItemStack.isEmpty()`, `SetItemInHand` и т.п.; привязка к одному конкретному
 * INVOKE по полному дескриптору (`ItemStack;finishUsingItem(Level, LivingEntity)ItemStack`)
 * плюс `@WrapOperation` даёт ровно одну точку и не требует `@Local`.
 *
 * ГДЕ ЭТО РАБОТАЕТ. Ровно там же, где в ванили: `completeUsingItem` зовётся из
 * `updateUsingItem` (когда досчитался таймер) и из `Player#tick`/эффектов. На клиенте
 * ванильный `completeUsingItem` сам выходит раньше, если предмет уже не используется, —
 * то есть поведение по сторонам совпадает с NeoForge без наших условий.
 *
 * ПРИМЕР ИСПОЛЬЗОВАНИЯ. ae2wtlib: `AE2wtlibForge.handle(LivingEntityUseItemEvent.Finish)`
 * -> `AE2wtlibEvents.restock(player, getItem(), getResultStack(), ...)` (ресток из
 * беспроводного терминала после еды/зелья).
 *
 * ХРУПКОСТЬ. Держится на имени `completeUsingItem` и на дескрипторе
 * `ItemStack.finishUsingItem(Level, LivingEntity)`. Если MC переименует метод или
 * вызов переедет, миксин упадёт громко на старте (`defaultRequire: 1` в
 * `nfcompat.mixins.json`) — это лучше, чем тихо не постить событие. Первое, что
 * надо проверить при обновлении MC: `javap -c net.minecraft.world.entity.LivingEntity`
 * и найти там `finishUsingItem`.
 */
package nfcompat.mixin.common;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public class LivingEntityUseItemMixin {

    @WrapOperation(
            method = "completeUsingItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;finishUsingItem"
                            + "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)"
                            + "Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack nfcompat$postUseItemFinish(ItemStack useItem, Level level, LivingEntity entity,
            Operation<ItemStack> original) {
        // Порядок как в NeoForge: сначала копия "до", потом остаток тиков, потом сам вызов.
        ItemStack before = useItem.copy();
        int remaining = entity.getUseItemRemainingTicks();

        ItemStack result = original.call(useItem, level, entity);

        var event = new LivingEntityUseItemEvent.Finish(entity, before, remaining, result);
        NeoForge.EVENT_BUS.post(event);
        // Слушатель мог подменить результат — ваниль ниже сравнивает его с useItem
        // и решает, класть ли стек в руку.
        return event.getResultStack();
    }
}

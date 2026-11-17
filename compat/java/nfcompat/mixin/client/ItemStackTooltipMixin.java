/*
 * Часть nfcompat. Постинг `ItemTooltipEvent` (тултипы предметов).
 *
 * ЗАЧЕМ. В NeoForge событие летит из `ItemStack#getTooltipLines(TooltipContext, Player, TooltipFlag)`
 * (так написано и в javadoc наших вендоренных классов). На него подписан, в частности, GuideME:
 * хоткей «зажми G у предмета» работает не сам по себе — пока игрок наводит предмет, слушатель
 * собирает из тултипа список страниц гайда (`OpenGuideHotkey.handleTooltip`) и только потом
 * по удержанию клавиши открывает нужную. Без события список пуст, и клавиша «ничего не делает».
 *
 * ГДЕ ЦЕПЛЯЕМСЯ. Ванильный `getTooltipLines` — единственная точка, где собираются строки
 * тултипа; событие постим в конце (RETURN) и передаём тот же мутабельный список: слушатели
 * NeoForge правят его на месте (`event.getToolTip().add/remove`), так что возвращать ничего
 * не нужно. `TooltipDisplay` берём из компонентов стека, иначе дефолтный.
 *
 * Только клиент: метод есть и на сервере, но событие — клиентское (в `nfcompat.mixins.json`
 * миксин лежит в секции "client").
 */
package nfcompat.mixin.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public class ItemStackTooltipMixin {

    @Inject(method = "getTooltipLines", at = @At("RETURN"))
    private void nfcompat$postItemTooltip(Item.TooltipContext context,
            net.minecraft.world.entity.player.Player player,
            TooltipFlag flags,
            CallbackInfoReturnable<List<Component>> cir) {
        var lines = cir.getReturnValue();
        if (lines == null || lines.isEmpty()) {
            return;
        }

        var stack = (ItemStack) (Object) this;
        var event = new ItemTooltipEvent(stack, player, lines, flags, context,
                stack.getOrDefault(net.minecraft.core.component.DataComponents.TOOLTIP_DISPLAY,
                        TooltipDisplay.DEFAULT));
        NeoForge.EVENT_BUS.post(event);
    }
}

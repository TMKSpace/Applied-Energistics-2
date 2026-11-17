/*
 * Часть nfcompat. Миксин на фабрику клиентских тултипов.
 *
 * Vanilla `ClientTooltipComponent.create` знает только ванильные типы компонентов и
 * бросает «Unknown TooltipComponent» на любом чужом. NeoForge это патчит; мы делаем
 * то же миксином: если для класса компонента есть фабрика из
 * `RegisterClientTooltipComponentFactoriesEvent`, возвращаем её результат и выходим.
 *
 * Почему не Fabric `TooltipComponentCallback`: этот API делает ровно то же, но живёт
 * в модуле `fabric-rendering-v1`, которого нет в нашем compile classpath. Тянуть
 * модуль ради одной строки не стали.
 */
package nfcompat.mixin.client;

import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import nfcompat.client.TooltipFactories;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientTooltipComponent.class)
public interface ClientTooltipComponentMixin {

    @Inject(method = "create(Lnet/minecraft/world/inventory/tooltip/TooltipComponent;)"
            + "Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipComponent;",
            at = @At("HEAD"),
            cancellable = true)
    private static void nfcompat$createCustom(TooltipComponent component,
            CallbackInfoReturnable<ClientTooltipComponent> cir) {
        var custom = TooltipFactories.create(component);
        if (custom != null) {
            cir.setReturnValue(custom);
        }
    }
}

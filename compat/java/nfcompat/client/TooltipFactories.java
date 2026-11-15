/*
 * Часть nfcompat. Реестр фабрик клиентских тултипов.
 *
 * NeoForge патчит `ClientTooltipComponent.create(TooltipComponent)`, чтобы он умел
 * отдавать клиентские компоненты модов (иначе ванильный switch бросает
 * «Unknown TooltipComponent»). Своих классов в ваниль мы не добавляем, поэтому
 * фабрики складываются здесь, а читает их миксин в `ClientTooltipComponent`.
 */
package nfcompat.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.jetbrains.annotations.Nullable;

public final class TooltipFactories {
    public static final Map<Class<? extends TooltipComponent>, Function<TooltipComponent, ClientTooltipComponent>> FACTORIES = new ConcurrentHashMap<>();

    private TooltipFactories() {
    }

    @Nullable
    public static ClientTooltipComponent create(TooltipComponent component) {
        var factory = FACTORIES.get(component.getClass());
        return factory == null ? null : factory.apply(component);
    }
}

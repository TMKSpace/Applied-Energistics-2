/*
 * Часть nfcompat. OUTBOUND-регистрация item-capability AE2 в Fabric Transfer API.
 *
 * Зеркало StorageBridge, только не по блокам, а по предметам: берём ровно те предметы,
 * для которых AE2 (и шим) зарегистрировали NeoForge-ные провайдеры
 * `Capabilities.Item.ITEM` / `Capabilities.Fluid.ITEM` / `Capabilities.Energy.ITEM`
 * (`ItemCapability.providerItems()`), и вешаем на них Fabric-ные lookup-провайдеры.
 *
 * Симптом без этого: энергоячейку/портативную ячейку AE2 нельзя зарядить кабелем Team
 * Reborn и нельзя «привязать» к P2P-туннелю (аттюнмент по `EnergyStorage.ITEM`).
 *
 * Провайдер спрашивает `getCapabilityDirect` — версию без Fabric-fallback'а: иначе
 * получился бы цикл `X.ITEM.find -> наш провайдер -> getCapability -> X.ITEM.find`.
 * `registerFallback` не используем по той же причине, что и для блоков: он дёргался
 * бы для каждого предмета в игре и открывал ту же рекурсию с обратной стороны.
 *
 * Вызывать один раз и обязательно ПОСЛЕ `RegisterCapabilitiesEvent`, иначе список
 * предметов-провайдеров будет пуст.
 */
package nfcompat.bridge;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

public final class ItemCapabilityBridge {

    private static boolean outboundRegistered;

    private ItemCapabilityBridge() {
    }

    public static void registerOutbound() {
        if (outboundRegistered) {
            return;
        }
        outboundRegistered = true;

        registerItems();
        registerFluids();
        // Энергия — мягкая зависимость: без team_reborn_energy просто ничего не делает.
        FabricEnergy.registerItemOutbound();
    }

    private static void registerItems() {
        var items = Capabilities.Item.ITEM.providerItems();
        if (items.isEmpty()) {
            return;
        }

        ItemStorage.ITEM.registerForItems((stack, context) -> {
            // Только NeoForge-ные провайдеры, без Fabric-fallback'а: см. шапку файла.
            ResourceHandler<ItemResource> handler = Capabilities.Item.ITEM.getCapabilityDirect(stack,
                    toAccess(context));
            return handler == null ? null : new ItemHandlerStorage(handler);
        }, items.toArray(Item[]::new));

        System.out.println("[nfcompat/item] outbound: ItemStorage.ITEM <- "
                + items.size() + " предмет(ов) с NeoForge-провайдером предметов");
    }

    private static void registerFluids() {
        var items = Capabilities.Fluid.ITEM.providerItems();
        if (items.isEmpty()) {
            return;
        }

        FluidStorage.ITEM.registerForItems((stack, context) -> {
            var handler = Capabilities.Fluid.ITEM.getCapabilityDirect(stack, toAccess(context));
            return handler == null ? null : new FluidHandlerStorage(handler);
        }, items.toArray(Item[]::new));

        System.out.println("[nfcompat/item] outbound: FluidStorage.ITEM <- "
                + items.size() + " предмет(ов) с NeoForge-провайдером жидкостей");
    }

    /** Fabric-ный контекст -> `ItemAccess` шима; см. ContainerContextItemAccess. */
    private static net.neoforged.neoforge.transfer.access.ItemAccess toAccess(
            net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext context) {
        return new ContainerContextItemAccess(context);
    }
}

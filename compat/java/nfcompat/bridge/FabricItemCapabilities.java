/*
 * Часть nfcompat. ITEM-мост NeoForge-capability -> Fabric Transfer API.
 *
 * ПОЧЕМУ ОН НУЖЕН
 * У NeoForge энергия и жидкость внутри предметов живут в `Capabilities.Energy.ITEM` /
 * `Capabilities.Fluid.ITEM` (`ItemCapability`, контекст — `ItemAccess`). На Fabric то
 * же самое — `EnergyStorage.ITEM` / `FluidStorage.ITEM` (`ItemApiLookup`, контекст —
 * `ContainerItemContext`). Пока моста не было:
 *   - жидкостной терминал/интерфейс не видели вёдра и контейнеры (пустая capability);
 *   - P2P-аттюнмент по предмету не работал ни для жидкостей, ни для энергии, из-за
 *     чего JEI-плагин AE2 падал на `Ingredient.of([])`;
 *   - ghost-drag жидкости из JEI не находил, куда её наливать.
 *
 * Как: `ItemCapability.getCapability` после NeoForge-ных провайдеров спрашивает этот
 * класс, а он отдаёт Fabric-ное хранилище, обёрнутое в наш `ResourceHandler` /
 * `EnergyHandler` (см. StorageItemHandler / StorageFluidHandler / EnergyStorageHandler).
 *
 * Контекст: Fabric-ному lookup'у нужен `ContainerItemContext`, у нас есть `ItemAccess`.
 * Переводим через {@link ItemAccessContainerContext} — иначе вёдра не смогли бы
 * подменять предмет на заполненный.
 *
 * Энергия — мягкая зависимость: без мода `team_reborn_energy` секция молчит, а типы
 * Team Reborn в этом классе не упоминаются вообще (см. FabricEnergy).
 */
package nfcompat.bridge;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.BaseCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import org.jetbrains.annotations.Nullable;

public final class FabricItemCapabilities {

    private FabricItemCapabilities() {
    }

    /**
     * @return объект для запрошенной item-capability или {@code null}, если Fabric-ного
     *         хранилища у этого предмета нет
     */
    @Nullable
    public static Object lookup(BaseCapability<?, ?> capability, ItemStack stack,
            @Nullable Object context) {
        // Все три item-capability шима имеют контекст ItemAccess. Без него спросить
        // Fabric нечего: без контекста провайдеры вёдер/ячеек просто не работают.
        if (!(context instanceof ItemAccess access)) {
            return null;
        }

        if (capability == Capabilities.Item.ITEM) {
            var storage = ItemStorage.ITEM.find(stack, new ItemAccessContainerContext(access));
            return storage == null ? null : new StorageItemHandler(storage);
        }

        if (capability == Capabilities.Fluid.ITEM) {
            var storage = FluidStorage.ITEM.find(stack, new ItemAccessContainerContext(access));
            // Журнал переноса остатка: у бутылки квант 27000 droplets = 333.33 mB, и без
            // него каждая налитая бутылка «дарит» 1/3 mB (см. FluidCarry).
            return storage == null ? null : new StorageFluidHandler(storage, FluidCarry.forAccess(access));
        }

        if (capability == Capabilities.Energy.ITEM) {
            return FabricEnergy.lookupItem(stack, new ItemAccessContainerContext(access));
        }

        return null;
    }
}

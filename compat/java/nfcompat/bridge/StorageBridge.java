/*
 * Часть nfcompat. OUTBOUND-регистрация предметного и жидкостного хранилищ AE2
 * в Fabric Transfer API.
 *
 * Зачем: inbound-мост (`FabricCapabilities` + StorageItemHandler/StorageFluidHandler)
 * научил AE2 видеть чужие хранилища, но обратной стороны не было — воронка, труба или
 * машина Fabric-мода по `ItemStorage.SIDED`/`FluidStorage.SIDED` получали null.
 * Симптом: воронка над ME-сундуком не вставляет, storage bus/интерфейс снаружи пустые.
 *
 * Как: берём ровно те блоки, для которых AE2 (и шим) зарегистрировали NeoForge-ные
 * провайдеры `Capabilities.Item.BLOCK` / `Capabilities.Fluid.BLOCK`
 * (`BlockCapability.providerBlocks()`), и вешаем на них Fabric-ные lookup-провайдеры.
 * Это покрывает ME-сундук, диск-драйв, интерфейс, конденсатор, sky stone tank и все
 * части на CABLE_BUS (AE2 регистрирует части как block-entity-провайдеры шины).
 *
 * Провайдер спрашивает `getCapabilityDirect` — версию без Fabric-fallback'а: иначе
 * получился бы цикл `SIDED.find -> наш провайдер -> getCapability -> SIDED.find`.
 * `registerFallback` не используем: он дёргался бы для КАЖДОГО блока в мире (перф)
 * и открывал бы ту же рекурсию с обратной стороны.
 *
 * Вызывать один раз и обязательно ПОСЛЕ `RegisterCapabilitiesEvent`, иначе список
 * блоков-провайдеров будет пуст.
 */
package nfcompat.bridge;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.capabilities.Capabilities;

public final class StorageBridge {

    private static boolean outboundRegistered;

    private StorageBridge() {
    }

    public static void registerOutbound() {
        if (outboundRegistered) {
            return;
        }
        outboundRegistered = true;

        registerItems();
        registerFluids();
    }

    private static void registerItems() {
        var blocks = Capabilities.Item.BLOCK.providerBlocks();
        if (blocks.isEmpty()) {
            // Ни один мод (включая сам AE2) не зарегистрировал предметных провайдеров.
            return;
        }

        ItemStorage.SIDED.registerForBlocks((level, pos, state, blockEntity, side) -> {
            // Только NeoForge-ные провайдеры, без Fabric-fallback'а: см. шапку файла.
            var handler = Capabilities.Item.BLOCK.getCapabilityDirect(level, pos, state,
                    blockEntity, side);
            return handler == null ? null : new ItemHandlerStorage(handler);
        }, blocks.toArray(Block[]::new));

        System.out.println("[nfcompat/storage] outbound: ItemStorage.SIDED <- "
                + blocks.size() + " блок(ов) с NeoForge-провайдером предметов");
    }

    private static void registerFluids() {
        var blocks = Capabilities.Fluid.BLOCK.providerBlocks();
        if (blocks.isEmpty()) {
            return;
        }

        FluidStorage.SIDED.registerForBlocks((level, pos, state, blockEntity, side) -> {
            var handler = Capabilities.Fluid.BLOCK.getCapabilityDirect(level, pos, state,
                    blockEntity, side);
            return handler == null ? null : new FluidHandlerStorage(handler);
        }, blocks.toArray(Block[]::new));

        System.out.println("[nfcompat/storage] outbound: FluidStorage.SIDED <- "
                + blocks.size() + " блок(ов) с NeoForge-провайдером жидкостей");
    }
}

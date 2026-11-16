/*
 * Часть nfcompat. ЕДИНСТВЕННОЕ место, где мост энергии трогает типы `team.reborn.energy.*`.
 *
 * Грузится только из `FabricEnergy` и только когда мод `team_reborn_energy` реально
 * загружен (см. комментарий в FabricEnergy). Поэтому в безусловно загружаемых классах
 * (`FabricCapabilities`, `NfCompat`, `LifecycleBridge`) нет ни одного упоминания
 * `team.reborn.energy.*`: на сборке без мода моста просто нет, а AE2 работает как раньше.
 *
 * Две стороны:
 *   inbound  — `EnergyStorage.SIDED.find(...)` заворачивается в `EnergyStorageHandler`
 *              (наш `EnergyHandler`), чтобы AE2 видела энергию чужих машин;
 *   outbound — блоки, у которых есть NeoForge-ный провайдер `Capabilities.Energy.BLOCK`,
 *              регистрируются в Fabric-ном lookup, чтобы кабели/машины Team Reborn
 *              видели AE2. Провайдер спрашивает `getCapabilityDirect` — версию без
 *              Fabric-fallback'а: иначе получилась бы бесконечная рекурсия
 *              SIDED.find -> наш провайдер -> getCapability -> SIDED.find.
 *
 * `registerFallback` сознательно не используется: он дёргался бы для КАЖДОГО блока
 * в мире (перф) и открывал бы ту же рекурсию с обратной стороны.
 *
 * Единицы не пересчитываются: 1 E (Team Reborn) == 1 FE. FE->AE (0.5) — это
 * `PowerUnit.FE` в `appeng.helpers.ForgeEnergyAdapter`, к мосту отношения не имеет.
 */
package nfcompat.bridge;

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import org.jetbrains.annotations.Nullable;
import team.reborn.energy.api.EnergyStorage;

public final class EnergyBridge {

    private static boolean outboundRegistered;
    private static boolean itemOutboundRegistered;

    private EnergyBridge() {
    }

    /** Inbound: Fabric-ное хранилище -> наш {@link EnergyHandler}. */
    @Nullable
    static Object lookup(Level level, BlockPos pos, @Nullable BlockState state,
            @Nullable BlockEntity blockEntity, @Nullable Direction side) {
        EnergyStorage storage = EnergyStorage.SIDED.find(level, pos, state, blockEntity, side);
        return storage == null ? null : new EnergyStorageHandler(storage);
    }

    /**
     * Inbound для предметов: `EnergyStorage.ITEM.find` -> наш {@link EnergyHandler}.
     * Хранилище предмета мутирует сам предмет через контекст, поэтому подмену
     * (разряженная ячейка -> заряженная) делает {@link ItemAccessContainerContext}.
     */
    @Nullable
    static Object lookupItem(ItemStack stack, ContainerItemContext context) {
        EnergyStorage storage = EnergyStorage.ITEM.find(stack, context);
        return storage == null ? null : new EnergyStorageHandler(storage);
    }

    /**
     * Outbound для предметов: NeoForge-ные провайдеры `Capabilities.Energy.ITEM`
     * (заряжаемые инструменты и портативные ячейки AE2) -> `EnergyStorage.ITEM`.
     * Однократно.
     */
    static void registerItemOutbound() {
        if (itemOutboundRegistered) {
            return;
        }
        itemOutboundRegistered = true;

        var items = Capabilities.Energy.ITEM.providerItems();
        if (items.isEmpty()) {
            return;
        }

        EnergyStorage.ITEM.registerForItems((stack, context) -> {
            // Только NeoForge-ные провайдеры, без Fabric-fallback'а: см. шапку файла.
            EnergyHandler handler = Capabilities.Energy.ITEM.getCapabilityDirect(stack,
                    new ContainerContextItemAccess(context));
            return handler == null ? null : new EnergyHandlerStorage(handler);
        }, items.toArray(Item[]::new));

        System.out.println("[nfcompat/energy] outbound: EnergyStorage.ITEM <- "
                + items.size() + " предмет(ов) с NeoForge-провайдером энергии");
    }

    /** Outbound: NeoForge-ные провайдеры энергии -> `EnergyStorage.SIDED`. Однократно. */
    static void registerOutbound() {
        if (outboundRegistered) {
            return;
        }
        outboundRegistered = true;

        var blocks = Capabilities.Energy.BLOCK.providerBlocks();
        if (blocks.isEmpty()) {
            // Ни один мод (включая сам AE2) не зарегистрировал энерго-провайдеров —
            // регистрировать в Fabric нечего.
            return;
        }

        EnergyStorage.SIDED.registerForBlocks((level, pos, state, blockEntity, side) -> {
            // Только NeoForge-ные провайдеры, без Fabric-fallback'а: см. шапку файла.
            EnergyHandler handler = Capabilities.Energy.BLOCK.getCapabilityDirect(level, pos, state,
                    blockEntity, side);
            return handler == null ? null : new EnergyHandlerStorage(handler);
        }, blocks.toArray(Block[]::new));

        System.out.println("[nfcompat/energy] outbound: EnergyStorage.SIDED <- "
                + blocks.size() + " блок(ов) с NeoForge-провайдером энергии");
    }
}

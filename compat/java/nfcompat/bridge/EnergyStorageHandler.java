/*
 * Часть nfcompat. INBOUND: Fabric-ное хранилище энергии (Team Reborn Energy)
 * предъявляется как NeoForge-ный `EnergyHandler`, которого ждёт AE2.
 *
 * Симметрично StorageItemHandler / StorageFluidHandler, но без индексов и без
 * пересчёта единиц: у Team Reborn всё в `long`, 1 E == 1 FE, поэтому числа идут как есть.
 * Никакого коэффициента тут быть не должно: FE->AE (0.5) применяет апстрим
 * (`PowerUnit.FE` в `ForgeEnergyAdapter`).
 *
 * Транзакции: работаем с ХРАНИЛИЩЕМ Fabric-ным, но транзакция снаружи NeoForge-ная
 * (AE2 держит её и передаёт в insert/extract). Привязываем её к Fabric-ной через
 * HandlerTransactions.runOntoFabric — иначе `Actionable.SIMULATE` реально менял бы
 * заряд, а откат не доходил бы до хранилища. Обратное направление
 * (EnergyHandlerStorage) делает то же зеркально.
 */
package nfcompat.bridge;

import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import team.reborn.energy.api.EnergyStorage;

public final class EnergyStorageHandler implements EnergyHandler {

    private final EnergyStorage storage;

    public EnergyStorageHandler(EnergyStorage storage) {
        this.storage = storage;
    }

    public EnergyStorage storage() {
        return storage;
    }

    @Override
    public long getAmountAsLong() {
        return Math.max(0, storage.getAmount());
    }

    @Override
    public long getCapacityAsLong() {
        return Math.max(0, storage.getCapacity());
    }

    @Override
    public int insert(int amount, TransactionContext transaction) {
        if (amount <= 0 || !storage.supportsInsertion()) {
            return 0;
        }
        return HandlerTransactions.runOntoFabric(transaction, amount,
                fabricTx -> (int) Math.clamp(storage.insert(amount, fabricTx), 0, amount));
    }

    @Override
    public int extract(int amount, TransactionContext transaction) {
        if (amount <= 0 || !storage.supportsExtraction()) {
            return 0;
        }
        return HandlerTransactions.runOntoFabric(transaction, amount,
                fabricTx -> (int) Math.clamp(storage.extract(amount, fabricTx), 0, amount));
    }
}

/*
 * Часть nfcompat. INBOUND: Fabric-ное хранилище энергии (Team Reborn Energy)
 * предъявляется как NeoForge-ный `EnergyHandler`, которого ждёт AE2.
 *
 * Симметрично StorageItemHandler / StorageFluidHandler, но без индексов и без
 * пересчёта единиц: у Team Reborn всё в `long`, 1 E == 1 FE, поэтому числа идут как есть.
 * Никакого коэффициента тут быть не должно: FE->AE (0.5) применяет апстрим
 * (`PowerUnit.FE` в `ForgeEnergyAdapter`).
 *
 * Транзакции: работаем с ХРАНИЛИЩЕМ Fabric-ным, поэтому и транзакция Fabric-ная
 * (`Transaction.openOuter`, см. StorageItemHandler). NeoForge-ная транзакция из
 * аргумента `insert`/`extract` тут не используется: прицепить её к Fabric-ной нельзя,
 * поэтому операция фиксируется сразу. Для одиночной вставки/извлечения это
 * эквивалентно, но составная операция через границу не откатится.
 * В обратную сторону (EnergyHandlerStorage) откат работает — там транзакция Fabric-ная
 * внешняя и шим-транзакцию можно привязать к её закрытию.
 */
package nfcompat.bridge;

import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
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
        try (Transaction tx = Transaction.openOuter()) {
            long inserted = storage.insert(amount, tx);
            tx.commit();
            return (int) Math.clamp(inserted, 0, amount);
        }
    }

    @Override
    public int extract(int amount, TransactionContext transaction) {
        if (amount <= 0 || !storage.supportsExtraction()) {
            return 0;
        }
        try (Transaction tx = Transaction.openOuter()) {
            long extracted = storage.extract(amount, tx);
            tx.commit();
            return (int) Math.clamp(extracted, 0, amount);
        }
    }
}

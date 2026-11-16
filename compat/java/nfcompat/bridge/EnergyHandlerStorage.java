/*
 * Часть nfcompat. OUTBOUND: NeoForge-ный `EnergyHandler` (приёмник AE2, FE P2P-туннель)
 * предъявляется как Fabric-ное `EnergyStorage` (Team Reborn Energy), чтобы кабели и
 * машины Fabric-модов могли качать энергию в AE2 и из AE2.
 *
 * Единицы: 1 FE == 1 E, пересчёта нет. Но типы разные: у Team Reborn `long`, у шима
 * `int`, поэтому всё, что больше `Integer.MAX_VALUE`, срезается (за раз столько никто
 * не гоняет, а молча терять запрос нельзя — поэтому клампим явно).
 *
 * Транзакции — см. HandlerTransactions: NeoForge-ная root-транзакция открывается внутри
 * и привязывается к Fabric-ной, чтобы симуляция/откат и commit работали в обе стороны.
 */
package nfcompat.bridge;

import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import team.reborn.energy.api.EnergyStorage;

public final class EnergyHandlerStorage implements EnergyStorage {

    private final EnergyHandler handler;

    public EnergyHandlerStorage(EnergyHandler handler) {
        this.handler = handler;
    }

    public EnergyHandler handler() {
        return handler;
    }

    @Override
    public boolean supportsInsertion() {
        return true;
    }

    @Override
    public boolean supportsExtraction() {
        return true;
    }

    @Override
    public long insert(long maxAmount, TransactionContext transaction) {
        if (maxAmount <= 0) {
            return 0;
        }
        int max = (int) Math.min(maxAmount, Integer.MAX_VALUE);
        return HandlerTransactions.run(transaction, max, tx -> handler.insert(max, tx));
    }

    @Override
    public long extract(long maxAmount, TransactionContext transaction) {
        if (maxAmount <= 0) {
            return 0;
        }
        int max = (int) Math.min(maxAmount, Integer.MAX_VALUE);
        return HandlerTransactions.run(transaction, max, tx -> handler.extract(max, tx));
    }

    @Override
    public long getAmount() {
        return Math.max(0, handler.getAmountAsLong());
    }

    @Override
    public long getCapacity() {
        return Math.max(0, handler.getCapacityAsLong());
    }
}

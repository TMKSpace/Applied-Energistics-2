/*
 * Часть nfcompat. OUTBOUND: NeoForge-ный `EnergyHandler` (приёмник AE2, FE P2P-туннель)
 * предъявляется как Fabric-ное `EnergyStorage` (Team Reborn Energy), чтобы кабели и
 * машины Fabric-модов могли качать энергию в AE2 и из AE2.
 *
 * Единицы: 1 FE == 1 E, пересчёта нет. Но типы разные: у Team Reborn `long`, у шима
 * `int`, поэтому всё, что больше `Integer.MAX_VALUE`, срезается (за раз столько никто
 * не гоняет, а молча терять запрос нельзя — поэтому клампим явно).
 *
 * Транзакции. Здесь внешняя транзакция — Fabric-ная, NeoForge-ную открываем внутри и
 * привязываем к её закрытию. Это не украшение:
 *   - Team Reborn при пуше сначала симулирует вставку (транзакция откатывается) —
 *     без привязки откат не дошёл бы до AE2 и энергия бы «дублировалась»;
 *   - `ForgeEnergyAdapter` копит вставки в буфере и сбрасывает их в сеть только на
 *     root-commit'e NeoForge-ной транзакции — без commit'а вставка не доедет вообще.
 * Ровно этот же паттерн уже используется в FluidHandlerStorage.
 *
 * Одна тонкость: если NeoForge-ная транзакция на этом потоке УЖЕ открыта (Fabric-ная
 * транзакция трогает два наших хранилища подряд, или AE2 сама зовёт нас из своей),
 * вторую root открывать нельзя — шим бросит IllegalStateException. В этом случае
 * работаем в текущей транзакции, как обычный вложенный хендлер.
 */
package nfcompat.bridge;

import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import team.reborn.energy.api.EnergyStorage;

public final class EnergyHandlerStorage implements EnergyStorage {

    /** Операция над нашим хендлером; транзакцию выбирает {@link #run}. */
    private interface EnergyOp {
        int apply(net.neoforged.neoforge.transfer.transaction.TransactionContext transaction);
    }

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
        return run(transaction, max, tx -> handler.insert(max, tx));
    }

    @Override
    public long extract(long maxAmount, TransactionContext transaction) {
        if (maxAmount <= 0) {
            return 0;
        }
        int max = (int) Math.min(maxAmount, Integer.MAX_VALUE);
        return run(transaction, max, tx -> handler.extract(max, tx));
    }

    @Override
    public long getAmount() {
        return Math.max(0, handler.getAmountAsLong());
    }

    @Override
    public long getCapacity() {
        return Math.max(0, handler.getCapacityAsLong());
    }

    /**
     * Выполнить операцию так, чтобы её судьба совпала с судьбой Fabric-ной транзакции.
     *
     * @param max верхняя граница результата (для клампа в {@code long})
     * @return сколько реально прошло, 0..max
     */
    private long run(TransactionContext fabricTx, int max, EnergyOp op) {
        var current = currentTransaction();
        if (current != null) {
            // Уже внутри NeoForge-ной транзакции: не открываем вторую root.
            return Math.clamp(op.apply(current), 0, max);
        }
        if (Transaction.getLifecycle() != Transaction.Lifecycle.NONE) {
            // Транзакция закрывается: открывать нельзя, а применить «мимо» — значит
            // потерять откат. Честнее вернуть 0, чем изменить состояние втихую.
            return 0;
        }
        Transaction nfTx = Transaction.openRoot();
        bindRollback(nfTx, fabricTx);
        return Math.clamp(op.apply(nfTx), 0, max);
    }

    /** Текущая ОТКРЫТАЯ транзакция потока или null. */
    @SuppressWarnings("deprecation")
    private static net.neoforged.neoforge.transfer.transaction.TransactionContext currentTransaction() {
        return Transaction.getLifecycle() == Transaction.Lifecycle.OPEN
                ? Transaction.getCurrentOpenedTransaction()
                : null;
    }

    /**
     * Привязка NeoForge-ной транзакции к внешней Fabric-ной: закрылась Fabric-ная —
     * коммитим, только если она закоммичена, иначе закрываем без коммита (откат).
     */
    private static void bindRollback(Transaction nfTx, TransactionContext fabricTx) {
        fabricTx.addCloseCallback((ctx, result) -> {
            if (result.wasCommitted()) {
                nfTx.commit();
            }
            nfTx.close();
        });
    }
}

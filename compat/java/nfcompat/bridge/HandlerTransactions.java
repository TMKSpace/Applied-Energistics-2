/*
 * Часть nfcompat. Общий мостик транзакций между Fabric Transfer API и шимом.
 *
 * Два направления, по одному методу на каждое:
 *
 *  - {@link #run} — Fabric -> NeoForge. Снаружи всегда открыта Fabric-ная транзакция
 *    (её открыл мод-потребитель: воронка, труба, машина Team Reborn). NeoForge-ную мы
 *    открываем внутри и привязываем к её закрытию, потому что иначе рвётся семантика:
 *      * Fabric-ные моды сначала СИМУЛИРУЮТ операцию (транзакция откатывается) — без
 *        привязки откат не дошёл бы до нашего хендлера, и предметы/жидкость «дублировались»;
 *      * хендлеры AE2 копят изменения и сбрасывают их в сеть/инвентарь только на commit'e
 *        root-транзакции NeoForge — без commit'а вставка не доедет вообще.
 *
 *  - {@link #runOntoFabric} — NeoForge -> Fabric. Теперь транзакция снаружи
 *    NeoForge-ная (AE2 держит её и оперирует `Actionable.SIMULATE`/`MODULATE`), а
 *    хранилище внутри Fabric-ное. Раньше на этом месте стоял `Transaction.openOuter()`
 *    с немедленным `commit()`: это ломало SIMULATE — AE2 «симулировала» опустошение
 *    ведра, а оно реально опустошалось: при отказе сети жидкость просто исчезала.
 *    Поэтому Fabric-ную транзакцию открываем ОДНУ на NeoForge-ную root-транзакцию и
 *    вешаем на неё журнал ({@link FabricBound}): abort -> rollback, root commit ->
 *    commit. Вложенные NeoForge-ные транзакции живут внутри уже открытой Fabric-ной
 *    вложенной (`openNested`): их откат по-прежнему не долетает до Fabric-ной стороны,
 *    но это осознанное ограничение (см. ниже).
 *
 * Ограничение (обоих направлений): вложенная (не root) транзакция одной стороны не
 * откатывает изменения, уже слитые в родительскую транзакцию другой. Вложенные
 * NeoForge-ные транзакции использует `ItemAccess.exchange` (попытка обмена предмета) —
 * на практике это одна операция над одним стеком, так что расхождение не наблюдаемо.
 */
package nfcompat.bridge;

import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import org.jetbrains.annotations.Nullable;

final class HandlerTransactions {

    /** Операция над нашим хендлером; транзакцию выбирает {@link #run}. */
    interface Operation {
        int apply(net.neoforged.neoforge.transfer.transaction.TransactionContext transaction);
    }

    /** Операция над Fabric-ным хранилищем; транзакцию выбирает {@link #runOntoFabric}. */
    interface FabricOperation {
        int apply(TransactionContext transaction);
    }

    /**
     * Fabric-ная транзакция, которая сейчас обслуживает NeoForge-ную. Нужна адаптеру
     * `ItemAccess` -> `ContainerItemContext`: он не получает Fabric-ную транзакцию
     * аргументом, а менять предмет обязан внутри неё (иначе не откатится).
     */
    private static final ThreadLocal<@Nullable TransactionContext> CURRENT_FABRIC = new ThreadLocal<>();

    /** Fabric-ная транзакция, привязанная к текущей NeoForge-ной root-транзакции потока. */
    private static final ThreadLocal<FabricBound> FABRIC_BOUND = ThreadLocal.withInitial(FabricBound::new);

    private HandlerTransactions() {
    }

    /**
     * Выполнить операцию так, чтобы её судьба совпала с судьбой Fabric-ной транзакции.
     *
     * @param max верхняя граница результата (кламп в {@code int} для NeoForge-ного API)
     * @return сколько реально прошло, 0..max
     */
    static int run(TransactionContext fabricTx, int max, Operation operation) {
        var current = currentTransaction();
        if (current != null) {
            // Уже внутри NeoForge-ной транзакции: не открываем вторую root.
            return apply(fabricTx, max, operation, current);
        }
        if (net.neoforged.neoforge.transfer.transaction.Transaction.getLifecycle()
                != net.neoforged.neoforge.transfer.transaction.Transaction.Lifecycle.NONE) {
            // Транзакция закрывается: открывать нельзя, а применить «мимо» — значит
            // потерять откат. Честнее вернуть 0, чем изменить состояние втихую.
            return 0;
        }
        var nfTx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot();
        fabricTx.addCloseCallback((ctx, result) -> {
            if (result.wasCommitted()) {
                nfTx.commit();
            }
            nfTx.close();
        });
        return apply(fabricTx, max, operation, nfTx);
    }

    /**
     * Выполнить операцию над Fabric-ным хранилищем так, чтобы её судьба совпала с
     * судьбой NeoForge-ной транзакции (см. шапку файла).
     *
     * @param max верхняя граница результата
     * @return сколько реально прошло, 0..max
     */
    static int runOntoFabric(net.neoforged.neoforge.transfer.transaction.TransactionContext nfTx,
            int max, FabricOperation operation) {
        if (max <= 0) {
            return 0;
        }

        TransactionContext fabric = currentFabricTransaction();
        if (fabric != null) {
            // Fabric-ная транзакция уже открыта снаружи: работаем вложенной в неё и
            // сразу сливаем результат, дальше судьбой распоряжается внешняя.
            try (Transaction nested = fabric.openNested()) {
                int result = Math.clamp(operation.apply(nested), 0, max);
                nested.commit();
                return result;
            }
        }

        if (Transaction.getLifecycle() != Transaction.Lifecycle.NONE) {
            // Fabric-ная транзакция закрывается (мы внутри её close-callback'а):
            // открывать новую нельзя, а менять состояние «мимо» — потерять откат.
            return 0;
        }

        if (nfTx.depth() != 0) {
            // Привязать можно только к root-транзакции NeoForge: на вложенной журнал
            // может быть откачен посреди root'а, и Fabric-ную транзакцию пришлось бы
            // закрывать вместе с ним. Для таких вызовов фиксируем сразу, как раньше.
            return runImmediate(max, operation);
        }

        return Math.clamp(operation.apply(FABRIC_BOUND.get().bind(nfTx)), 0, max);
    }

    /** Fabric-ная транзакция, обслуживающая текущую NeoForge-ную, или null. */
    @Nullable
    static TransactionContext currentFabricContext() {
        return CURRENT_FABRIC.get();
    }

    private static int apply(TransactionContext fabricTx, int max, Operation operation,
            net.neoforged.neoforge.transfer.transaction.TransactionContext nfTx) {
        TransactionContext previous = CURRENT_FABRIC.get();
        CURRENT_FABRIC.set(fabricTx);
        try {
            return Math.clamp(operation.apply(nfTx), 0, max);
        } finally {
            if (previous == null) {
                CURRENT_FABRIC.remove();
            } else {
                CURRENT_FABRIC.set(previous);
            }
        }
    }

    /** Старое поведение: своя Fabric-ная транзакция с немедленным commit'ом. */
    private static int runImmediate(int max, FabricOperation operation) {
        try (Transaction tx = Transaction.openOuter()) {
            int result = Math.clamp(operation.apply(tx), 0, max);
            tx.commit();
            return result;
        }
    }

    @SuppressWarnings("deprecation")
    @Nullable
    private static TransactionContext currentFabricTransaction() {
        return Transaction.getCurrentUnsafe();
    }

    /** Текущая ОТКРЫТАЯ транзакция потока или null. */
    @SuppressWarnings("deprecation")
    private static net.neoforged.neoforge.transfer.transaction.TransactionContext currentTransaction() {
        return net.neoforged.neoforge.transfer.transaction.Transaction.getLifecycle()
                == net.neoforged.neoforge.transfer.transaction.Transaction.Lifecycle.OPEN
                        ? net.neoforged.neoforge.transfer.transaction.Transaction.getCurrentOpenedTransaction()
                        : null;
    }

    /**
     * Fabric-ная outer-транзакция, привязанная к NeoForge-ной root-транзакции:
     * откат NeoForge-ной закрывает её без commit'а, успешный root commit — коммитит.
     *
     * <p>Живёт в ThreadLocal и обнуляется в обоих колбэках, поэтому следующая
     * root-транзакция NeoForge получает свежую Fabric-ную.
     */
    private static final class FabricBound extends SnapshotJournal<Transaction> {
        /** null — привязки нет; открытая Fabric-ная транзакция — привязка есть. */
        @Nullable
        private Transaction transaction;

        Transaction bind(net.neoforged.neoforge.transfer.transaction.TransactionContext nfTx) {
            if (transaction == null) {
                transaction = Transaction.openOuter();
            }
            // Регистрирует журнал в транзакции; повторный вызов на той же глубине
            // ничего не делает, поэтому одна Fabric-ная транзакция обслуживает все
            // операции в рамках одной NeoForge-ной.
            updateSnapshots(nfTx);
            return transaction;
        }

        @Override
        protected Transaction createSnapshot() {
            return transaction;
        }

        @Override
        protected void revertToSnapshot(Transaction snapshot) {
            // NeoForge-ная транзакция откатилась: выбрасываем всё, что в ней накопилось.
            snapshot.close();
            transaction = null;
        }

        @Override
        protected void onRootCommit(Transaction snapshot) {
            snapshot.commit();
            snapshot.close();
            transaction = null;
        }
    }
}

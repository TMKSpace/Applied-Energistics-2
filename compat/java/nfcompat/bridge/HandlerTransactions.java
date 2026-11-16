/*
 * Часть nfcompat. Общий мостик транзакций для адаптеров NeoForge -> Fabric.
 *
 * Снаружи всегда открыта Fabric-ная транзакция (её открыл мод-потребитель: воронка,
 * труба, машина Team Reborn). NeoForge-ную мы открываем внутри и привязываем к её
 * закрытию, потому что иначе рвётся семантика:
 *   - Fabric-ные моды сначала СИМУЛИРУЮТ операцию (транзакция откатывается) — без
 *     привязки откат не дошёл бы до нашего хендлера, и предметы/жидкость «дублировались»;
 *   - хендлеры AE2 копят изменения и сбрасывают их в сеть/инвентарь только на commit'e
 *     root-транзакции NeoForge — без commit'а вставка не доедет вообще.
 *
 * Тонкость: если NeoForge-ная транзакция на этом потоке УЖЕ открыта (например, AE2
 * позвала нас из своей, или Fabric-ная операция трогает два наших хранилища подряд),
 * вторую root открывать нельзя — шим бросит IllegalStateException. В этом случае
 * работаем в текущей транзакции, как обычный вложенный хендлер.
 *
 * Тот же паттерн изначально жил приватно в EnergyHandlerStorage; здесь он вынесен,
 * чтобы item/fluid/energy адаптеры не разъезжались.
 */
package nfcompat.bridge;

import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.neoforged.neoforge.transfer.transaction.Transaction;

final class HandlerTransactions {

    /** Операция над нашим хендлером; транзакцию выбирает {@link #run}. */
    interface Operation {
        int apply(net.neoforged.neoforge.transfer.transaction.TransactionContext transaction);
    }

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
            return Math.clamp(operation.apply(current), 0, max);
        }
        if (Transaction.getLifecycle() != Transaction.Lifecycle.NONE) {
            // Транзакция закрывается: открывать нельзя, а применить «мимо» — значит
            // потерять откат. Честнее вернуть 0, чем изменить состояние втихую.
            return 0;
        }
        Transaction nfTx = Transaction.openRoot();
        fabricTx.addCloseCallback((ctx, result) -> {
            if (result.wasCommitted()) {
                nfTx.commit();
            }
            nfTx.close();
        });
        return Math.clamp(operation.apply(nfTx), 0, max);
    }

    /** Текущая ОТКРЫТАЯ транзакция потока или null. */
    @SuppressWarnings("deprecation")
    private static net.neoforged.neoforge.transfer.transaction.TransactionContext currentTransaction() {
        return Transaction.getLifecycle() == Transaction.Lifecycle.OPEN
                ? Transaction.getCurrentOpenedTransaction()
                : null;
    }
}

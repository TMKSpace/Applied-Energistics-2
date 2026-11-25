/*
 * Часть nfcompat: перенос остатка при пересчёте mB <-> droplets.
 *
 * ПРОБЛЕМА. Внутри AE2 и шима объём жидкости живёт в NeoForge-ных mB (ведро = 1000),
 * а Fabric Transfer API считает в droplets (ведро = 81000, бутылка = 27000). Бутылка
 * в mB не представима: 27000/81 = 333.33. Пересчёт с округлением вниз (FluidUnits.toNf)
 * на каждой операции «дарит» контейнеру дробную часть: сеть списывает 333 mB, а в
 * бутылку уезжает 27000 droplets (333.33 mB). Три бутылки стоили сети 999 mB вместо
 * 1000 — в игре это видно как 6000 -> 5001 mB.
 *
 * РЕШЕНИЕ. Хранить не «сколько mB в этом кванте», а «сколько droplets ещё НЕ оплачено»
 * (debt, меньше одного mB) и выравнивать им следующую операцию:
 *
 *     mB = (debt + droplets) / 81
 *     debt += droplets - mB * 81
 *
 * Тогда три бутылки стоят 333 + 333 + 334 = ровно 1000 mB, а ведро (81000 droplets,
 * кратно 81) всегда стоит ровно 1000 mB и остаток не трогает. Направления считаются
 * раздельно (вставка в контейнер / извлечение из контейнера), иначе налив и слив
 * гасили бы друг друга и «бутылочный» долг не доезжал бы до сети.
 *
 * ГДЕ ЖИВЁТ. Журнал привязан к ИГРОКУ (см. {@link #forAccess}), а не к
 * StorageFluidHandler: хендлер создаётся заново на каждый запрос capability, то есть
 * на каждый клик терминала, и остаток в нём не пережил бы даже вторую бутылку. Игрок —
 * единственный объект, который видит все клики; при выходе из мира он исчезает
 * (WeakHashMap), теряя меньше одного mB — это осознанная плата.
 *
 * ТРАНЗАКЦИИ. Журнал — {@link SnapshotJournal}: остаток меняется сразу, но снапшот
 * привязан к NeoForge-ной транзакции. `Actionable.SIMULATE` (root-транзакция без
 * commit'а) откатывает остаток, поэтому симуляция и реальная операция всегда
 * докладывают ОДНО И ТО ЖЕ число — иначе меню вынуло бы из сети не столько, сколько
 * положило в предмет.
 *
 * Ограничение то же, что у HandlerTransactions: вложенная (не root) NeoForge-ная
 * транзакция, в которой Fabric-ная сторона уже зафиксировалась сразу
 * (`runImmediate`), откатит только остаток, а жидкость оставит. Ошибка и там меньше
 * одного mB — это тот же класс расхождения, что и в мосте транзакций.
 *
 * ЧЕСТНОСТЬ. Отчёт всегда {@code <=} запрошенного (кламп по {@code maxAmount}) и всегда
 * {@code отчёт * 81 <= droplets}, то есть сеть никогда не платит больше, чем контейнер
 * реально получил: остаток не может стать отрицательным. Ошибка в любой момент
 * времени меньше одного mB на направление.
 */
package nfcompat.bridge;

import java.util.Map;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

public final class FluidCarry extends SnapshotJournal<long[]> {

    /**
     * Журналы по игрокам. WeakHashMap: как только игрок вышел и был собран, запись
     * исчезает сама (значение на игрока не ссылается, так что цикла нет).
     */
    private static final Map<Player, FluidCarry> BY_PLAYER = new WeakHashMap<>();

    /** Droplets уже отданы контейнеру, но ещё не списаны с сети. */
    private long insertDebt;
    /** Droplets уже забраны у контейнера, но ещё не зачислены в сеть. */
    private long extractDebt;

    /** Для тестов: журнал без игрока. */
    FluidCarry() {
    }

    /**
     * Журнал для конкретного доступа к предмету или {@code null}, если владельца-игрока
     * нет (например {@code ItemAccess.forStack} — там ведро наполнить всё равно нельзя).
     */
    @Nullable
    public static FluidCarry forAccess(@Nullable ItemAccess access) {
        if (!(access instanceof PlayerLedgerAccess owner)) {
            return null;
        }
        var player = owner.nfcompat$player();
        if (player == null) {
            return null;
        }
        synchronized (BY_PLAYER) {
            return BY_PLAYER.computeIfAbsent(player, p -> new FluidCarry());
        }
    }

    /**
     * Сколько mB списать с сети за {@code droplets}, отданные контейнеру.
     *
     * @param maxAmount сколько вообще было запрошено (отчёт не может быть больше)
     */
    public int reportInsert(TransactionContext transaction, long droplets, int maxAmount) {
        return report(transaction, true, droplets, maxAmount);
    }

    /**
     * Сколько mB зачислить в сеть за {@code droplets}, забранные у контейнера.
     */
    public int reportExtract(TransactionContext transaction, long droplets, int maxAmount) {
        return report(transaction, false, droplets, maxAmount);
    }

    private int report(TransactionContext transaction, boolean insert, long droplets, int maxAmount) {
        if (droplets <= 0 || maxAmount <= 0) {
            return 0;
        }

        long debt = insert ? insertDebt : extractDebt;

        // Потолок, который физически может вернуть наш хендлер: запрос (amount * 81)
        // плюс запас PAD, которым добирают квант контейнера. Всё, что больше, — мусор
        // от чужого хранилища (или Long.MAX_VALUE в тесте на переполнение): тогда не
        // изобретаем арифметику, а начинаем счёт заново с нулевого остатка.
        long maxDroplets = (long) maxAmount * FluidUnits.SCALE + FluidUnits.PAD;
        if (droplets > maxDroplets) {
            updateSnapshots(transaction);
            setDebt(insert, 0);
            return maxAmount;
        }

        long total = debt > Long.MAX_VALUE - droplets ? Long.MAX_VALUE : debt + droplets;
        long amountMb = total / FluidUnits.SCALE;
        int reported = (int) Math.min(amountMb, (long) maxAmount);

        // Снапшот ДО изменения: откат NeoForge-ной транзакции вернёт старый остаток
        // (это и есть поведение SIMULATE).
        updateSnapshots(transaction);
        setDebt(insert, total - (long) reported * FluidUnits.SCALE);
        return reported;
    }

    private void setDebt(boolean insert, long value) {
        if (insert) {
            insertDebt = value;
        } else {
            extractDebt = value;
        }
    }

    /** Текущий неоплаченный остаток при вставке (droplets, для тестов и отладки). */
    long insertDebt() {
        return insertDebt;
    }

    /** Текущий неоплаченный остаток при извлечении (droplets, для тестов и отладки). */
    long extractDebt() {
        return extractDebt;
    }

    @Override
    protected long[] createSnapshot() {
        return new long[] { insertDebt, extractDebt };
    }

    @Override
    protected void revertToSnapshot(long[] snapshot) {
        insertDebt = snapshot[0];
        extractDebt = snapshot[1];
    }

    @Override
    public String toString() {
        return "FluidCarry[insert=" + insertDebt + ", extract=" + extractDebt + "]";
    }
}

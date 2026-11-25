package nfcompat.bridge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.neoforged.neoforge.transfer.transaction.Transaction;
import nfcompat.GameBootstrap;

/**
 * Перенос остатка при пересчёте mB &lt;-&gt; droplets.
 * <p>
 * Почему это вообще нужно: бутылка в Fabric — это 27000 droplets = 333.33 mB, целым
 * числом mB не представима. Пока отчёт округлялся вниз на каждой операции, три
 * бутылки списывали 999 mB вместо 1000 (в игре: 6000 -&gt; 5001). Здесь проверяем, что
 * остаток копится и выравнивает баланс, что ведро (81000 droplets, кратно 81) от
 * остатка не страдает и что на границах ничего не переполняется.
 * <p>
 * Тест чистый: настоящий {@code StorageFluidHandler.insert} тянет FluidVariant, а он
 * вне Fabric-рантайма падает (см. StorageFluidHandlerTest), поэтому проверяем сам
 * учёт — ту арифметику, которую хендлер зовёт. Сквозной путь (сеть -&gt; бутылка) ловит
 * геймтест fluid_bottles.
 */
class FluidCarryTest {

    /** Квант бутылки в Fabric: треть ведра, 333.33 mB. */
    private static final long BOTTLE = FluidUnits.FABRIC_BOTTLE;
    private static final long BUCKET = FluidUnits.FABRIC_BUCKET;

    @BeforeAll
    static void setUp() {
        GameBootstrap.ensure();
    }

    /** Одна реальная (коммитящаяся) операция вставки; возвращает списанные mB. */
    private static long insert(FluidCarry carry, long droplets, int max) {
        try (Transaction tx = Transaction.openRoot()) {
            int reported = carry.reportInsert(tx, droplets, max);
            tx.commit();
            return reported;
        }
    }

    /** Одна реальная (коммитящаяся) операция извлечения; возвращает зачисленные mB. */
    private static long extract(FluidCarry carry, long droplets, int max) {
        try (Transaction tx = Transaction.openRoot()) {
            int reported = carry.reportExtract(tx, droplets, max);
            tx.commit();
            return reported;
        }
    }

    @Test
    @DisplayName("3 бутылки из 6000 mB списывают ровно 1000 (333 + 333 + 334)")
    void threeBottlesCostExactlyOneBucket() {
        var carry = new FluidCarry();
        long networkMb = 6000;

        long first = insert(carry, BOTTLE, 1000);
        long second = insert(carry, BOTTLE, 1000);
        long third = insert(carry, BOTTLE, 1000);

        assertThat(new long[] { first, second, third }).containsExactly(333L, 333L, 334L);
        networkMb -= first + second + third;
        assertThat(networkMb).as("в сети после трёх бутылок").isEqualTo(5000);
    }

    @Test
    @DisplayName("3 бутылки обратно зачисляют ровно 1000: 6000 -> 5000 -> 6000")
    void threeBottlesGiveBackExactlyOneBucket() {
        var carry = new FluidCarry();
        long networkMb = 6000;
        for (int i = 0; i < 3; i++) {
            networkMb -= insert(carry, BOTTLE, 1000);
        }
        assertThat(networkMb).isEqualTo(5000);

        for (int i = 0; i < 3; i++) {
            networkMb += extract(carry, BOTTLE, 1000);
        }
        assertThat(networkMb).as("после слива трёх бутылок обратно").isEqualTo(6000);
    }

    @Test
    @DisplayName("слив и налив считаются раздельно и не гасят друг друга")
    void insertAndExtractRemaindersAreIndependent() {
        var carry = new FluidCarry();

        // налили одну бутылку: сеть недоплатила 27 droplets
        assertThat(insert(carry, BOTTLE, 1000)).isEqualTo(333);
        assertThat(carry.insertDebt()).isEqualTo(27);
        assertThat(carry.extractDebt()).isZero();

        // слили одну бутылку: это отдельный счёт, наливный долг не тронут
        assertThat(extract(carry, BOTTLE, 1000)).isEqualTo(333);
        assertThat(carry.extractDebt()).isEqualTo(27);
        assertThat(carry.insertDebt()).isEqualTo(27);
    }

    @Test
    @DisplayName("ведро = 1000 mB и до, и после бутылочного остатка")
    void bucketIsAlwaysExact() {
        var clean = new FluidCarry();
        assertThat(insert(clean, BUCKET, 1000)).isEqualTo(1000);
        assertThat(clean.insertDebt()).as("ведро не оставляет остатка").isZero();

        // 27 droplets «долга» от бутылки не превращают ведро в 1001 mB:
        // 81000 + 27 = 81027, это всё ещё 1000 целых mB
        var dirty = new FluidCarry();
        assertThat(insert(dirty, BOTTLE, 1000)).isEqualTo(333);
        assertThat(insert(dirty, BUCKET, 1000)).isEqualTo(1000);
        assertThat(dirty.insertDebt()).isEqualTo(27);

        // и обратно: слив ведра тоже ровно 1000
        assertThat(extract(dirty, BUCKET, 1000)).isEqualTo(1000);
    }

    @Test
    @DisplayName("отчёт никогда не больше запрошенного, поэтому остаток не уходит в минус")
    void neverReportsMoreThanRequested() {
        var carry = new FluidCarry();
        // два раза по 1000 mB: 333, 333 и долг 54 droplets
        assertThat(insert(carry, BOTTLE, 1000)).isEqualTo(333);
        assertThat(insert(carry, BOTTLE, 1000)).isEqualTo(333);
        assertThat(carry.insertDebt()).isEqualTo(54);

        // теперь просим ровно 333 mB (26973 droplets): запас дотягивает до кванта 27000,
        // и «честно» получилось бы 334 mB — но их никто не запрашивал
        int reported = (int) insert(carry, BOTTLE, 333);

        assertThat(reported).isEqualTo(333);
        assertThat(carry.insertDebt()).as("недоплаченный mB остался в долге, а не пропал").isEqualTo(81);

        // долг не превращает ведро в 1001 mB: кламп по запросу работает и там
        assertThat(insert(carry, BUCKET, 1000)).isEqualTo(1000);
    }

    @Test
    @DisplayName("аккумулятор честен: сеть никогда не платит больше, чем контейнер получил")
    void neverCreatesFluid() {
        var carry = new FluidCarry();
        long dropletsTotal = 0;
        long chargedTotal = 0;

        // разнокалиберные кванты: бутылка, ведро, «две бутылки» и странный контейнер.
        // Больше, чем 1000 mB * 81 + PAD, хранилище вернуть не может — это проверяет
        // отдельный тест на насыщение.
        long[] quanta = { BOTTLE, BUCKET, BOTTLE * 2, 12345L, 1L, BOTTLE, BOTTLE, BOTTLE };
        for (long quantum : quanta) {
            chargedTotal += insert(carry, quantum, 1000);
            dropletsTotal += quantum;
            assertThat(chargedTotal * FluidUnits.SCALE)
                    .as("списано mB против полученных droplets")
                    .isLessThanOrEqualTo(dropletsTotal);
            assertThat(carry.insertDebt())
                    .as("остаток не может быть отрицательным")
                    .isGreaterThanOrEqualTo(0);
            // и меньше одного «полного» кванта: долг гасится следующей же операцией
            assertThat(carry.insertDebt()).isLessThan(FluidUnits.SCALE + 80);
        }
    }

    @Test
    @DisplayName("SIMULATE (транзакция без commit) не двигает остаток")
    void simulateDoesNotMoveRemainder() {
        var carry = new FluidCarry();
        assertThat(insert(carry, BOTTLE, 1000)).isEqualTo(333);
        long debtAfterFirst = carry.insertDebt();

        // как Actionable.SIMULATE: root-транзакция закрывается без commit'а
        int simulated;
        try (Transaction tx = Transaction.openRoot()) {
            simulated = carry.reportInsert(tx, BOTTLE, 1000);
        }
        assertThat(simulated).as("симуляция считает так же, как реальная операция").isEqualTo(333);
        assertThat(carry.insertDebt()).as("откат вернул остаток").isEqualTo(debtAfterFirst);

        // и настоящая операция после симуляции даёт то же число, что дала бы без неё
        assertThat(insert(carry, BOTTLE, 1000)).isEqualTo(333);
    }

    @Test
    @DisplayName("Long.MAX_VALUE droplets не переполняет счётчик")
    void saturatesInsteadOfOverflowing() {
        var carry = new FluidCarry();

        try (Transaction tx = Transaction.openRoot()) {
            // кламп по запрошенному количеству: больше int в NeoForge-ный API всё равно нельзя
            assertThat(carry.reportInsert(tx, Long.MAX_VALUE, Integer.MAX_VALUE))
                    .isEqualTo(Integer.MAX_VALUE);
            tx.commit();
        }
        assertThat(carry.insertDebt()).as("при насыщении остаток обнуляется, а не уезжает в минус").isZero();

        // после «мусорной» операции журнал снова ведёт себя нормально
        assertThat(insert(carry, BOTTLE, 1000)).isEqualTo(333);

        // второй раз, уже с ненулевым долгом
        carry = new FluidCarry();
        insert(carry, 1, 1000);
        try (Transaction tx = Transaction.openRoot()) {
            assertThat(carry.reportExtract(tx, Long.MAX_VALUE, 10)).isEqualTo(10);
            tx.commit();
        }
        assertThat(carry.extractDebt()).isZero();
    }

    @Test
    @DisplayName("нулевые и отрицательные количества не трогают журнал")
    void zeroAndNegativeAreNoOps() {
        var carry = new FluidCarry();
        try (Transaction tx = Transaction.openRoot()) {
            assertThat(carry.reportInsert(tx, 0, 1000)).isZero();
            assertThat(carry.reportInsert(tx, -27000, 1000)).isZero();
            assertThat(carry.reportInsert(tx, 27000, 0)).isZero();
            assertThat(carry.reportExtract(tx, 0, 1000)).isZero();
        }
        assertThat(carry.insertDebt()).isZero();
        assertThat(carry.extractDebt()).isZero();
    }
}

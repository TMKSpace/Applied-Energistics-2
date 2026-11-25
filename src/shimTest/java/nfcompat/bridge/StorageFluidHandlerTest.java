package nfcompat.bridge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import nfcompat.GameBootstrap;

/**
 * StorageFluidHandler: Fabric-ный Storage&lt;FluidVariant&gt; предъявляется NeoForge-ному
 * коду как ResourceHandler&lt;FluidResource&gt;. Единицы у порта и Fabric общие
 * (droplets, ведро = 81000), поэтому количества проходят как есть; здесь проверяем
 * это и поведение на границах индексов.
 * <p>
 * ЧЕГО ЗДЕСЬ НЕТ И ПОЧЕМУ: настоящий FluidVariant в тестах не создать — в loader'е
 * Fabric патчит {@code Fluid} до {@code FluidVariantCache}, а вне рантайма это
 * ClassCastException. Поэтому все ветки, где нужен непустой вариант (реальный
 * insert/extract через слой Fabric), помечены {@code @Disabled} ниже.
 */
class StorageFluidHandlerTest {

    @BeforeAll
    static void setUp() {
        GameBootstrap.ensure();
    }

    @Test
    @DisplayName("size(): у SlottedStorage — число слотов")
    void sizeOfSlottedStorage() {
        var handler = new StorageFluidHandler(FakeSlottedStorage.of(FakeSlot.blank(81000), FakeSlot.blank(81000)));

        assertThat(handler.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("size(): у обычного Storage (без слотов) — 1")
    void sizeOfPlainStorage() {
        var handler = new StorageFluidHandler(new FakeStorage(FakeSlot.blank(81000)));

        assertThat(handler.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("пустой слот: пустой ресурс и нулевое количество")
    void blankSlot() {
        var handler = new StorageFluidHandler(FakeSlottedStorage.of(FakeSlot.blank(81000)));

        assertThat(handler.getResource(0).isEmpty()).isTrue();
        assertThat(handler.getResource(0)).isEqualTo(FluidResource.EMPTY);
        assertThat(handler.getAmountAsLong(0)).isZero();
    }

    @Test
    @DisplayName("индекс вне диапазона: не падает, отдаёт пустоту и нули")
    void outOfRangeIndex() {
        var handler = new StorageFluidHandler(FakeSlottedStorage.of(FakeSlot.blank(81000)));

        for (int index : new int[] { -1, 1, 99 }) {
            assertThat(handler.getResource(index).isEmpty()).as("индекс %d", index).isTrue();
            assertThat(handler.getAmountAsLong(index)).as("индекс %d", index).isZero();
            assertThat(handler.getCapacityAsLong(index, FluidResource.EMPTY)).as("индекс %d", index).isZero();
        }
    }

    @Test
    @DisplayName("обычный Storage игнорирует индекс и всегда отдаёт нулевой вид")
    void plainStorageIgnoresIndex() {
        // ФАКТИЧЕСКОЕ поведение шима (не NeoForge-контракт): для не-slotted Storage
        // view(index) всегда возвращает views.get(0). ResourceHandler в NeoForge обычно
        // бросает на выходе за границы, здесь — молча отдаёт первый слот.
        var handler = new StorageFluidHandler(new FakeStorage(FakeSlot.filled(40500, 81000)));

        assertThat(handler.getAmountAsLong(0)).isEqualTo(40500);
        assertThat(handler.getAmountAsLong(99)).isEqualTo(40500);
    }

    @Test
    @DisplayName("amount: единицы общие — 81000 droplets так и остаются 81000")
    void amountPassesThrough() {
        var handler = new StorageFluidHandler(FakeSlottedStorage.of(FakeSlot.filled(81000, 81000)));

        assertThat(handler.getAmountAsLong(0)).isEqualTo(81000);
    }

    @Test
    @DisplayName("amount: остатки droplets не округляются и не сгорают")
    void amountKeepsRemainders() {
        var halfBucket = new StorageFluidHandler(FakeSlottedStorage.of(FakeSlot.filled(40500, 81000)));
        var almostOneNf = new StorageFluidHandler(FakeSlottedStorage.of(FakeSlot.filled(404, 81000)));

        assertThat(halfBucket.getAmountAsLong(0)).isEqualTo(40500);
        assertThat(almostOneNf.getAmountAsLong(0)).isEqualTo(404);
    }

    @Test
    @DisplayName("capacity: тоже в droplets, как есть")
    void capacityPassesThrough() {
        var bucket = new StorageFluidHandler(FakeSlottedStorage.of(FakeSlot.blank(81000)));
        var odd = new StorageFluidHandler(FakeSlottedStorage.of(FakeSlot.blank(100000)));

        assertThat(bucket.getCapacityAsLong(0, FluidResource.EMPTY)).isEqualTo(81000);
        assertThat(odd.getCapacityAsLong(0, FluidResource.EMPTY)).isEqualTo(100000);
    }

    @Test
    @DisplayName("isValid: пустой ресурс — нельзя, непустой — можно (без обращения к стору)")
    void isValid() {
        var handler = new StorageFluidHandler(FakeSlottedStorage.of(FakeSlot.blank(81000)));

        assertThat(handler.isValid(0, FluidResource.EMPTY)).isFalse();
        // ВАЖНО: шим отвечает "да" на любой непустой ресурс и не спрашивает сам стор.
        assertThat(handler.isValid(0, FluidResource.of(Fluids.WATER))).isTrue();
        assertThat(handler.isValid(99, FluidResource.of(Fluids.WATER))).isTrue();
    }

    @Test
    @DisplayName("insert/extract с нулевым или отрицательным количеством — no-op без похода в стор")
    void zeroAndNegativeAmountsAreNoOps() {
        var storage = FakeSlottedStorage.of(FakeSlot.blank(81000));
        var handler = new StorageFluidHandler(storage);
        var water = FluidResource.of(Fluids.WATER);

        try (Transaction tx = Transaction.openRoot()) {
            assertThat(handler.insert(0, water, 0, tx)).isZero();
            // Договорённость NeoForge: отрицательное количество = IllegalArgumentException.
            // Шим молча отдаёт 0 — фактическое поведение, расхождение отмечено в отчёте.
            assertThat(handler.insert(0, water, -5, tx)).isZero();
            assertThat(handler.extract(0, water, 0, tx)).isZero();
            assertThat(handler.extract(0, water, -5, tx)).isZero();
        }

        assertThat(storage.insertCalls).isZero();
        assertThat(storage.extractCalls).isZero();
    }

    @Test
    @DisplayName("insert/extract пустого ресурса — no-op")
    void emptyResourceIsNoOp() {
        var storage = FakeSlottedStorage.of(FakeSlot.blank(81000));
        var handler = new StorageFluidHandler(storage);

        try (Transaction tx = Transaction.openRoot()) {
            assertThat(handler.insert(0, FluidResource.EMPTY, 1000, tx)).isZero();
            assertThat(handler.extract(0, FluidResource.EMPTY, 1000, tx)).isZero();
        }

        assertThat(storage.insertCalls).isZero();
        assertThat(storage.extractCalls).isZero();
    }

    @Test
    @DisplayName("ofSlot: одиночный слот = хендлер из одного индекса")
    void ofSlot() {
        var handler = StorageFluidHandler.ofSlot(FakeSlot.filled(81000, 81000));

        assertThat(handler.size()).isEqualTo(1);
        assertThat(handler.getAmountAsLong(0)).isEqualTo(81000);
    }

    @Test
    @DisplayName("storage() возвращает тот же объект, что обернули")
    void storageAccessor() {
        var storage = FakeSlottedStorage.of(FakeSlot.blank(81000));

        assertThat(new StorageFluidHandler(storage).storage()).isSameAs(storage);
    }

    @Test
    @org.junit.jupiter.api.Disabled("Нужен Fabric-рантайм: FluidVariant.of() падает ClassCastException "
            + "(Fluid не реализует FluidVariantCache без loader'а), поэтому insert реальной жидкости не проверить")
    @DisplayName("insert реальной жидкости через слой Fabric")
    void insertRealFluid() {
        var storage = FakeSlottedStorage.of(FakeSlot.blank(81000));
        var handler = new StorageFluidHandler(storage);

        try (Transaction tx = Transaction.openRoot()) {
            handler.insert(0, FluidResource.of(Fluids.WATER), 1000, tx);
            tx.commit();
        }

        assertThat(storage.insertCalls).isEqualTo(1);
    }

    /** Слот фейкового Fabric-танка. Количества — в единицах Fabric (81000 = ведро). */
    static final class FakeSlot implements SingleSlotStorage<FluidVariant> {

        private final long amount;
        private final long capacity;
        private final boolean blank;

        private FakeSlot(long amount, long capacity, boolean blank) {
            this.amount = amount;
            this.capacity = capacity;
            this.blank = blank;
        }

        static FakeSlot blank(long capacity) {
            return new FakeSlot(0, capacity, true);
        }

        static FakeSlot filled(long amount, long capacity) {
            return new FakeSlot(amount, capacity, false);
        }

        @Override
        public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            throw new AssertionError("insert не должен доходить до Fabric-слоя в этих тестах");
        }

        @Override
        public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            throw new AssertionError("extract не должен доходить до Fabric-слоя в этих тестах");
        }

        @Override
        public boolean isResourceBlank() {
            return blank;
        }

        @Override
        public FluidVariant getResource() {
            throw new AssertionError("FluidVariant недоступен вне Fabric-рантайма");
        }

        @Override
        public long getAmount() {
            return amount;
        }

        @Override
        public long getCapacity() {
            return capacity;
        }
    }

    /** SlottedStorage с фиксированным набором слотов. */
    static final class FakeSlottedStorage implements SlottedStorage<FluidVariant> {

        private final List<SingleSlotStorage<FluidVariant>> slots;
        int insertCalls;
        int extractCalls;

        private FakeSlottedStorage(List<SingleSlotStorage<FluidVariant>> slots) {
            this.slots = slots;
        }

        @SafeVarargs
        static FakeSlottedStorage of(SingleSlotStorage<FluidVariant>... slots) {
            return new FakeSlottedStorage(List.of(slots));
        }

        @Override
        public int getSlotCount() {
            return slots.size();
        }

        @Override
        public SingleSlotStorage<FluidVariant> getSlot(int slot) {
            return slots.get(slot);
        }

        @Override
        public Iterator<StorageView<FluidVariant>> iterator() {
            List<StorageView<FluidVariant>> views = new ArrayList<>(slots);
            return views.iterator();
        }

        @Override
        public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            insertCalls++;
            return 0;
        }

        @Override
        public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            extractCalls++;
            return 0;
        }
    }

    /** Storage без слотов: единственный вид отдаётся через iterator. */
    static final class FakeStorage implements Storage<FluidVariant> {

        private final SingleSlotStorage<FluidVariant> view;

        FakeStorage(SingleSlotStorage<FluidVariant> view) {
            this.view = view;
        }

        @Override
        public Iterator<StorageView<FluidVariant>> iterator() {
            return List.<StorageView<FluidVariant>>of(view).iterator();
        }

        @Override
        public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            return 0;
        }
    }
}

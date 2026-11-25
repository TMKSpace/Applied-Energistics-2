package nfcompat.bridge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import nfcompat.GameBootstrap;

/**
 * FluidHandlerStorage: обратное направление моста — NeoForge-ный
 * ResourceHandler&lt;FluidResource&gt; выглядит как Fabric-ный SlottedStorage&lt;FluidVariant&gt;.
 * <p>
 * Проверяем то, что не требует варианта жидкости: количество слотов, передачу
 * количеств/ёмкостей NF -> Fabric (теперь как есть, шкала общая), "пустоту" слота,
 * no-op на пустых входах и версию. Транзакции и реальные вставки см. {@code @Disabled} внизу.
 */
class FluidHandlerStorageTest {

    @BeforeAll
    static void setUp() {
        GameBootstrap.ensure();
    }

    @Test
    @DisplayName("getSlotCount() = size() хендлера")
    void slotCount() {
        assertThat(new FluidHandlerStorage(FakeHandler.withSlots(3)).getSlotCount()).isEqualTo(3);
        assertThat(new FluidHandlerStorage(FakeHandler.withSlots(0)).getSlotCount()).isZero();
    }

    @Test
    @DisplayName("amount: единицы общие (81000 = ведро) — проходит как есть")
    void amountPassesThrough() {
        var storage = new FluidHandlerStorage(FakeHandler.filled(81000, 81000));

        assertThat(storage.getSlot(0).getAmount()).isEqualTo(81000);
    }

    @Test
    @DisplayName("amount: насыщение на Long.MAX_VALUE, а не переполнение")
    void amountSaturates() {
        var storage = new FluidHandlerStorage(FakeHandler.filled(Long.MAX_VALUE, Long.MAX_VALUE));

        assertThat(storage.getSlot(0).getAmount()).isEqualTo(Long.MAX_VALUE);
        assertThat(storage.getSlot(0).getCapacity()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    @DisplayName("capacity: тоже в droplets, как есть")
    void capacityPassesThrough() {
        var storage = new FluidHandlerStorage(FakeHandler.filled(0, 2000));

        assertThat(storage.getSlot(0).getCapacity()).isEqualTo(2000);
    }

    @Test
    @DisplayName("isResourceBlank отражает пустоту ресурса в хендлере")
    void resourceBlank() {
        var empty = new FluidHandlerStorage(FakeHandler.withResource(FluidResource.EMPTY));
        var water = new FluidHandlerStorage(FakeHandler.withResource(FluidResource.of(Fluids.WATER)));

        assertThat(empty.getSlot(0).isResourceBlank()).isTrue();
        assertThat(water.getSlot(0).isResourceBlank()).isFalse();
    }

    @Test
    @DisplayName("insert/extract с null-вариантом — 0, хендлер не дёргается")
    void nullVariantIsNoOp() {
        var fake = FakeHandler.withSlots(1);
        var storage = new FluidHandlerStorage(fake);

        try (var tx = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
            assertThat(storage.insert(null, 1000, tx)).isZero();
            assertThat(storage.extract(null, 1000, tx)).isZero();
            assertThat(storage.getSlot(0).insert(null, 1000, tx)).isZero();
            assertThat(storage.getSlot(0).extract(null, 1000, tx)).isZero();
        }

        assertThat(fake.insertCalls).isZero();
        assertThat(fake.extractCalls).isZero();
    }

    @Test
    @DisplayName("iterator(): по одному виду на слот")
    void iteratorYieldsViews() {
        var storage = new FluidHandlerStorage(FakeHandler.withSlots(3));

        int count = 0;
        for (var view : storage) {
            count++;
            // getResource() зовём только через isResourceBlank(): сам вариант
            // требует Fabric-рантайма (см. @Disabled ниже)
            assertThat(view.isResourceBlank()).isTrue();
        }

        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("версия стартует с нуля (инкремент — только на реальных операциях)")
    void versionStartsAtZero() {
        assertThat(new FluidHandlerStorage(FakeHandler.withSlots(1)).getVersion()).isZero();
    }

    @Test
    @DisplayName("handler() возвращает обёрнутый хендлер")
    void handlerAccessor() {
        var fake = FakeHandler.withSlots(1);

        assertThat(new FluidHandlerStorage(fake).handler()).isSameAs(fake);
    }

    @Test
    @DisplayName("getSlot() вне диапазона падает уже при создании слота (индекс проверяется лениво)")
    void slotOutOfRangeFailsLazily() {
        var storage = new FluidHandlerStorage(FakeHandler.withSlots(1));

        // Слот создаётся для любого индекса, но обращение к данным уходит в хендлер,
        // который у нас бросает IndexOutOfBounds — так видно, что шим не валидирует индекс.
        assertThat(storage.getSlotCount()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> storage.getSlot(5).getAmount())
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    @org.junit.jupiter.api.Disabled("Нужен Fabric-рантайм: FluidStacks.toVariant -> FluidVariant.of() "
            + "падает ClassCastException (Fluid не реализует FluidVariantCache без loader'а)")
    @DisplayName("getResource() слота отдаёт вариант жидкости")
    void slotResource() {
        var storage = new FluidHandlerStorage(FakeHandler.withResource(FluidResource.of(Fluids.WATER)));

        assertThat(storage.getSlot(0).getResource().isBlank()).isFalse();
    }

    /** Хендлер-пустышка: отдаёт заданные значения и считает обращения. */
    static final class FakeHandler implements ResourceHandler<FluidResource> {

        private final int size;
        private final FluidResource resource;
        private final long amount;
        private final long capacity;
        int insertCalls;
        int extractCalls;

        private FakeHandler(int size, FluidResource resource, long amount, long capacity) {
            this.size = size;
            this.resource = resource;
            this.amount = amount;
            this.capacity = capacity;
        }

        static FakeHandler withSlots(int size) {
            return new FakeHandler(size, FluidResource.EMPTY, 0, 0);
        }

        static FakeHandler withResource(FluidResource resource) {
            return new FakeHandler(1, resource, 0, 0);
        }

        static FakeHandler filled(long amount, long capacity) {
            return new FakeHandler(1, FluidResource.of(Fluids.WATER), amount, capacity);
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public FluidResource getResource(int index) {
            checkIndex(index);
            return resource;
        }

        @Override
        public long getAmountAsLong(int index) {
            checkIndex(index);
            return amount;
        }

        @Override
        public long getCapacityAsLong(int index, FluidResource resource) {
            checkIndex(index);
            return capacity;
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            checkIndex(index);
            return !resource.isEmpty();
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            checkIndex(index);
            insertCalls++;
            return 0;
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            checkIndex(index);
            extractCalls++;
            return 0;
        }

        private void checkIndex(int index) {
            if (index < 0 || index >= size) {
                throw new IndexOutOfBoundsException("index " + index + " of " + size);
            }
        }
    }
}

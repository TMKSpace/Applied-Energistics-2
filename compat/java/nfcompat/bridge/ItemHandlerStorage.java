/*
 * Часть nfcompat. OUTBOUND: NeoForge-ный `ResourceHandler<ItemResource>` (ME-сундук,
 * диск-драйв, интерфейс, конденсатор, шины/части на CABLE_BUS) предъявляется как
 * Fabric-ный `Storage<ItemVariant>`, чтобы воронки, трубы и машины Fabric-модов
 * могли класть предметы в AE2 и забирать их оттуда.
 *
 * Почему `SlottedStorage`, а не просто `Storage`: часть Fabric-модов (и наш собственный
 * `StorageItemHandler` в обратную сторону) ходит по слотам через `getSlotCount`/`getSlot`.
 * Отдаём полноценный поштучный вид: каждый индекс — отдельный `SingleSlotStorage`.
 *
 * Единицы: 1 предмет = 1 штука и там, и там, пересчёта нет. Типы количеств разные
 * (`long` у Fabric, `int` у шима), поэтому запрос больше `Integer.MAX_VALUE` срезается.
 *
 * Транзакции — см. HandlerTransactions: NeoForge-ная root-транзакция открывается внутри
 * и привязывается к Fabric-ной, чтобы симуляция/откат и commit работали в обе стороны.
 */
package nfcompat.bridge;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

public final class ItemHandlerStorage implements SlottedStorage<ItemVariant> {

    private final ResourceHandler<ItemResource> handler;
    private final AtomicLong version = new AtomicLong();

    public ItemHandlerStorage(ResourceHandler<ItemResource> handler) {
        this.handler = handler;
    }

    public ResourceHandler<ItemResource> handler() {
        return handler;
    }

    @Override
    public int getSlotCount() {
        return handler.size();
    }

    @Override
    public SingleSlotStorage<ItemVariant> getSlot(int index) {
        return new Slot(index);
    }

    @Override
    public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        if (resource == null || resource.isBlank() || maxAmount <= 0) {
            return 0;
        }
        ItemResource nfResource = toResource(resource);
        if (nfResource.isEmpty()) {
            return 0;
        }
        int max = (int) Math.min(maxAmount, Integer.MAX_VALUE);
        int inserted = HandlerTransactions.run(transaction, max,
                nfTx -> handler.insert(nfResource, max, nfTx));
        if (inserted != 0) {
            version.incrementAndGet();
        }
        return inserted;
    }

    @Override
    public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        if (resource == null || resource.isBlank() || maxAmount <= 0) {
            return 0;
        }
        ItemResource nfResource = toResource(resource);
        if (nfResource.isEmpty()) {
            return 0;
        }
        int max = (int) Math.min(maxAmount, Integer.MAX_VALUE);
        int extracted = HandlerTransactions.run(transaction, max,
                nfTx -> handler.extract(nfResource, max, nfTx));
        if (extracted != 0) {
            version.incrementAndGet();
        }
        return extracted;
    }

    @Override
    public Iterator<StorageView<ItemVariant>> iterator() {
        List<StorageView<ItemVariant>> views = new ArrayList<>(handler.size());
        for (int i = 0; i < handler.size(); i++) {
            views.add(new Slot(i));
        }
        return views.iterator();
    }

    @Override
    public long getVersion() {
        return version.get();
    }

    private static ItemResource toResource(ItemVariant variant) {
        return ItemResource.of(variant.getItem(), variant.getComponentsPatch());
    }

    private final class Slot implements SingleSlotStorage<ItemVariant> {
        private final int index;

        private Slot(int index) {
            this.index = index;
        }

        @Override
        public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            if (resource == null || resource.isBlank() || maxAmount <= 0) {
                return 0;
            }
            ItemResource nfResource = toResource(resource);
            if (nfResource.isEmpty()) {
                return 0;
            }
            int max = (int) Math.min(maxAmount, Integer.MAX_VALUE);
            int inserted = HandlerTransactions.run(transaction, max,
                    nfTx -> handler.insert(index, nfResource, max, nfTx));
            if (inserted != 0) {
                version.incrementAndGet();
            }
            return inserted;
        }

        @Override
        public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            if (resource == null || resource.isBlank() || maxAmount <= 0) {
                return 0;
            }
            ItemResource nfResource = toResource(resource);
            if (nfResource.isEmpty()) {
                return 0;
            }
            int max = (int) Math.min(maxAmount, Integer.MAX_VALUE);
            int extracted = HandlerTransactions.run(transaction, max,
                    nfTx -> handler.extract(index, nfResource, max, nfTx));
            if (extracted != 0) {
                version.incrementAndGet();
            }
            return extracted;
        }

        @Override
        public boolean isResourceBlank() {
            return handler.getResource(index).isEmpty();
        }

        @Override
        public ItemVariant getResource() {
            ItemResource resource = handler.getResource(index);
            return resource.isEmpty()
                    ? ItemVariant.blank()
                    : ItemVariant.of(resource.getItem(), resource.getComponentsPatch());
        }

        @Override
        public long getAmount() {
            return Math.max(0, handler.getAmountAsLong(index));
        }

        @Override
        public long getCapacity() {
            return Math.max(0, handler.getCapacityAsLong(index, handler.getResource(index)));
        }
    }
}

/*
 * Часть nfcompat: показываем AE2-шный ResourceHandler<FluidResource> как Fabric-ный
 * Storage<FluidVariant>, чтобы другие Fabric-моды (трубы, танки) могли качать
 * жидкость в AE2 и из AE2.
 *
 * Транзакции: Fabric-транзакция открыта снаружи, поэтому NeoForge-ную мы открываем
 * внутри и привязываем к её закрытию — коммитнулась Fabric-ная, коммитится и наша;
 * иначе просто закрываем без коммита (откат).
 *
 * Единицы: NeoForge 1000/ведро, Fabric 81000/ведро — пересчёт в nfcompat.bridge.FluidUnits.
 */
package nfcompat.bridge;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

public final class FluidHandlerStorage implements SlottedStorage<FluidVariant> {

    private final ResourceHandler<FluidResource> handler;
    private final AtomicLong version = new AtomicLong();

    public FluidHandlerStorage(ResourceHandler<FluidResource> handler) {
        this.handler = handler;
    }

    public ResourceHandler<FluidResource> handler() {
        return handler;
    }

    @Override
    public int getSlotCount() {
        return handler.size();
    }

    @Override
    public SingleSlotStorage<FluidVariant> getSlot(int index) {
        return new Slot(index);
    }

    @Override
    public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        if (resource == null || resource.isBlank() || maxAmount <= 0) {
            return 0;
        }
        FluidResource nfResource = FluidResource.of(resource.getObject(), resource.getComponentsPatch());
        int maxNf = FluidUnits.toNf(maxAmount);
        if (maxNf <= 0) {
            return 0;
        }
        Transaction nfTx = Transaction.openRoot();
        bindRollback(nfTx, transaction);
        int inserted = handler.insert(nfResource, maxNf, nfTx);
        version.incrementAndGet();
        return FluidUnits.toFabric(inserted);
    }

    @Override
    public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        if (resource == null || resource.isBlank() || maxAmount <= 0) {
            return 0;
        }
        FluidResource nfResource = FluidResource.of(resource.getObject(), resource.getComponentsPatch());
        int maxNf = FluidUnits.toNf(maxAmount);
        if (maxNf <= 0) {
            return 0;
        }
        Transaction nfTx = Transaction.openRoot();
        bindRollback(nfTx, transaction);
        int extracted = handler.extract(nfResource, maxNf, nfTx);
        version.incrementAndGet();
        return FluidUnits.toFabric(extracted);
    }

    @Override
    public Iterator<StorageView<FluidVariant>> iterator() {
        List<StorageView<FluidVariant>> views = new ArrayList<>(handler.size());
        for (int i = 0; i < handler.size(); i++) {
            views.add(new Slot(i));
        }
        return views.iterator();
    }

    @Override
    public long getVersion() {
        return version.get();
    }

    /**
     * Привязка NeoForge-ной транзакции к внешней Fabric-ной:
     * закрылась Fabric-ная — коммитим только если она закоммичена,
     * иначе закрываем NF-ную без коммита (это и есть откат).
     */
    private static void bindRollback(Transaction nfTx, TransactionContext fabricTx) {
        fabricTx.addCloseCallback((ctx, result) -> {
            if (result.wasCommitted()) {
                nfTx.commit();
            }
            nfTx.close();
        });
    }

    private final class Slot implements SingleSlotStorage<FluidVariant> {
        private final int index;

        private Slot(int index) {
            this.index = index;
        }

        @Override
        public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            if (resource == null || resource.isBlank() || maxAmount <= 0) {
                return 0;
            }
            FluidResource nfResource = FluidResource.of(resource.getObject(), resource.getComponentsPatch());
            int maxNf = FluidUnits.toNf(maxAmount);
            if (maxNf <= 0) {
                return 0;
            }
            Transaction nfTx = Transaction.openRoot();
            bindRollback(nfTx, transaction);
            int inserted = handler.insert(index, nfResource, maxNf, nfTx);
            version.incrementAndGet();
            return FluidUnits.toFabric(inserted);
        }

        @Override
        public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            if (resource == null || resource.isBlank() || maxAmount <= 0) {
                return 0;
            }
            FluidResource nfResource = FluidResource.of(resource.getObject(), resource.getComponentsPatch());
            int maxNf = FluidUnits.toNf(maxAmount);
            if (maxNf <= 0) {
                return 0;
            }
            Transaction nfTx = Transaction.openRoot();
            bindRollback(nfTx, transaction);
            int extracted = handler.extract(index, nfResource, maxNf, nfTx);
            version.incrementAndGet();
            return FluidUnits.toFabric(extracted);
        }

        @Override
        public boolean isResourceBlank() {
            return handler.getResource(index).isEmpty();
        }

        @Override
        public FluidVariant getResource() {
            return FluidStacks.toVariant(handler.getResource(index));
        }

        @Override
        public long getAmount() {
            return FluidUnits.toFabricSaturated(handler.getAmountAsLong(index));
        }

        @Override
        public long getCapacity() {
            FluidResource current = handler.getResource(index);
            return FluidUnits.toFabricSaturated(handler.getCapacityAsLong(index, current));
        }
    }
}

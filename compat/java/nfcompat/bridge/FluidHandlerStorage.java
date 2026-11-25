/*
 * Часть nfcompat. OUTBOUND: NeoForge-ный `ResourceHandler<FluidResource>` (ME-сундук,
 * конденсатор, sky stone tank, fluid-P2P-туннель) предъявляется как Fabric-ный
 * `SlottedStorage<FluidVariant>`, чтобы трубы и танки Fabric-модов могли качать
 * жидкость в AE2 и из AE2.
 *
 * Единицы: шкала одна (droplets, ведро = 81000), пересчёта нет — всё идёт через
 * nfcompat.bridge.FluidUnits, оставленный identity-обёрткой.
 *
 * Транзакции — см. HandlerTransactions: NeoForge-ная root-транзакция открывается внутри
 * и привязывается к Fabric-ной, чтобы симуляция/откат и commit работали в обе стороны.
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
        FluidResource nfResource = FluidStacks.toResource(resource);
        if (nfResource.isEmpty()) {
            return 0;
        }
        int maxNf = FluidUnits.toNf(maxAmount);
        if (maxNf <= 0) {
            return 0;
        }
        int inserted = HandlerTransactions.run(transaction, maxNf,
                nfTx -> handler.insert(nfResource, maxNf, nfTx));
        if (inserted != 0) {
            version.incrementAndGet();
        }
        return FluidUnits.toFabric(inserted);
    }

    @Override
    public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        if (resource == null || resource.isBlank() || maxAmount <= 0) {
            return 0;
        }
        FluidResource nfResource = FluidStacks.toResource(resource);
        if (nfResource.isEmpty()) {
            return 0;
        }
        int maxNf = FluidUnits.toNf(maxAmount);
        if (maxNf <= 0) {
            return 0;
        }
        int extracted = HandlerTransactions.run(transaction, maxNf,
                nfTx -> handler.extract(nfResource, maxNf, nfTx));
        if (extracted != 0) {
            version.incrementAndGet();
        }
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
            FluidResource nfResource = FluidStacks.toResource(resource);
            if (nfResource.isEmpty()) {
                return 0;
            }
            int maxNf = FluidUnits.toNf(maxAmount);
            if (maxNf <= 0) {
                return 0;
            }
            int inserted = HandlerTransactions.run(transaction, maxNf,
                    nfTx -> handler.insert(index, nfResource, maxNf, nfTx));
            if (inserted != 0) {
                version.incrementAndGet();
            }
            return FluidUnits.toFabric(inserted);
        }

        @Override
        public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            if (resource == null || resource.isBlank() || maxAmount <= 0) {
                return 0;
            }
            FluidResource nfResource = FluidStacks.toResource(resource);
            if (nfResource.isEmpty()) {
                return 0;
            }
            int maxNf = FluidUnits.toNf(maxAmount);
            if (maxNf <= 0) {
                return 0;
            }
            int extracted = HandlerTransactions.run(transaction, maxNf,
                    nfTx -> handler.extract(index, nfResource, maxNf, nfTx));
            if (extracted != 0) {
                version.incrementAndGet();
            }
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

/*
 * Часть nfcompat: обратная сторона моста — предъявляем Fabric-ный Storage<FluidVariant>
 * (танк другого мода) как NeoForge-ный ResourceHandler<FluidResource>, которого ждёт AE2.
 *
 * Транзакции: NeoForge-ная транзакция (её держит AE2 и передаёт в insert/extract)
 * привязывается к Fabric-ной через HandlerTransactions.runOntoFabric, поэтому
 * `Actionable.SIMULATE` больше не «протекает»: без commit'а Fabric-ная сторона
 * откатывается. Раньше здесь был openOuter + немедленный commit — то есть симуляция
 * реально опустошала ведро/бак (SIMULATE-дыра).
 */
package nfcompat.bridge;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

public final class StorageFluidHandler implements ResourceHandler<FluidResource> {

    private final Storage<FluidVariant> storage;

    public StorageFluidHandler(Storage<FluidVariant> storage) {
        this.storage = storage;
    }

    public Storage<FluidVariant> storage() {
        return storage;
    }

    @Override
    public int size() {
        if (storage instanceof SlottedStorage<?> slotted) {
            return slotted.getSlotCount();
        }
        return 1;
    }

    @Override
    public FluidResource getResource(int index) {
        StorageView<FluidVariant> view = view(index);
        if (view == null || view.isResourceBlank()) {
            return FluidResource.EMPTY;
        }
        return FluidResource.of(view.getResource().getFluid(), view.getResource().getComponentsPatch());
    }

    @Override
    public long getAmountAsLong(int index) {
        StorageView<FluidVariant> view = view(index);
        return view == null ? 0 : FluidUnits.toNfLong(view.getAmount());
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        StorageView<FluidVariant> view = view(index);
        return view == null ? 0 : FluidUnits.toNfLong(view.getCapacity());
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return !resource.isEmpty();
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        if (resource.isEmpty() || amount <= 0) {
            return 0;
        }
        FluidVariant variant = FluidStacks.toVariant(resource);
        long maxFabric = FluidUnits.toFabric(amount);
        return HandlerTransactions.runOntoFabric(transaction, amount,
                fabricTx -> FluidUnits.toNf(storage.insert(variant, maxFabric, fabricTx)));
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        if (resource.isEmpty() || amount <= 0) {
            return 0;
        }
        FluidVariant variant = FluidStacks.toVariant(resource);
        long maxFabric = FluidUnits.toFabric(amount);
        int extracted = HandlerTransactions.runOntoFabric(transaction, amount,
                fabricTx -> FluidUnits.toNf(storage.extract(variant, maxFabric, fabricTx)));
        return extracted;
    }

    private StorageView<FluidVariant> view(int index) {        if (storage instanceof SlottedStorage<?> slotted) {
            if (index < 0 || index >= slotted.getSlotCount()) {
                return null;
            }
            return (StorageView<FluidVariant>) slotted.getSlot(index);
        }
        List<StorageView<FluidVariant>> views = views();
        return views.isEmpty() ? null : views.get(0);
    }

    private List<StorageView<FluidVariant>> views() {
        List<StorageView<FluidVariant>> result = new ArrayList<>();
        for (StorageView<FluidVariant> view : storage) {
            result.add(view);
        }
        return result;
    }

    /** Удобно: обернуть одиночный слот как ресурсный хендлер из одного индекса. */
    public static ResourceHandler<FluidResource> ofSlot(SingleSlotStorage<FluidVariant> slot) {
        return new StorageFluidHandler(slot);
    }
}

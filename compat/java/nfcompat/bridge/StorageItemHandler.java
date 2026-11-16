/*
 * Часть nfcompat: предъявляем Fabric-ный Storage<ItemVariant> (сундук, печь, бак мода)
 * как NeoForge-ный ResourceHandler<ItemResource>, которого ждёт AE2.
 *
 * Симметрично nfcompat.bridge.StorageFluidHandler; отличие только в том, что у предметов
 * нет пересчёта единиц (1 предмет = 1 штука и там, и там), а жидкости расходятся
 * в 81 раз (ведро 1000 у NeoForge против 81000 у Fabric) — см. FluidUnits.
 *
 * Транзакции — через HandlerTransactions.runOntoFabric: SIMULATE больше не «протекает»
 * (раньше здесь был openOuter + немедленный commit).
 */
package nfcompat.bridge;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.neoforged.neoforge.transfer.ResourceHandler;import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

public final class StorageItemHandler implements ResourceHandler<ItemResource> {

    private final Storage<ItemVariant> storage;

    public StorageItemHandler(Storage<ItemVariant> storage) {
        this.storage = storage;
    }

    public Storage<ItemVariant> storage() {
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
    public ItemResource getResource(int index) {
        StorageView<ItemVariant> view = view(index);
        if (view == null || view.isResourceBlank()) {
            return ItemResource.EMPTY;
        }
        var variant = view.getResource();
        return ItemResource.of(variant.getItem(), variant.getComponentsPatch());
    }

    @Override
    public long getAmountAsLong(int index) {
        StorageView<ItemVariant> view = view(index);
        return view == null ? 0 : view.getAmount();
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        StorageView<ItemVariant> view = view(index);
        return view == null ? 0 : view.getCapacity();
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return !resource.isEmpty();
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        if (resource.isEmpty() || amount <= 0) {
            return 0;
        }
        ItemVariant variant = ItemVariant.of(resource.getItem(), resource.getComponentsPatch());
        return HandlerTransactions.runOntoFabric(transaction, amount,
                fabricTx -> (int) Math.min(storage.insert(variant, amount, fabricTx), amount));
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        if (resource.isEmpty() || amount <= 0) {
            return 0;
        }
        ItemVariant variant = ItemVariant.of(resource.getItem(), resource.getComponentsPatch());
        return HandlerTransactions.runOntoFabric(transaction, amount,
                fabricTx -> (int) Math.min(storage.extract(variant, amount, fabricTx), amount));
    }

    private StorageView<ItemVariant> view(int index) {
        if (storage instanceof SlottedStorage<?> slotted) {
            if (index < 0 || index >= slotted.getSlotCount()) {
                return null;
            }
            return (StorageView<ItemVariant>) slotted.getSlot(index);
        }
        List<StorageView<ItemVariant>> views = views();
        return views.isEmpty() ? null : views.get(0);
    }

    private List<StorageView<ItemVariant>> views() {
        List<StorageView<ItemVariant>> result = new ArrayList<>();
        for (StorageView<ItemVariant> view : storage) {
            result.add(view);
        }
        return result;
    }

    /** Обернуть один слот как одноиндексный хендлер. */
    public static ResourceHandler<ItemResource> ofSlot(SingleSlotStorage<ItemVariant> slot) {
        return new StorageItemHandler(slot);
    }
}

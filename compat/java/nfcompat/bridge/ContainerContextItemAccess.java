/*
 * Часть nfcompat. Адаптер `ContainerItemContext` (Fabric) -> `ItemAccess` (шим).
 *
 * Нужен в обратную сторону от {@link ItemAccessContainerContext}: Fabric-ный мод
 * спрашивает `FluidStorage.ITEM.find(stack, context)` / `EnergyStorage.ITEM.find(...)`,
 * мы отдаём провайдер, который обязан позвать NeoForge-ный
 * `Capabilities.*.ITEM.getCapabilityDirect(stack, access)`, а провайдер AE2
 * (`PoweredItemCapabilities`) хочет именно `ItemAccess`.
 *
 * Обмен предмета (ведро -> ведро воды, разряженная ячейка -> заряженная) при этом
 * обязан попасть в Fabric-ную транзакцию потребителя. Транзакцию аргументом
 * `ItemAccess` не передаёт, поэтому берём её у {@link HandlerTransactions}: пока наш
 * адаптер выполняет операцию внутри `HandlerTransactions.run`, там лежит актуальный
 * Fabric-ный контекст (см. `currentFabricContext`). Если его нет (провайдер вернул
 * хендлер и его дёрнули вне нашего адаптера) — открываем свою outer-транзакцию и
 * сразу коммитим: отката через границу в этом случае всё равно не будет.
 *
 * Ограничение: NeoForge-ная вложенная транзакция (`ItemAccess.exchange` открывает
 * такую) при откате не откатывает уже слитое в Fabric-ную транзакцию изменение.
 * На практике это одна операция над одним стеком, поэтому расхождения не видно.
 */
package nfcompat.bridge;

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.item.ItemResource;

final class ContainerContextItemAccess implements ItemAccess {

    private final ContainerItemContext context;

    ContainerContextItemAccess(ContainerItemContext context) {
        this.context = context;
    }

    ContainerItemContext context() {
        return context;
    }

    @Override
    public ItemResource getResource() {
        ItemVariant variant = context.getItemVariant();
        if (variant.isBlank()) {
            return ItemResource.EMPTY;
        }
        return ItemResource.of(variant.getItem(), variant.getComponentsPatch());
    }

    @Override
    public int getAmount() {
        if (context.getItemVariant().isBlank()) {
            return 0;
        }
        return (int) Math.clamp(context.getAmount(), 0, Integer.MAX_VALUE);
    }

    @Override
    public int insert(ItemResource resource, int amount,
            net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
        if (resource.isEmpty() || amount <= 0) {
            return 0;
        }
        ItemVariant variant = toVariant(resource);
        TransactionContext fabricTx = HandlerTransactions.currentFabricContext();
        if (fabricTx != null) {
            return (int) Math.clamp(context.insert(variant, amount, fabricTx), 0, amount);
        }
        try (Transaction tx = Transaction.openOuter()) {
            int inserted = (int) Math.clamp(context.insert(variant, amount, tx), 0, amount);
            tx.commit();
            return inserted;
        }
    }

    @Override
    public int extract(ItemResource resource, int amount,
            net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
        if (resource.isEmpty() || amount <= 0) {
            return 0;
        }
        ItemVariant variant = toVariant(resource);
        TransactionContext fabricTx = HandlerTransactions.currentFabricContext();
        if (fabricTx != null) {
            return (int) Math.clamp(context.extract(variant, amount, fabricTx), 0, amount);
        }
        try (Transaction tx = Transaction.openOuter()) {
            int extracted = (int) Math.clamp(context.extract(variant, amount, tx), 0, amount);
            tx.commit();
            return extracted;
        }
    }

    private static ItemVariant toVariant(ItemResource resource) {
        return ItemVariant.of(resource.getItem(), resource.getComponentsPatch());
    }
}

/*
 * Часть nfcompat. Адаптер `net.neoforged.neoforge.transfer.access.ItemAccess` ->
 * `net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext`.
 *
 * Зачем: NeoForge-ные item-capability (`Capabilities.Fluid.ITEM`, `Item.ITEM`,
 * `Energy.ITEM`) получают контекст `ItemAccess` — «доступ к слоту, в котором лежит
 * предмет». Fabric-ные lookup'ы (`FluidStorage.ITEM.find(stack, context)`) ждут
 * `ContainerItemContext` — почти то же самое, но со своим набором операций над
 * слотами. Этот класс переводит одно в другое, чтобы чужие (и ванильные) Fabric-ные
 * провайдеры могли не только читать, но и ПОДМЕНЯТЬ предмет: пустое ведро ->
 * ведро воды и обратно.
 *
 * Почему это не тривиально:
 *  - у `ItemAccess` ОДИН логический слот (`getResource`/`getAmount`), у Fabric-ного
 *    контекста есть ещё «перелив» (insertOverflow) и дополнительные слоты. Перелив
 *    отдаём в тот же слот: `ItemAccess.insert` сам разбирается, куда девать остаток
 *    (в инвентарь игрока), а дополнительных слотов у нас нет;
 *  - транзакции. Fabric-ный провайдер зовёт `context.insert(..., fabricTx)`, а
 *    `ItemAccess.insert` требует NeoForge-ную. Переводим через
 *    {@link HandlerTransactions#run} — иначе откат/симуляция Fabric-ной стороны не
 *    доходили бы до предмета (и ведро «наполнялось» на SIMULATE);
 *  - `ItemAccess.forStack` (его использует P2P-аттюнмент и JEI) не умеет менять
 *    сам предмет — `insert` чужого предмета вернёт 0. Это ожидаемо: такой контекст
 *    только для чтения, наполнение ведра идёт через `forPlayerSlot`/`forPlayerCursor`.
 */
package nfcompat.bridge;

import java.util.List;

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.item.ItemResource;

final class ItemAccessContainerContext implements ContainerItemContext {

    private final ItemAccess access;
    private final Slot mainSlot = new Slot();

    ItemAccessContainerContext(ItemAccess access) {
        this.access = access;
    }

    @Override
    public SingleSlotStorage<ItemVariant> getMainSlot() {
        return mainSlot;
    }

    /**
     * Слот у {@link ItemAccess} один, поэтому «перелив» — это тот же слот: остаток
     * `ItemAccess` девает сам (в инвентарь игрока), а не мы.
     */
    @Override
    public long insertOverflow(ItemVariant itemVariant, long maxAmount, TransactionContext transaction) {
        return mainSlot.insert(itemVariant, maxAmount, transaction);
    }

    @Override
    public List<SingleSlotStorage<ItemVariant>> getAdditionalSlots() {
        return List.of();
    }

    @Override
    public String toString() {
        return "ItemAccessContainerContext[" + mainSlot.getAmount() + " " + mainSlot.getResource() + "]";
    }

    private final class Slot implements SingleSlotStorage<ItemVariant> {

        @Override
        public long insert(ItemVariant variant, long maxAmount, TransactionContext transaction) {
            if (variant == null || variant.isBlank() || maxAmount <= 0) {
                return 0;
            }
            ItemResource resource = toResource(variant);
            if (resource.isEmpty()) {
                return 0;
            }
            int max = (int) Math.min(maxAmount, Integer.MAX_VALUE);
            return HandlerTransactions.run(transaction, max, nfTx -> access.insert(resource, max, nfTx));
        }

        @Override
        public long extract(ItemVariant variant, long maxAmount, TransactionContext transaction) {
            if (variant == null || variant.isBlank() || maxAmount <= 0) {
                return 0;
            }
            ItemResource resource = toResource(variant);
            if (resource.isEmpty()) {
                return 0;
            }
            int max = (int) Math.min(maxAmount, Integer.MAX_VALUE);
            return HandlerTransactions.run(transaction, max, nfTx -> access.extract(resource, max, nfTx));
        }

        @Override
        public boolean isResourceBlank() {
            return access.getAmount() <= 0 || access.getResource().isEmpty();
        }

        @Override
        public ItemVariant getResource() {
            ItemResource resource = access.getResource();
            return resource.isEmpty() ? ItemVariant.blank() : toVariant(resource);
        }

        @Override
        public long getAmount() {
            return Math.max(0, access.getAmount());
        }

        @Override
        public long getCapacity() {
            ItemResource resource = access.getResource();
            // Один слот — это ровно один стек: сколько в него влезает, столько и ёмкость.
            return resource.isEmpty() ? 0 : resource.getMaxStackSize();
        }
    }

    private static ItemResource toResource(ItemVariant variant) {
        return ItemResource.of(variant.getItem(), variant.getComponentsPatch());
    }

    private static ItemVariant toVariant(ItemResource resource) {
        return ItemVariant.of(resource.getItem(), resource.getComponentsPatch());
    }
}

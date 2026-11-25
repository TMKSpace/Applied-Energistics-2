/*
 * Часть nfcompat: обратная сторона моста — предъявляем Fabric-ный Storage<FluidVariant>
 * (танк другого мода) как NeoForge-ный ResourceHandler<FluidResource>, которого ждёт AE2.
 *
 * Транзакции: NeoForge-ная транзакция (её держит AE2 и передаёт в insert/extract)
 * привязывается к Fabric-ной через HandlerTransactions.runOntoFabric, поэтому
 * `Actionable.SIMULATE` больше не «протекает»: без commit'а Fabric-ная сторона
 * откатывается. Раньше здесь был openOuter + немедленный commit — то есть симуляция
 * реально опустошала ведро/бак (SIMULATE-дыра).
 *
 * КВАНТ КОНТЕЙНЕРА (бутылка). Запрос в Fabric всегда `amount * 81` droplets, а отчёт
 * обратно — деление вниз. Для ведра (81000) это точно, а для бутылки нет: 27000
 * droplets = 333.33 mB, поэтому AE2 считает её за 333 mB, и повторный запрос на 333
 * даёт 26973 < 27000. Контейнеры «всё или ничего» (EmptyItemFluidStorage для
 * стеклянной бутылки, WaterPotionStorage для бутылки воды) на 26973 отвечают 0 —
 * получалось, что жидкость из сети уже списана по симуляции, а предмет не сменился.
 * Поэтому здесь: сначала точное число (как раньше), и только если контейнер отказал
 * ЦЕЛИКОМ — повтор с запасом меньше одного mB (см. {@link FluidUnits#PAD}).
 *
 * ОСТАТОК (см. {@link FluidCarry}). Отчёт о результате раньше всегда округлялся вниз
 * независимо от предыдущих операций, из-за чего каждая бутылка стоила сети 333 mB
 * вместо 333.33, и три бутылки списывали 999 mB. Теперь, если у доступа к предмету
 * есть игрок, число mB считает журнал переноса остатка: 333, 333, 334 = ровно 1000.
 * Без игрока (танки-блоки, ItemAccess.forStack) поведение прежнее — честное деление
 * вниз, остальное там не нужно: у блока нет «сессии игрока», к которой остаток можно
 * привязать.
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
import org.jetbrains.annotations.Nullable;

public final class StorageFluidHandler implements ResourceHandler<FluidResource> {

    private final Storage<FluidVariant> storage;
    /** Журнал переноса остатка или null, если привязывать его не к кому. */
    @Nullable
    private final FluidCarry carry;

    public StorageFluidHandler(Storage<FluidVariant> storage) {
        this(storage, null);
    }

    public StorageFluidHandler(Storage<FluidVariant> storage, @Nullable FluidCarry carry) {
        this.storage = storage;
        this.carry = carry;
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
                fabricTx -> reportInsert(insertWithPad(storage, variant, maxFabric, fabricTx), amount,
                        transaction));
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        if (resource.isEmpty() || amount <= 0) {
            return 0;
        }
        FluidVariant variant = FluidStacks.toVariant(resource);
        long maxFabric = FluidUnits.toFabric(amount);
        return HandlerTransactions.runOntoFabric(transaction, amount,
                fabricTx -> reportExtract(extractWithPad(storage, variant, maxFabric, fabricTx), amount,
                        transaction));
    }

    /** Отчёт о вставке: журнал остатка, если он есть, иначе прежнее деление вниз. */
    private int reportInsert(long droplets, int amount, TransactionContext transaction) {
        if (carry == null) {
            return FluidUnits.toNf(droplets);
        }
        return carry.reportInsert(transaction, droplets, amount);
    }

    /** Отчёт об извлечении: см. {@link #reportInsert}. */
    private int reportExtract(long droplets, int amount, TransactionContext transaction) {
        if (carry == null) {
            return FluidUnits.toNf(droplets);
        }
        return carry.reportExtract(transaction, droplets, amount);
    }

    /**
     * Fabric-ная вставка: сначала ровно столько, сколько попросили, и только при полном
     * отказе — повтор с запасом меньше одного mB (см. {@link FluidUnits#PAD}).
     *
     * Почему не «всегда с запасом»: обычный танк принимает и частично, и лишний запрос
     * позволил бы ему принять больше droplets, чем оплачено в mB (пусть журнал остатка
     * и выровнял бы это позже — лишний запрос каждый раз уводил бы долг вверх, а
     * кламп по запрошенному количеству не давал бы его вернуть). А контейнер-«всё или
     * ничего» на точный запрос отвечает нулём, так что повтор после нуля — единственный
     * случай, где запас что-то меняет.
     */
    private static long insertWithPad(Storage<FluidVariant> storage, FluidVariant variant, long maxFabric,
            net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext transaction) {
        long inserted = storage.insert(variant, maxFabric, transaction);
        if (inserted > 0) {
            return inserted;
        }
        return storage.insert(variant, FluidUnits.pad(maxFabric), transaction);
    }

    /**
     * То же для извлечения (бутылка воды отдаёт ровно 27000 droplets только если
     * разрешено взять не меньше этого, иначе возвращает 0 и не меняет предмет).
     */
    private static long extractWithPad(Storage<FluidVariant> storage, FluidVariant variant, long maxFabric,
            net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext transaction) {
        long extracted = storage.extract(variant, maxFabric, transaction);
        if (extracted > 0) {
            return extracted;
        }
        return storage.extract(variant, FluidUnits.pad(maxFabric), transaction);
    }

    private StorageView<FluidVariant> view(int index) {
        if (storage instanceof SlottedStorage<?> slotted) {
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

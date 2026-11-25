/*
 * Часть nfcompat: пересчёт единиц жидкости между NeoForge и Fabric.
 *
 * NeoForge: FluidType.BUCKET_VOLUME = 1000 (ведро = 1000)
 * Fabric  : FluidConstants.BUCKET   = 81000 (ведро = 81000, т.е. «капля/блок»)
 *
 * Канон внутри шима и AE2 — NeoForge-ный. На границе с Fabric Transfer API
 * обязательно туда-обратно умножать/делить на 81. Деление всегда с округлением
 * вниз: иначе можно «создать» жидкость из ничего.
 *
 * БУТЫЛКА. В Fabric бутылка — это FluidConstants.BOTTLE = 27000 droplets, то есть
 * 27000/81 = 333.33 mB: в NeoForge-ной шкале её объём не представим целым числом
 * (в NeoForge 26.3 нет и константы BOTTLE_VOLUME, только BUCKET_VOLUME). Ведро кратно
 * 81 и переживает пересчёт точно, а бутылка — нет: AE2 видит её как 333 mB, обратный
 * пересчёт даёт 333*81 = 26973 droplets < 27000, и контейнеры «всё или ничего»
 * (EmptyItemFluidStorage / WaterPotionStorage) отвечают отказом. На этом уже терялась
 * жидкость: по симуляции сеть списывала 333 mB, а предмет не менялся. Лечится не
 * округлением вверх (это «создание» жидкости), а запасом меньше одного mB — см. {@link #PAD}.
 */
package nfcompat.bridge;

public final class FluidUnits {
    /** Ведро в юнитах NeoForge. */
    public static final int NF_BUCKET = 1000;
    /** Ведро в юнитах Fabric Transfer API. */
    public static final long FABRIC_BUCKET = 81000;
    /**
     * Бутылка в юнитах Fabric Transfer API (треть ведра — как у ванильного котла:
     * BottleItem/CauldronInteraction отливают 1 из 3 уровней). В mB это 333.33.
     */
    public static final long FABRIC_BOTTLE = 27000;
    /** Множитель пересчёта NF -> Fabric. */
    public static final long SCALE = FABRIC_BUCKET / NF_BUCKET; // 81
    /**
     * Запас меньше одного mB (81-1 droplets) для запросов в Fabric.
     *
     * Нужен там, где запрос обязан «дотянуться» до кванта контейнера, не кратного mB
     * (бутылка: 333 mB = 26973 droplets, а ей нужно ровно 27000 — иначе «всё или ничего»
     * откажет). Жидкость при этом не создаётся: отчёт о результате всё равно округляется
     * вниз, а сам контейнер обрезает запрос по своему кванту.
     */
    public static final long PAD = SCALE - 1;

    private FluidUnits() {
    }

    public static long toFabric(long nfAmount) {
        return nfAmount * SCALE;
    }

    public static long toFabric(int nfAmount) {
        return (long) nfAmount * SCALE;
    }

    /** Fabric -> NF с насыщением в int (ResourceHandler.insert оперирует int). */
    public static int toNf(long fabricAmount) {
        long nf = fabricAmount / SCALE;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, nf));
    }

    /** Fabric -> NF без ограничения int (для getAmountAsLong). */
    public static long toNfLong(long fabricAmount) {
        return Math.max(0, fabricAmount / SCALE);
    }

    /** NF long -> Fabric, с защитой от переполнения. */
    public static long toFabricSaturated(long nfAmount) {
        return nfAmount > Long.MAX_VALUE / SCALE ? Long.MAX_VALUE : nfAmount * SCALE;
    }

    /**
     * Запрос в droplets с запасом {@link #PAD} (меньше одного mB), с насыщением.
     * Используется только как повторная попытка после отказа контейнера, см.
     * {@code StorageFluidHandler}.
     */
    public static long pad(long fabricAmount) {
        long saturated = Math.max(0, fabricAmount);
        return saturated > Long.MAX_VALUE - PAD ? Long.MAX_VALUE : saturated + PAD;
    }
}

/*
 * Часть nfcompat: единицы жидкости в порте.
 *
 * Раньше здесь был пересчёт NeoForge mB <-> Fabric droplets (ведро 1000 против 81000).
 * Теперь порт целиком живёт в Fabric-шкале droplets, как официальный 1.20.1-Fabric-порт
 * AE2: AEFluidKey.AMOUNT_BUCKET = FluidType.BUCKET_VOLUME = FluidConstants.BUCKET = 81000.
 * Конверсии нет — toFabric/toNf возвращают то же число, что получили. Класс оставлен
 * тонкой обёрткой, чтобы не переписывать все места вызова и не плодить диф.
 *
 * Побочный эффект: бутылка (FluidConstants.BOTTLE = 27000 droplets) теперь кратна шкале
 * нацело (треть ведра), поэтому «всё или ничего»-контейнеры (EmptyItemFluidStorage /
 * WaterPotionStorage) больше не нуждаются в запасе на округление — прежний PAD удалён
 * вместе со всей схемой «дотяжки».
 */
package nfcompat.bridge;

public final class FluidUnits {
    /** Ведро в юнитах шима — шкала теперь одна. */
    public static final int NF_BUCKET = 81000;
    /** Ведро в юнитах Fabric Transfer API (FluidConstants.BUCKET). */
    public static final long FABRIC_BUCKET = 81000;
    /**
     * Бутылка в юнитах Fabric Transfer API (треть ведра — как у ванильного котла:
     * BottleItem/CauldronInteraction отливают 1 из 3 уровней).
     */
    public static final long FABRIC_BOTTLE = 27000;
    /** Юниты одинаковые, поэтому множитель единичный (оставлен для читаемости). */
    public static final long SCALE = 1;

    private FluidUnits() {
    }

    /** Раньше — mB -> droplets; теперь тождественно. */
    public static long toFabric(long amount) {
        return amount;
    }

    /** Раньше — mB -> droplets; теперь тождественно. */
    public static long toFabric(int amount) {
        return amount;
    }

    /**
     * Раньше — droplets -> mB делением; теперь тождественно, но с приведением к int,
     * потому что {@code ResourceHandler.insert/extract} оперируют int.
     */
    public static int toNf(long amount) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, amount));
    }

    /** То же, но без ограничения int (для {@code getAmountAsLong}). */
    public static long toNfLong(long amount) {
        return Math.max(0, amount);
    }

    /** Раньше — с защитой от переполнения при умножении; теперь просто отсекаем минус. */
    public static long toFabricSaturated(long amount) {
        return Math.max(0, amount);
    }
}

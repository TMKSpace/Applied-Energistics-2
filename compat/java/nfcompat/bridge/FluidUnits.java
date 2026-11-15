/*
 * Часть nfcompat: пересчёт единиц жидкости между NeoForge и Fabric.
 *
 * NeoForge: FluidType.BUCKET_VOLUME = 1000 (ведро = 1000)
 * Fabric  : FluidConstants.BUCKET   = 81000 (ведро = 81000, т.е. «капля/блок»)
 *
 * Канон внутри шима и AE2 — NeoForge-ный. На границе с Fabric Transfer API
 * обязательно туда-обратно умножать/делить на 81. Деление всегда с округлением
 * вниз: иначе можно «создать» жидкость из ничего.
 */
package nfcompat.bridge;

public final class FluidUnits {
    /** Ведро в юнитах NeoForge. */
    public static final int NF_BUCKET = 1000;
    /** Ведро в юнитах Fabric Transfer API. */
    public static final long FABRIC_BUCKET = 81000;
    /** Множитель пересчёта NF -> Fabric. */
    public static final long SCALE = FABRIC_BUCKET / NF_BUCKET; // 81

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
}

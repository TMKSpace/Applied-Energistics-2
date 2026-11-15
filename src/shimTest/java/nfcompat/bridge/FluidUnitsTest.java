package nfcompat.bridge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Мост единиц жидкости. Здесь легко ошибиться в 81 раз, а заметить это можно
 * только по "ведро превратилось в 81 ведро" в игре — поэтому тест.
 */
class FluidUnitsTest {

    @Test
    @DisplayName("ведро NeoForge = ведро Fabric")
    void bucketStaysBucket() {
        assertThat(FluidUnits.toFabric(1000L)).isEqualTo(81000L);
        assertThat(FluidUnits.toNf(81000L)).isEqualTo(1000);
    }

    @Test
    @DisplayName("масштаб ровно 81, не 80 и не 1000")
    void scaleIsEightyOne() {
        assertThat(FluidUnits.SCALE).isEqualTo(81L);
        assertThat(FluidUnits.FABRIC_BUCKET / FluidUnits.NF_BUCKET).isEqualTo(81L);
    }

    @Test
    @DisplayName("Fabric -> NF округляет ВНИЗ, а не к ближайшему")
    void roundsDown() {
        // ровно один mB Fabric — это меньше одного mB NeoForge
        assertThat(FluidUnits.toNf(80L)).isZero();
        assertThat(FluidUnits.toNf(81L)).isEqualTo(1);
        assertThat(FluidUnits.toNf(162L)).isEqualTo(2);
        assertThat(FluidUnits.toNfLong(161L)).isEqualTo(1L);
    }

    @Test
    @DisplayName("дробные остатки не накапливаются в минус")
    void neverNegative() {
        assertThat(FluidUnits.toNf(0L)).isZero();
        assertThat(FluidUnits.toNf(-1L)).isZero();
        assertThat(FluidUnits.toNfLong(-1000L)).isZero();
    }

    @Test
    @DisplayName("int-перегрузка не переполняется на больших объёмах")
    void intOverloadDoesNotOverflow() {
        // 2^31-1 mB NeoForge * 81 не влезет в int — проверяем, что считаем в long
        long asFabric = FluidUnits.toFabric(Integer.MAX_VALUE);
        assertThat(asFabric).isEqualTo((long) Integer.MAX_VALUE * 81L);
        assertThat(FluidUnits.toNf(asFabric)).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("насыщение на Long.MAX_VALUE вместо тихого переполнения")
    void saturatesOnOverflow() {
        assertThat(FluidUnits.toFabricSaturated(Long.MAX_VALUE)).isEqualTo(Long.MAX_VALUE);
        assertThat(FluidUnits.toFabricSaturated(Long.MAX_VALUE / 81L)).isEqualTo((Long.MAX_VALUE / 81L) * 81L);
    }

    @Test
    @DisplayName("туда-обратно: целые вёдра переживают конвертацию без потерь")
    void roundTripWholeBuckets() {
        for (int buckets = 0; buckets <= 64; buckets++) {
            long nf = (long) buckets * FluidUnits.NF_BUCKET;
            assertThat(FluidUnits.toNf(FluidUnits.toFabric(nf)))
                    .as("вёдер: %d", buckets)
                    .isEqualTo((int) nf);
        }
    }
}

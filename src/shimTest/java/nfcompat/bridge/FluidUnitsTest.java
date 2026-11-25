package nfcompat.bridge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FluidUnits теперь identity-обёртка: порт живёт в Fabric-шкале droplets (ведро = 81000),
 * как официальный 1.20.1-Fabric-порт AE2. Тест фиксирует, что пересчёта больше нет:
 * числа проходят как есть, масштаб единичный, бутылка — ровно треть ведра, остатки
 * droplets не сгорают. Осталась только защита int-границы (ResourceHandler).
 */
class FluidUnitsTest {

    @Test
    @DisplayName("ведро остаётся ведром: 81000 -> 81000")
    void bucketStaysBucket() {
        assertThat(FluidUnits.toFabric(81000L)).isEqualTo(81000L);
        assertThat(FluidUnits.toNf(81000L)).isEqualTo(81000);
        assertThat(FluidUnits.toNfLong(81000L)).isEqualTo(81000L);
    }

    @Test
    @DisplayName("масштаб единичный, константы ведра совпадают")
    void scaleIsOne() {
        assertThat(FluidUnits.SCALE).isEqualTo(1L);
        assertThat(FluidUnits.NF_BUCKET).isEqualTo(81000);
        assertThat(FluidUnits.FABRIC_BUCKET).isEqualTo(81000L);
        assertThat(FluidUnits.NF_BUCKET).isEqualTo(FluidUnits.FABRIC_BUCKET);
    }

    @Test
    @DisplayName("1000 проходит как 1000 (бывший mB больше ни при чём)")
    void thousandPassesThrough() {
        assertThat(FluidUnits.toFabric(1000L)).isEqualTo(1000L);
        assertThat(FluidUnits.toFabric(1000)).isEqualTo(1000L);
        assertThat(FluidUnits.toNf(1000L)).isEqualTo(1000);
    }

    @Test
    @DisplayName("бутылка — ровно треть ведра, без дробей и округления")
    void bottleIsThirdOfBucket() {
        assertThat(FluidUnits.FABRIC_BOTTLE * 3).isEqualTo(FluidUnits.FABRIC_BUCKET);
        assertThat(FluidUnits.toNf(FluidUnits.FABRIC_BOTTLE)).isEqualTo(27000);
        assertThat(FluidUnits.toFabric(27000L)).isEqualTo(FluidUnits.FABRIC_BOTTLE);
        // раньше здесь было 333 mB -> 26973 droplets, теперь 27000 остаётся 27000
        assertThat(FluidUnits.toFabric(FluidUnits.toNf(FluidUnits.FABRIC_BOTTLE)))
                .isEqualTo(FluidUnits.FABRIC_BOTTLE);
    }

    @Test
    @DisplayName("округления вниз больше нет: остатки droplets не сгорают")
    void noRounding() {
        assertThat(FluidUnits.toNf(80L)).isEqualTo(80);
        assertThat(FluidUnits.toNf(1L)).isEqualTo(1);
        assertThat(FluidUnits.toNfLong(161L)).isEqualTo(161L);
        assertThat(FluidUnits.toFabric(404)).isEqualTo(404L);
    }

    @Test
    @DisplayName("отрицательные значения отсекаются на входе в int/long-ветки")
    void neverNegative() {
        assertThat(FluidUnits.toNf(0L)).isZero();
        assertThat(FluidUnits.toNf(-1L)).isZero();
        assertThat(FluidUnits.toNfLong(-1000L)).isZero();
        assertThat(FluidUnits.toFabricSaturated(-1L)).isZero();
    }

    @Test
    @DisplayName("int-граница: больше Integer.MAX_VALUE насыщается, а не переполняется")
    void intBoundarySaturates() {
        assertThat(FluidUnits.toNf(Integer.MAX_VALUE)).isEqualTo(Integer.MAX_VALUE);
        assertThat(FluidUnits.toNf((long) Integer.MAX_VALUE + 1L)).isEqualTo(Integer.MAX_VALUE);
        assertThat(FluidUnits.toNf(Long.MAX_VALUE)).isEqualTo(Integer.MAX_VALUE);
        // toNfLong int не ограничивает
        assertThat(FluidUnits.toNfLong(Long.MAX_VALUE)).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    @DisplayName("туда-обратно: любое целое число droplets возвращается собой")
    void roundTrip() {
        long[] samples = { 0L, 1L, 80L, 1000L, 27000L, 81000L, 486000L, 648000L, 1_000_000L };
        for (long droplets : samples) {
            assertThat(FluidUnits.toNf(FluidUnits.toFabric(droplets)))
                    .as("droplets: %d", droplets)
                    .isEqualTo((int) droplets);
        }
    }

    @Test
    @DisplayName("Long.MAX_VALUE не переполняется ни в одну сторону")
    void saturatesOnOverflow() {
        assertThat(FluidUnits.toFabricSaturated(Long.MAX_VALUE)).isEqualTo(Long.MAX_VALUE);
        assertThat(FluidUnits.toFabric(Long.MAX_VALUE)).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    @DisplayName("ведро за ведром: 64 ведра туда-обратно без потерь")
    void roundTripWholeBuckets() {
        for (int buckets = 0; buckets <= 64; buckets++) {
            long droplets = (long) buckets * FluidUnits.FABRIC_BUCKET;
            assertThat(FluidUnits.toNf(FluidUnits.toFabric(droplets)))
                    .as("вёдер: %d", buckets)
                    .isEqualTo((int) droplets);
        }
    }
}

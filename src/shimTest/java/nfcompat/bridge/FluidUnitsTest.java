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

    @Test
    @DisplayName("бутылка = треть ведра, и запас меньше mB дотягивает запрос до её кванта")
    void bottleIsOneThirdOfBucket() {
        // треть ведра, как у ванильного котла
        assertThat(FluidUnits.FABRIC_BOTTLE * 3).isEqualTo(FluidUnits.FABRIC_BUCKET);
        // в mB это 333.33 -> AE2 видит 333
        assertThat(FluidUnits.toNf(FluidUnits.FABRIC_BOTTLE)).isEqualTo(333);
        // 333 mB обратно = 26973 droplets, на 27 меньше кванта бутылки:
        // контейнер «всё или ничего» (EmptyItemFluidStorage/WaterPotionStorage) откажет
        assertThat(FluidUnits.toFabric(333)).isEqualTo(26973L);
        assertThat(FluidUnits.toFabric(333)).isLessThan(FluidUnits.FABRIC_BOTTLE);
        // с запасом меньше одного mB запрос дотягивается до 27000, но не раздувает его
        assertThat(FluidUnits.pad(FluidUnits.toFabric(333)))
                .isBetween(FluidUnits.FABRIC_BOTTLE, FluidUnits.FABRIC_BOTTLE + FluidUnits.PAD);
        // запас не превращается в «лишнее ведро»: он всегда меньше одного mB
        assertThat(FluidUnits.PAD).isEqualTo(FluidUnits.SCALE - 1);
        assertThat(FluidUnits.pad(Long.MAX_VALUE)).isEqualTo(Long.MAX_VALUE);
    }
}

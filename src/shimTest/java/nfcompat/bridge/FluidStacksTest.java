package nfcompat.bridge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * FluidStacks: конвертация FluidStack/FluidResource (NeoForge) в FluidVariant (Fabric)
 * и обратно.
 * <p>
 * ПОЧЕМУ ВСЁ ОТКЛЮЧЕНО: оба направления упираются в {@code FluidVariant}, а его в
 * тестах не создать. В loader'е Fabric патчит {@code Fluid} так, чтобы он реализовывал
 * {@code net.fabricmc.fabric.impl.transfer.fluid.FluidVariantCache}; вне рантайма
 * loader'а (обычный JUnit на классах MC) получаем
 * {@code ClassCastException: Fluid cannot be cast to FluidVariantCache}.
 * Бутстрап регистров это не лечит — нужен именно запущенный Fabric.
 * <p>
 * Что в итоге проверяется без рантайма: масштаб и округление количеств
 * (см. {@link FluidUnitsTest}) и то, что "меньше одного mB -> EMPTY" — эта ветка
 * тоже внутри {@code fromVariant}, поэтому здесь только тесты "на будущее":
 * как только появится Fabric test-harness, их достаточно снять с @Disabled.
 */
class FluidStacksTest {

    @Test
    @org.junit.jupiter.api.Disabled("Fabric-рантайм: FluidVariant.blank() падает ClassCastException")
    @DisplayName("пустой стек -> пустой вариант, вариант -> EMPTY стек")
    void emptyStackRoundTrip() {
        assertThat(FluidStacks.toVariant(FluidStack.EMPTY).isBlank()).isTrue();
        assertThat(FluidStacks.fromVariant(FluidStacks.toVariant(FluidStack.EMPTY), 81000))
                .isSameAs(FluidStack.EMPTY);
    }

    @Test
    @org.junit.jupiter.api.Disabled("Fabric-рантайм: FluidVariant.of() падает ClassCastException")
    @DisplayName("ведро: 81000 Fabric -> 1000 mB NeoForge")
    void bucketRoundTrip() {
        var variant = FluidStacks.toVariant(new FluidStack(Fluids.WATER, 1000));
        var stack = FluidStacks.fromVariant(variant, 81000);

        assertThat(stack.getFluid()).isEqualTo(Fluids.WATER);
        assertThat(stack.getAmount()).isEqualTo(1000);
    }

    @Test
    @org.junit.jupiter.api.Disabled("Fabric-рантайм: FluidVariant.of() падает ClassCastException")
    @DisplayName("меньше одного mB Fabric -> пустой стек, а не 0-стек")
    void lessThanOneMillibucketIsEmpty() {
        var variant = FluidStacks.toVariant(FluidResource.of(Fluids.WATER));

        assertThat(FluidStacks.fromVariant(variant, 80)).isSameAs(FluidStack.EMPTY);
        assertThat(FluidStacks.fromVariant(variant, 0)).isSameAs(FluidStack.EMPTY);
    }
}

/*
 * Часть nfcompat. В NeoForge FluidStack — аналог ItemStack для жидкостей
 * (жидкость + количество + компоненты). В ванили 26.3 такого класса нет.
 *
 * ВАЖНО про единицы: здесь NeoForge-ная шкала, ведро = 1000 (FluidType.BUCKET_VOLUME).
 * Fabric Transfer API считает ведро как 81000, поэтому при пересечении границы
 * обязателен пересчёт — см. nfcompat.bridge.FluidUnits.
 */
package net.neoforged.neoforge.fluids;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

public final class FluidStack {

    public static final Codec<FluidStack> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.FLUID.holderByNameCodec().fieldOf("id").forGetter(FluidStack::typeHolder),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("amount", 1).forGetter(FluidStack::getAmount),
            DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY)
                    .forGetter(FluidStack::getComponentsPatch))
            .apply(instance, FluidStack::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, FluidStack> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.holderRegistry(Registries.FLUID), FluidStack::typeHolder,
            ByteBufCodecs.VAR_INT, FluidStack::getAmount,
            DataComponentPatch.STREAM_CODEC, FluidStack::getComponentsPatch,
            FluidStack::new);

    public static final FluidStack EMPTY = new FluidStack(Fluids.EMPTY.builtInRegistryHolder(), 0, DataComponentPatch.EMPTY);

    public static final StreamCodec<RegistryFriendlyByteBuf, FluidStack> OPTIONAL_STREAM_CODEC = ByteBufCodecs
            .optional(STREAM_CODEC)
            .map(stack -> stack.orElse(EMPTY),
                    stack -> stack.isEmpty() ? Optional.empty() : Optional.of(stack));

    public static final Codec<FluidStack> OPTIONAL_CODEC = ExtraCodecs.optionalEmptyMap(CODEC)
            .xmap(optional -> optional.orElse(EMPTY), stack -> stack.isEmpty() ? Optional.empty() : Optional.of(stack));

    private final Holder<Fluid> fluid;
    private int amount;
    private final PatchedDataComponentMap components;

    public FluidStack(Fluid fluid, int amount) {
        this(fluid.builtInRegistryHolder(), amount, DataComponentPatch.EMPTY);
    }

    public FluidStack(Fluid fluid, int amount, DataComponentPatch patch) {
        this(fluid.builtInRegistryHolder(), amount, patch);
    }

    public FluidStack(Holder<Fluid> fluid, int amount) {
        this(fluid, amount, DataComponentPatch.EMPTY);
    }

    public FluidStack(Holder<Fluid> fluid, int amount, DataComponentPatch patch) {
        this.fluid = fluid;
        this.amount = Math.max(amount, 0);
        this.components = PatchedDataComponentMap.fromPatch(DataComponentMap.EMPTY, patch);
    }

    /** Кодек с фиксированным количеством (нужен для ресурсов/компонентов, где объём не хранится). */
    public static Codec<FluidStack> fixedAmountCodec(int amount) {
        return RecordCodecBuilder.create(instance -> instance.group(
                BuiltInRegistries.FLUID.holderByNameCodec().fieldOf("id").forGetter(FluidStack::typeHolder),
                DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY)
                        .forGetter(FluidStack::getComponentsPatch))
                .apply(instance, (fluid, patch) -> new FluidStack(fluid, amount, patch)));
    }

    public static FluidStack of(Fluid fluid, int amount) {
        return fluid == Fluids.EMPTY || amount <= 0 ? EMPTY : new FluidStack(fluid, amount);
    }

    public static FluidStack of(Holder<Fluid> fluid, int amount) {
        return of(fluid.value(), amount);
    }

    public Holder<Fluid> typeHolder() {
        return fluid;
    }

    public Fluid getFluid() {
        return fluid.value();
    }

    public FluidType getFluidType() {
        return FluidType.of(getFluid());
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = Math.max(amount, 0);
    }

    public boolean isEmpty() {
        return amount <= 0 || fluid.value() == Fluids.EMPTY;
    }

    public boolean is(Fluid other) {
        return getFluid() == other;
    }

    public boolean is(Holder<Fluid> other) {
        return fluid.equals(other);
    }

    public boolean is(FluidType type) {
        return getFluidType().equals(type);
    }

    public FluidStack copy() {
        return amount <= 0 ? EMPTY : new FluidStack(fluid, amount, getComponentsPatch());
    }

    public FluidStack copyWithAmount(int newAmount) {
        return newAmount <= 0 ? EMPTY : new FluidStack(fluid, newAmount, getComponentsPatch());
    }

    public DataComponentMap getComponents() {
        return components;
    }

    public DataComponentMap immutableComponents() {
        return components;
    }

    public DataComponentPatch getComponentsPatch() {
        return components.asPatch();
    }

    public boolean isComponentsPatchEmpty() {
        return getComponentsPatch().isEmpty();
    }

    public boolean has(DataComponentType<?> type) {
        return components.has(type);
    }

    @Nullable
    public <T> T get(DataComponentType<T> type) {
        return components.get(type);
    }

    public <T> void set(DataComponentType<T> type, @Nullable T value) {
        components.set(type, value);
    }

    public void remove(DataComponentType<?> type) {
        components.remove(type);
    }

    public void applyComponents(DataComponentPatch patch) {
        components.applyPatch(patch);
    }

    public Component getHoverName() {
        return getFluidType().getDescription(this);
    }

    public static boolean isSameFluidSameComponents(FluidStack a, FluidStack b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.fluid.equals(b.fluid) && a.getComponentsPatch().equals(b.getComponentsPatch());
    }

    public static boolean isSameFluidSameComponents(FluidStack stack, FluidStackTemplate template) {
        if (stack == null || template == null) {
            return false;
        }
        return stack.typeHolder().equals(template.fluid()) && stack.getComponentsPatch().equals(template.components());
    }

    public static int hashFluidAndComponents(@Nullable FluidStack stack) {
        return stack == null ? 0 : 31 * stack.fluid.hashCode() + stack.getComponentsPatch().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FluidStack other)) {
            return false;
        }
        return amount == other.amount && isSameFluidSameComponents(this, other);
    }

    @Override
    public int hashCode() {
        return 31 * hashFluidAndComponents(this) + amount;
    }

    @Override
    public String toString() {
        return "FluidStack[" + fluid.getRegisteredName() + " x" + amount + "]";
    }
}

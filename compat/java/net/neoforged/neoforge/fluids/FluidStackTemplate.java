package net.neoforged.neoforge.fluids;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import org.jspecify.annotations.Nullable;

/**
 * Жидкость + компоненты без количества (шаблон, из которого делают стек).
 */
public record FluidStackTemplate(Holder<Fluid> fluid, DataComponentPatch components) {
    public static final Codec<FluidStackTemplate> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.FLUID.holderByNameCodec().fieldOf("id").forGetter(FluidStackTemplate::fluid),
            DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY)
                    .forGetter(FluidStackTemplate::components))
            .apply(instance, FluidStackTemplate::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, FluidStackTemplate> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.holderRegistry(Registries.FLUID), FluidStackTemplate::fluid,
            DataComponentPatch.STREAM_CODEC, FluidStackTemplate::components,
            FluidStackTemplate::new);

    public FluidStackTemplate(Fluid fluid) {
        this(fluid.builtInRegistryHolder(), DataComponentPatch.EMPTY);
    }

    public static FluidStackTemplate fromStack(FluidStack stack) {
        return new FluidStackTemplate(stack.typeHolder(), stack.getComponentsPatch());
    }

    public FluidStack createStack(int amount) {
        return new FluidStack(fluid, amount, components);
    }

    /** В NeoForge это создание стека с количеством по умолчанию (1). */
    public FluidStack create() {
        return createStack(1);
    }

    public Fluid getFluid() {
        return fluid.value();
    }

    @Nullable
    public <T> T get(DataComponentType<T> type) {
        return PatchedDataComponentMap.fromPatch(DataComponentMap.EMPTY, components).get(type);
    }
}

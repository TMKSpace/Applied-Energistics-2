package net.neoforged.neoforge.fluids;

import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Лёгкая пара «жидкость + компоненты» (то, что в NeoForge зовут FluidInstance),
 * плюс хелперы для кодеков, которые ожидает скопированный transfer/**.
 */
public record FluidInstance(Fluid fluid, DataComponentPatch components) {

    public static final Codec<Holder<Fluid>> FLUID_HOLDER_CODEC = BuiltInRegistries.FLUID
            .holderByNameCodec()
            .validate(fluid -> fluid.is(Fluids.EMPTY.builtInRegistryHolder())
                    ? com.mojang.serialization.DataResult.error(() -> "Fluid must not be minecraft:empty")
                    : com.mojang.serialization.DataResult.success(fluid));

    public static final StreamCodec<RegistryFriendlyByteBuf, Holder<Fluid>> FLUID_HOLDER_STREAM_CODEC = ByteBufCodecs
            .holderRegistry(Registries.FLUID);

    public static FluidInstance of(FluidStack stack) {
        return new FluidInstance(stack.getFluid(), stack.getComponentsPatch());
    }

    public static FluidInstance of(Fluid fluid) {
        return new FluidInstance(fluid, DataComponentPatch.EMPTY);
    }

    public FluidType getType() {
        return FluidType.of(fluid);
    }
}

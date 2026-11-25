/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

/*
 * Часть nfcompat. Шим `net.neoforged.neoforge.fluids.crafting.SimpleFluidIngredient`.
 *
 * Сверено с NeoForge 26.3.x: тот же `HolderSet`-бэкенд, та же валидация
 * (пустой набор и `minecraft:empty` запрещены), те же `CODEC`/`CONTENTS_STREAM_CODEC`
 * (в апстриме они package-private, у нас тоже).
 *
 * Отличия только внешние, из-за отсутствия `getType()` (см. FluidIngredient, п.1):
 *  - нет `display()`/`FluidTagSlotDisplay` — клиентского SlotDisplay для жидкостей в шиме нет;
 *  - `fluidSet()` есть (его зовут редко, но он часть API).
 *
 * Валидация пустого набора: в NeoForge 26.x для этого используется
 * `HolderSet#isImmediatelyResolvable()`, которого в нашей 26.3 нет, поэтому смотрим
 * `unwrap()`: у Direct это right(список), у Named — left(тег), а тег на момент
 * конструирования может быть ещё не привязан — его не проверяем (как и апстрим).
 */
package net.neoforged.neoforge.fluids.crafting;

import java.util.stream.Stream;

import com.mojang.serialization.Codec;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.codec.HolderSetCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidInstance;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;

/**
 * Ингредиент, который совпадает с жидкостями из заданного {@link HolderSet}
 * (обычно это список жидкостей или тег).
 */
public class SimpleFluidIngredient extends FluidIngredient {
    private static final Codec<HolderSet<Fluid>> HOLDER_SET_NO_EMPTY_FLUID = HolderSetCodec.create(
            Registries.FLUID, FluidInstance.FLUID_HOLDER_CODEC, false);

    static final Codec<SimpleFluidIngredient> CODEC = ExtraCodecs.nonEmptyHolderSet(HOLDER_SET_NO_EMPTY_FLUID)
            .xmap(SimpleFluidIngredient::new, SimpleFluidIngredient::fluidSet);

    static final StreamCodec<RegistryFriendlyByteBuf, SimpleFluidIngredient> CONTENTS_STREAM_CODEC = ByteBufCodecs
            .holderSet(Registries.FLUID)
            .map(SimpleFluidIngredient::new, SimpleFluidIngredient::fluidSet);

    private final HolderSet<Fluid> values;

    public SimpleFluidIngredient(HolderSet<Fluid> values) {
        values.unwrap().ifRight(list -> {
            if (list.isEmpty()) {
                throw new UnsupportedOperationException("Fluid ingredients can't be empty!");
            } else if (list.contains(Fluids.EMPTY.builtInRegistryHolder())) {
                throw new UnsupportedOperationException("Fluid ingredients can't contain the empty fluid");
            }
        });
        this.values = values;
    }

    @Override
    public boolean test(FluidStack fluidStack) {
        return values.contains(fluidStack.typeHolder());
    }

    @Override
    protected Stream<Holder<Fluid>> generateFluids() {
        return values.stream();
    }

    @Override
    public boolean isSimple() {
        return true;
    }

    @Override
    public int hashCode() {
        return this.fluidSet().hashCode();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        }
        return obj instanceof SimpleFluidIngredient other && other.fluidSet().equals(this.fluidSet());
    }

    public HolderSet<Fluid> fluidSet() {
        return values;
    }
}

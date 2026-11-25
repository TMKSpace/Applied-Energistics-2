/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

/*
 * Часть nfcompat. Шим `net.neoforged.neoforge.fluids.crafting.CompoundFluidIngredient`.
 *
 * Сверено с NeoForge 26.3.x: совпадает предикат (любой из детей), `generateFluids`,
 * `isSimple` (все дети простые), `of(...)` и `children()`.
 *
 * Отличия:
 *  - вместо `NeoForgeExtraCodecs.aliasedFieldOf(..., "children", "ingredients")` пишем
 *    только `ingredients`: алиас "children" — legacy-форма для старых датапаков, у нас
 *    её никто не пишет. Если понадобится, добавлять в NeoForgeExtraCodecs, а не тут.
 *  - нет `getType()` (см. FluidIngredient, п.1).
 *
 * `CODEC_WITH_TYPE` (дополнительно к апстримному `CODEC`) — объект с явным полем
 * `neoforge:ingredient_type: neoforge:compound`. Именно так выглядит compound в JSON
 * NeoForge: тип добавляет dispatch, у нас его дописывает это поле. Нужен, чтобы
 * `FluidIngredient.CODEC` мог отличить объект от simple-формы.
 */
package net.neoforged.neoforge.fluids.crafting;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;

/**
 * Ингредиент, совпадающий, если совпал хотя бы один из детей.
 */
public final class CompoundFluidIngredient extends FluidIngredient {

    /** Имя типа в JSON. В NeoForge приходит из реестра, у нас — константа. */
    private static final String TYPE_ID = "neoforge:compound";

    private static final Codec<String> TYPE_NAME_CODEC = Codec.STRING.validate(
            name -> name.equals(TYPE_ID)
                    ? DataResult.success(name)
                    : DataResult.error(() -> "nfcompat: неподдерживаемый тип жидкостного ингредиента \"" + name
                            + "\" (поддерживается только " + TYPE_ID + ")"));

    public static final MapCodec<CompoundFluidIngredient> CODEC = RecordCodecBuilder
            .mapCodec(instance -> instance.group(
                    // lazyInitialized - потому что FluidIngredient.CODEC ссылается на этот кодек обратно.
                    Codec.lazyInitialized(() -> FluidIngredient.CODEC).listOf(1, Integer.MAX_VALUE)
                            .fieldOf("ingredients")
                            .forGetter(CompoundFluidIngredient::children))
                    .apply(instance, CompoundFluidIngredient::new));

    static final Codec<CompoundFluidIngredient> CODEC_WITH_TYPE = RecordCodecBuilder
            .create(instance -> instance.group(
                    TYPE_NAME_CODEC.fieldOf("neoforge:ingredient_type").forGetter(ingredient -> TYPE_ID),
                    Codec.lazyInitialized(() -> FluidIngredient.CODEC).listOf(1, Integer.MAX_VALUE)
                            .fieldOf("ingredients")
                            .forGetter(CompoundFluidIngredient::children))
                    // Второй аргумент нужен только для записи типа, в конструктор он не идёт.
                    .apply(instance, (type, children) -> new CompoundFluidIngredient(children)));

    private final List<FluidIngredient> children;

    public CompoundFluidIngredient(List<? extends FluidIngredient> children) {
        if (children.isEmpty()) {
            throw new IllegalArgumentException("Compound fluid ingredient must have at least one child");
        }
        this.children = List.copyOf(children);
    }

    public static FluidIngredient of(FluidIngredient... children) {
        if (children.length == 1) {
            return children[0];
        }
        return new CompoundFluidIngredient(List.of(children));
    }

    public static FluidIngredient of(List<FluidIngredient> children) {
        if (children.size() == 1) {
            return children.getFirst();
        }
        return new CompoundFluidIngredient(children);
    }

    @Override
    protected Stream<Holder<Fluid>> generateFluids() {
        return children.stream().flatMap(FluidIngredient::generateFluids);
    }

    @Override
    public boolean test(FluidStack stack) {
        for (var child : children) {
            if (child.test(stack)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isSimple() {
        for (var child : children) {
            if (!child.isSimple()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(children);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        }
        return obj instanceof CompoundFluidIngredient other && other.children.equals(this.children);
    }

    public List<FluidIngredient> children() {
        return children;
    }
}

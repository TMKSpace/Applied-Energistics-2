/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

/*
 * Часть nfcompat. Шим `net.neoforged.neoforge.fluids.crafting.FluidIngredient`.
 *
 * Сверено с NeoForge 26.3.x (src/main/java/net/neoforged/neoforge/fluids/crafting/FluidIngredient.java).
 * Взято один-в-один по смыслу:
 *  - `fluids()` с ленивым кэшем и `generateFluids()`;
 *  - `test(FluidStack)`, `isSimple()`;
 *  - фабрики `of(FluidStack...)`, `of(Fluid...)`, `of(Stream<Fluid>)`, `of(HolderSet<Fluid>)`;
 *  - `CODEC` / `STREAM_CODEC`.
 *
 * Кому нужно: extendedae_plus (JEI-категория Super Crystal Assembler) делает
 * `(FluidIngredient) stack.getIngredient()` и зовёт `fluids()`, ожидая `List<Holder<Fluid>>`
 * (в байткоде — `List`, дальше checkcast на `Holder` + `value()`). Больше ничего из
 * этого класса аддоны из замера не трогают.
 *
 * СОЗНАТЕЛЬНЫЕ РАСХОЖДЕНИЯ:
 *  1. НЕТ `getType()`/`FluidIngredientType` и регистрации `neoforge:fluid_ingredient_types`:
 *     такого реестра в MC нет, а `NeoForgeRegistries` у нас — виртуальная мапа.
 *     Следствие: `CustomDisplayFluidIngredient`-подобных типов из датапаков не будет.
 *  2. `CODEC` поддерживает только два наших типа (simple и compound), а не dispatch
 *     по реестру. JSON-форма для них совпадает с NeoForge:
 *       "minecraft:water" / ["a:b", "c:d"] / "#tag"   — simple;
 *       {"neoforge:ingredient_type":"neoforge:compound","ingredients":[...]} — compound.
 *     Неизвестный `neoforge:ingredient_type` даст ошибку декодирования, а не тихую заглушку.
 *  3. `STREAM_CODEC` — свой формат (varint-дискриминатор 0=simple, 1=compound), а не
 *     dispatch по id реестра типов. По сети это уходит только между нашими клиентом и
 *     сервером (в NeoForge-мирах таких пакетов не бывает), так что формат внутренний.
 *  4. Нет `display()`/`FluidSlotDisplay` — клиентского SlotDisplay для жидкостей в шиме нет.
 *  5. `getStacks()` в 26.x из NeoForge УБРАН (остался `fluids()`); оставлен здесь,
 *     потому что его зовут аддоны, написанные под 1.20/1.21: отдаёт по одному стаку
 *     на жидкость с amount=1, как тогда.
 *  6. `of(TagKey)` в 26.x тоже нет (тег задаётся через `HolderSet`/JSON `#tag`);
 *     добавлен как удобная обёртка — вреда не несёт.
 *  7. Нет `EMPTY`: в 26.x его нет, а конструктор `SimpleFluidIngredient` пустой набор
 *     запрещает (NeoForge тоже запрещает).
 */
package net.neoforged.neoforge.fluids.crafting;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;

/**
 * Жидкостный аналог ванильного {@code Ingredient}: предикат по {@link FluidStack}
 * плюс список подходящих жидкостей (для отображения).
 */
public abstract class FluidIngredient implements Predicate<FluidStack> {

    /**
     * Разбор JSON. Simple-форма — «ванильный синтаксис» (id, список id или #тег),
     * compound — объект с полем типа. Dispatch по реестру типов не делаем, см. шапку.
     */
    public static final Codec<FluidIngredient> CODEC = Codec
            .xor(SimpleFluidIngredient.CODEC, CompoundFluidIngredient.CODEC_WITH_TYPE)
            .xmap(
                    either -> either.map(simple -> (FluidIngredient) simple, compound -> (FluidIngredient) compound),
                    ingredient -> {
                        // Третьих типов у нас нет (getType/реестр типов не реализованы):
                        // simple пишется «ванильным синтаксисом», всё остальное - compound.
                        if (ingredient instanceof SimpleFluidIngredient simple) {
                            return Either.left(simple);
                        }
                        return Either.right((CompoundFluidIngredient) ingredient);
                    });

    public static final StreamCodec<RegistryFriendlyByteBuf, FluidIngredient> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public FluidIngredient decode(RegistryFriendlyByteBuf buf) {
            int type = buf.readVarInt();
            return switch (type) {
                case 0 -> SimpleFluidIngredient.CONTENTS_STREAM_CODEC.decode(buf);
                case 1 -> {
                    int size = buf.readVarInt();
                    var children = new java.util.ArrayList<FluidIngredient>(size);
                    for (int i = 0; i < size; i++) {
                        children.add(STREAM_CODEC.decode(buf));
                    }
                    yield new CompoundFluidIngredient(children);
                }
                default -> throw new io.netty.handler.codec.DecoderException("nfcompat: неизвестный тип FluidIngredient " + type);
            };
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, FluidIngredient ingredient) {
            if (ingredient instanceof SimpleFluidIngredient simple) {
                buf.writeVarInt(0);
                SimpleFluidIngredient.CONTENTS_STREAM_CODEC.encode(buf, simple);
            } else if (ingredient instanceof CompoundFluidIngredient compound) {
                buf.writeVarInt(1);
                buf.writeVarInt(compound.children().size());
                for (var child : compound.children()) {
                    STREAM_CODEC.encode(buf, child);
                }
            } else {
                throw new UnsupportedOperationException(
                        "nfcompat: FluidIngredient " + ingredient.getClass().getName() + " не сериализуется по сети");
            }
        }
    };

    public static final StreamCodec<RegistryFriendlyByteBuf, Optional<FluidIngredient>> OPTIONAL_STREAM_CODEC = ByteBufCodecs
            .optional(STREAM_CODEC);

    @Nullable
    private List<Holder<Fluid>> fluids;

    /**
     * Кэшированный список жидкостей этого ингредиента. Список неизменяемый,
     * менять его нельзя (как и в NeoForge).
     */
    public final List<Holder<Fluid>> fluids() {
        if (fluids == null) {
            fluids = generateFluids().toList();
        }
        return fluids;
    }

    /** Подходит ли стак. Стек менять нельзя. */
    @Override
    public abstract boolean test(FluidStack fluidStack);

    /** Поток подходящих жидкостей; кэшируется в {@link #fluids()}. */
    protected abstract Stream<Holder<Fluid>> generateFluids();

    /** true, если тестировать стак напрямую не обязательно (совпадает с NeoForge). */
    public abstract boolean isSimple();

    /**
     * В 26.x у NeoForge такого метода нет (см. шапку, п.5) — оставлен для аддонов
     * под 1.20/1.21. Amount=1, как было в NeoForge.
     */
    public List<FluidStack> getStacks() {
        return fluids().stream().map(holder -> new FluidStack(holder, 1)).toList();
    }

    public static FluidIngredient of(FluidStack... fluids) {
        return of(Arrays.stream(fluids).map(FluidStack::getFluid));
    }

    public static FluidIngredient of(Fluid... fluids) {
        return of(Arrays.stream(fluids));
    }

    public static FluidIngredient of(Stream<Fluid> fluids) {
        return of(HolderSet.direct(fluids.map(Fluid::builtInRegistryHolder).toList()));
    }

    public static FluidIngredient of(HolderSet<Fluid> fluids) {
        return new SimpleFluidIngredient(fluids);
    }

    /** Удобная обёртка (в 26.x нет, см. шапку, п.6): ингредиент по тегу. */
    public static FluidIngredient of(TagKey<Fluid> tag) {
        return new SimpleFluidIngredient(BuiltInRegistries.FLUID.getOrThrow(tag));
    }
}

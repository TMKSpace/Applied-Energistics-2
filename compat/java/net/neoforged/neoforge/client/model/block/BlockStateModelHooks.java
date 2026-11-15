/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

// Часть nfcompat. Порт NeoForge-механизма кастомных unbaked-моделей blockstate.
//
// Проблема: ванильный `BlockStateModel.Unbaked.CODEC` = either(список весов, SingleVariant),
// поэтому одиночный объект вида {"type": "ae2:crafting_cube", "unit_type": "storage_1k"}
// не парсится ("Not a json array" / "No key model in MapLike"). NeoForge патчит кодек,
// добавляя диспетчеризацию по "type", мы делаем то же самое, но снаружи - см.
// nfcompat.mixin.SimpleModelSelectorsMixin и nfcompat.mixin.MultipartSelectorMixin:
// они подменяют чтение ванильного поля CODEC на `BlockStateModelHooks.codec()`.
//
// Почему не через @Mutable, как делает NeoForge: CODEC лежит в интерфейсе
// (`BlockStateModel.Unbaked`), а JVM требует, чтобы поля интерфейса были final -
// снятие ACC_FINAL даёт ClassFormatError: Illegal field modifiers in class.
package net.neoforged.neoforge.client.model.block;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.SingleVariant;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.client.renderer.block.dispatch.WeightedVariants;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.neoforged.neoforge.common.util.NeoForgeExtraCodecs;

/**
 * NeoForge-хук для регистрации кастомных {@link BlockStateModel.Unbaked}.
 *
 * <p>В NeoForge типы регистрируются через {@code RegisterBlockStateModels}, который
 * постится из {@code BlockStateModelHooks.init()} при инициализации мода. У нас шим,
 * поэтому событие постит клиентский мост (nfcompat.NfCompatClient), а тут есть просто
 * публичный {@link #registerModel(Identifier, MapCodec)} и сам маппер.
 */
public final class BlockStateModelHooks {
    /**
     * Реестр "id типа" -> кодек модели. Заполняется поздно (после инициализации AE2),
     * поэтому {@link ExtraCodecs.LateBoundIdMapper#codec} читает мапу лениво - при разборе
     * конкретного blockstate-файла, а не в момент создания кодека.
     */
    public static final ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends CustomUnbakedBlockStateModel>> BLOCK_STATE_MODEL_IDS = new ExtraCodecs.LateBoundIdMapper<>();

    /**
     * Маппер для кастомных *определений* blockstate-файла (NeoForge `CustomBlockModelDefinition`).
     * У нас он не подключён (ванильный `BlockStateModelDispatcher.CODEC` не подменить: из него
     * нельзя вытащить MapCodec), но нужен, чтобы собрать {@code RegisterBlockStateModels}.
     */
    public static final ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends CustomBlockModelDefinition>> BLOCK_MODEL_DEFINITION_IDS = new ExtraCodecs.LateBoundIdMapper<>();

    /**
     * Ванильный `SingleVariant.Unbaked.CODEC` - это Codec (Variant.MAP_CODEC.codec().xmap(...)),
     * а для диспетчеризации нужен MapCodec. NeoForge патчит SingleVariant и добавляет MAP_CODEC;
     * класс ванильный, поэтому строим такой же сами (ровно то же преобразование).
     */
    private static final MapCodec<SingleVariant.Unbaked> SINGLE_VARIANT_MAP_CODEC = Variant.MAP_CODEC
            .xmap(SingleVariant.Unbaked::new, SingleVariant.Unbaked::variant);

    /** Регистрация типа кастомной модели (`ae2:cable_bus`, `ae2:drive`, ...). */
    public static void registerModel(Identifier location, MapCodec<? extends CustomUnbakedBlockStateModel> codec) {
        BLOCK_STATE_MODEL_IDS.put(location, codec);
    }

    /** Кодек, которым надо заменить ванильный {@code BlockStateModel.Unbaked.CODEC}. */
    public static Codec<BlockStateModel.Unbaked> codec() {
        return Holder.CODEC;
    }

    // NeoForge: BlockStateModelHooks.makeSingleModelCodec()
    private static MapCodec<Either<CustomUnbakedBlockStateModel, SingleVariant.Unbaked>> makeSingleModelCodec() {
        return NeoForgeExtraCodecs.dispatchMapOrElse(
                BLOCK_STATE_MODEL_IDS.codec(Identifier.CODEC),
                CustomUnbakedBlockStateModel::codec,
                c -> c,
                SINGLE_VARIANT_MAP_CODEC);
    }

    // NeoForge: BlockStateModelHooks.makeElementCodec()
    private static Codec<Weighted<Either<CustomUnbakedBlockStateModel, SingleVariant.Unbaked>>> makeElementCodec() {
        var singleModelCodec = makeSingleModelCodec();
        return RecordCodecBuilder.create(
                instance -> instance.group(
                        singleModelCodec.forGetter(Weighted::value),
                        ExtraCodecs.POSITIVE_INT.optionalFieldOf("weight", 1).forGetter(Weighted::weight))
                        .apply(instance, Weighted::new));
    }

    // Копия ванильного HARDCODED_WEIGHTED_CODEC/CODEC, но с кастомными моделями в элементах.
    private static Codec<BlockStateModel.Unbaked> makeCodec() {
        Codec<Either<CustomUnbakedBlockStateModel, SingleVariant.Unbaked>> singleModelCodec = makeSingleModelCodec().codec();

        Codec<WeightedVariants.Unbaked> weightedVariantsCodec = ExtraCodecs.nonEmptyList(makeElementCodec().listOf())
                .flatComapMap(BlockStateModelHooks::toWeightedVariants, BlockStateModelHooks::fromWeightedVariants);

        return Codec.either(weightedVariantsCodec, singleModelCodec)
                .flatComapMap(BlockStateModelHooks::toSingleOrWeighted, BlockStateModelHooks::fromSingleOrWeighted);
    }

    // List<Weighted<Either<custom, vanilla>>> -> WeightedVariants.Unbaked
    private static WeightedVariants.Unbaked toWeightedVariants(
            List<Weighted<Either<CustomUnbakedBlockStateModel, SingleVariant.Unbaked>>> list) {
        List<Weighted<BlockStateModel.Unbaked>> entries = new ArrayList<>(list.size());
        for (Weighted<Either<CustomUnbakedBlockStateModel, SingleVariant.Unbaked>> entry : list) {
            BlockStateModel.Unbaked model = entry.value()
                    .map(l -> (BlockStateModel.Unbaked) l, r -> (BlockStateModel.Unbaked) r);
            entries.add(new Weighted<>(model, entry.weight()));
        }
        return new WeightedVariants.Unbaked(WeightedList.of(entries));
    }

    // WeightedVariants.Unbaked -> List<Weighted<Either<custom, vanilla>>>
    private static DataResult<List<Weighted<Either<CustomUnbakedBlockStateModel, SingleVariant.Unbaked>>>> fromWeightedVariants(
            WeightedVariants.Unbaked unbaked) {
        List<Weighted<BlockStateModel.Unbaked>> entries = unbaked.entries().unwrap();
        List<Weighted<Either<CustomUnbakedBlockStateModel, SingleVariant.Unbaked>>> result = new ArrayList<>(entries.size());

        for (Weighted<BlockStateModel.Unbaked> entry : entries) {
            Either<CustomUnbakedBlockStateModel, SingleVariant.Unbaked> either;
            if (entry.value() instanceof CustomUnbakedBlockStateModel customModel) {
                either = Either.left(customModel);
            } else if (entry.value() instanceof SingleVariant.Unbaked singleVariant) {
                either = Either.right(singleVariant);
            } else {
                return DataResult.error(() -> "Only custom models or single variants are supported");
            }
            result.add(new Weighted<>(either, entry.weight()));
        }

        return DataResult.success(result);
    }

    // Either<weighted, Either<custom, vanilla>> -> BlockStateModel.Unbaked
    private static BlockStateModel.Unbaked toSingleOrWeighted(
            Either<WeightedVariants.Unbaked, Either<CustomUnbakedBlockStateModel, SingleVariant.Unbaked>> either) {
        if (either.left().isPresent()) {
            return either.left().get();
        }
        Either<CustomUnbakedBlockStateModel, SingleVariant.Unbaked> single = either.right().orElseThrow();
        return single.map(l -> (BlockStateModel.Unbaked) l, r -> (BlockStateModel.Unbaked) r);
    }

    // BlockStateModel.Unbaked -> Either<weighted, Either<custom, vanilla>>
    private static DataResult<Either<WeightedVariants.Unbaked, Either<CustomUnbakedBlockStateModel, SingleVariant.Unbaked>>> fromSingleOrWeighted(
            BlockStateModel.Unbaked unbaked) {
        Either<WeightedVariants.Unbaked, Either<CustomUnbakedBlockStateModel, SingleVariant.Unbaked>> result;
        if (unbaked instanceof WeightedVariants.Unbaked multiple) {
            result = Either.left(multiple);
        } else if (unbaked instanceof CustomUnbakedBlockStateModel customModel) {
            Either<CustomUnbakedBlockStateModel, SingleVariant.Unbaked> single = Either.left(customModel);
            result = Either.right(single);
        } else if (unbaked instanceof SingleVariant.Unbaked singleVariant) {
            Either<CustomUnbakedBlockStateModel, SingleVariant.Unbaked> single = Either.right(singleVariant);
            result = Either.right(single);
        } else {
            return DataResult.error(() -> "Only a custom model or a single variant or a list of variants are supported");
        }
        return DataResult.success(result);
    }

    // Отдельный holder, чтобы кодек строился лениво: mixin обращается к нему из <clinit>
    // BlockStateModelDispatcher.SimpleModelSelectors, и порядок инициализации классов
    // лучше не трогать (Variant, ExtraCodecs и т.п.).
    private static final class Holder {
        private static final Codec<BlockStateModel.Unbaked> CODEC = makeCodec();
    }

    private BlockStateModelHooks() {}
}

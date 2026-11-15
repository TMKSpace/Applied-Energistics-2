/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

// Часть nfcompat. Вендоренный кусок NeoForge: из всего `NeoForgeExtraCodecs`
// нужен только `dispatchMapOrElse` - кодек, который диспетчеризует по ключу
// "type", а если ключа нет - отдаёт разбор фолбэку (ванильному кодеку).
// Именно так NeoForge научил blockstate-файлы понимать кастомные типы моделей,
// не ломая обычные варианты вида {"model": "..."}.
package net.neoforged.neoforge.common.util;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import java.util.function.Function;
import java.util.stream.Stream;

public final class NeoForgeExtraCodecs {
    /**
     * Map dispatch codec with an alternative.
     *
     * <p>The alternative will only be used if there is no {@code "type"} key in the serialized object.
     *
     * @param typeCodec     codec for the dispatch type
     * @param type          function to retrieve the dispatch type from the dispatched type
     * @param codec         function to retrieve the dispatched type map codec from the dispatch type
     * @param fallbackCodec fallback to use when the deserialized object does not have a {@code "type"} key
     * @param <A>           dispatch type
     * @param <E>           dispatched type
     * @param <B>           fallback type
     */
    public static <A, E, B> MapCodec<Either<E, B>> dispatchMapOrElse(Codec<A> typeCodec, Function<? super E, ? extends A> type,
            Function<? super A, ? extends MapCodec<? extends E>> codec, MapCodec<B> fallbackCodec) {
        return dispatchMapOrElse("type", typeCodec, type, codec, fallbackCodec);
    }

    /**
     * Map dispatch codec with an alternative.
     *
     * <p>The alternative will only be used if the provided key is not present in the serialized object.
     *
     * @param key           key to dispatch on
     * @param typeCodec     codec for the dispatch type
     * @param type          function to retrieve the dispatch type from the dispatched type
     * @param codec         function to retrieve the dispatched type map codec from the dispatch type
     * @param fallbackCodec fallback to use when the deserialized object does not have a {@code "type"} key
     * @param <A>           dispatch type
     * @param <E>           dispatched type
     * @param <B>           fallback type
     */
    public static <A, E, B> MapCodec<Either<E, B>> dispatchMapOrElse(String key, Codec<A> typeCodec, Function<? super E, ? extends A> type,
            Function<? super A, ? extends MapCodec<? extends E>> codec, MapCodec<B> fallbackCodec) {
        var dispatchCodec = typeCodec.dispatchMap(key, type, codec);
        return new MapCodec<>() {
            @Override
            public <T> Stream<T> keys(DynamicOps<T> ops) {
                return Stream.concat(dispatchCodec.keys(ops), fallbackCodec.keys(ops)).distinct();
            }

            @Override
            public <T> DataResult<Either<E, B>> decode(DynamicOps<T> ops, MapLike<T> input) {
                if (input.get(key) != null) {
                    return dispatchCodec.decode(ops, input).map(Either::left);
                } else {
                    return fallbackCodec.decode(ops, input).map(Either::right);
                }
            }

            @Override
            public <T> RecordBuilder<T> encode(Either<E, B> input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                return input.map(
                        dispatched -> dispatchCodec.encode(dispatched, ops, prefix),
                        fallback -> fallbackCodec.encode(fallback, ops, prefix));
            }

            @Override
            public String toString() {
                return "DispatchOrElse[" + dispatchCodec + ", " + fallbackCodec + "]";
            }
        };
    }

    private NeoForgeExtraCodecs() {}
}

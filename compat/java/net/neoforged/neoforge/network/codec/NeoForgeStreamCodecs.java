/*
 * Часть nfcompat. NeoForge-овский NeoForgeStreamCodecs.
 *
 * Перенесено только то подмножество, которое зовёт AE2: CHUNK_POS и enumCodec.
 * Заодно перенесены три безобидные утилиты, у которых нет платформенной
 * специфики (UNBOUNDED_BYTE_ARRAY, lazy, uncheckedUnit) - они целиком
 * на ванильных API.
 *
 * НЕ перенесено сознательно:
 *   registryKey()       - тривиально, но AE2 её не зовёт;
 *   connectionAware()   - в NeoForge выбирает кодек по buf.getConnectionType(),
 *                         а на Fabric поля connectionType в буфере нет, так что
 *                         честно перенести нельзя (любой выбор был бы враньём).
 */
package net.neoforged.neoforge.network.codec;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.ChunkPos;

public final class NeoForgeStreamCodecs {
    private NeoForgeStreamCodecs() {
    }

    /** Читает/пишет byte[] без ограничения длины (в отличие от ByteBufCodecs.BYTE_ARRAY). */
    public static final StreamCodec<FriendlyByteBuf, byte[]> UNBOUNDED_BYTE_ARRAY = new StreamCodec<>() {
        @Override
        public byte[] decode(FriendlyByteBuf buf) {
            return buf.readByteArray();
        }

        @Override
        public void encode(FriendlyByteBuf buf, byte[] data) {
            buf.writeByteArray(data);
        }
    };

    public static final StreamCodec<FriendlyByteBuf, ChunkPos> CHUNK_POS = new StreamCodec<>() {
        @Override
        public ChunkPos decode(FriendlyByteBuf buf) {
            return buf.readChunkPos();
        }

        @Override
        public void encode(FriendlyByteBuf buf, ChunkPos pos) {
            buf.writeChunkPos(pos);
        }
    };

    public static <B, V> StreamCodec<B, V> lazy(Supplier<StreamCodec<B, V>> streamCodecSupplier) {
        return new LazyStreamCodec<>(streamCodecSupplier);
    }

    /**
     * Кодек для enum'а по ordinal'у. Один в один как в NeoForge: пишет
     * writeEnum, читает readEnum, то есть по ordinal, а не по имени.
     */
    public static <B extends FriendlyByteBuf, V extends Enum<V>> StreamCodec<B, V> enumCodec(Class<V> enumClass) {
        return new StreamCodec<>() {
            @Override
            public V decode(B buf) {
                return buf.readEnum(enumClass);
            }

            @Override
            public void encode(B buf, V value) {
                buf.writeEnum(value);
            }
        };
    }

    /** Как {@link StreamCodec#unit}, но без проверки значения при записи. */
    public static <B, V> StreamCodec<B, V> uncheckedUnit(final V defaultValue) {
        return new StreamCodec<>() {
            @Override
            public V decode(B buf) {
                return defaultValue;
            }

            @Override
            public void encode(B buf, V value) {
            }
        };
    }

    private static final class LazyStreamCodec<B, V> implements StreamCodec<B, V> {
        private Supplier<StreamCodec<B, V>> supplier;
        private volatile StreamCodec<B, V> delegate;

        private LazyStreamCodec(Supplier<StreamCodec<B, V>> supplier) {
            this.supplier = supplier;
        }

        private StreamCodec<B, V> delegate() {
            var result = delegate;
            if (result == null) {
                synchronized (this) {
                    result = delegate;
                    if (result == null) {
                        result = supplier.get();
                        if (result == null) {
                            throw new IllegalStateException("StreamCodec supplier вернул null");
                        }
                        delegate = result;
                        supplier = null;
                    }
                }
            }
            return result;
        }

        @Override
        public void encode(B buf, V value) {
            delegate().encode(buf, value);
        }

        @Override
        public V decode(B buf) {
            return delegate().decode(buf);
        }
    }
}

/*
 * Часть nfcompat. Шим net.neoforged.neoforge.attachment.AttachmentType для Fabric.
 *
 * ЧЕСТНО О ТОМ, ЧТО ЭТО ЗАГЛУШКА:
 *  - Умеет только builder(Supplier<T>).build() - ровно то, что использует AE2.
 *  - Ни сериализации (serialize/MapCodec/ValueIOSerializable), ни copyOnDeath,
 *    ни sync(...) тут нет. Если понадобится - дописывать вместе с реальным
 *    хранилищем attachment'ов на Fabric (attachment-API у Fabric нет вообще,
 *    ближайший аналог - Cardinal Components / собственный CompoundTag на Entity).
 *  - AttachmentType'ы регистрируются в "виртуальный" реестр neoforge:attachment_types:
 *    DeferredRegister складывает их в локальную мапу, потому что такого реестра в MC нет.
 *  - Методы getData/setData у Entity/BlockEntity/Level (NeoForge IAttachmentHolder)
 *    этим шимом НЕ покрываются.
 */
package net.neoforged.neoforge.attachment;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Тип данных, которые можно навесить на объект.
 *
 * @param <T> тип значения
 */
public final class AttachmentType<T> {

    private final Supplier<T> defaultValueSupplier;

    private AttachmentType(Builder<T> builder) {
        this.defaultValueSupplier = builder.defaultValueSupplier;
    }

    /** Фабрика билдера (единственная используемая AE2 форма). */
    public static <T> Builder<T> builder(Supplier<T> defaultValueSupplier) {
        return new Builder<>(Objects.requireNonNull(defaultValueSupplier));
    }

    /** Значение по умолчанию для нового держателя. */
    public T defaultValue() {
        return this.defaultValueSupplier.get();
    }

    public Supplier<T> defaultValueSupplier() {
        return this.defaultValueSupplier;
    }

    public static class Builder<T> {
        private final Supplier<T> defaultValueSupplier;

        private Builder(Supplier<T> defaultValueSupplier) {
            this.defaultValueSupplier = defaultValueSupplier;
        }

        public AttachmentType<T> build() {
            return new AttachmentType<>(this);
        }
    }
}

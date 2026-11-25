/*
 * Часть nfcompat. Шим `net.neoforged.neoforge.attachment.AttachmentType`.
 *
 * Сверено с NeoForge 26.3.x (src/main/java/net/neoforged/neoforge/attachment/AttachmentType.java).
 * Что из NeoForge взято как есть:
 *  - `builder(Supplier<T>)` и `builder(Function<IAttachmentHolder, T>)` — обе перегрузки,
 *    причём первая, как в апстриме, делегирует второй (`holder -> supplier.get()`).
 *    Вторая форма нужна ae2wtlib: `AttachmentType.builder(holder -> new CraftingTerminalHandler((Player) holder))`.
 *
 * ЧЕГО НЕТ (и почему):
 *  - `serialize(...)`, `serializable(...)`, `copyOnDeath()`, `copyHandler(...)`, `sync(...)`:
 *    в шиме нет ни `IAttachmentSerializer`, ни `AttachmentSyncHandler`, ни записи в NBT.
 *    Значения attachment'ов живут только в памяти (см. nfcompat.attachments.Attachments).
 *    Если аддон позовёт builder#serialize — будет NoSuchMethodError, это осознанный разрыв.
 *  - `AttachmentType` не регистрируется в настоящем реестре: `DeferredRegister<AttachmentType<?>>`
 *    складывает их в локальную мапу (реестра `neoforge:attachment_types` в MC нет).
 *  - Поля в NeoForge package-private; у нас private + геттеры, т.к. хранилище лежит
 *    в другом пакете (nfcompat.attachments).
 */
package net.neoforged.neoforge.attachment;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

/**
 * Тип данных, которые можно навесить на объект.
 *
 * @param <T> тип значения
 */
public final class AttachmentType<T> {

    /** В NeoForge 26.x это именно функция от holder'а, а не Supplier. */
    private final Function<IAttachmentHolder, T> defaultValueSupplier;

    private AttachmentType(Builder<T> builder) {
        this.defaultValueSupplier = builder.defaultValueSupplier;
    }

    /** Форма без holder'а (AE2: `AttachmentType.builder(() -> false)`). */
    public static <T> Builder<T> builder(Supplier<T> defaultValueSupplier) {
        Objects.requireNonNull(defaultValueSupplier, "defaultValueSupplier");
        return builder(holder -> defaultValueSupplier.get());
    }

    /**
     * Форма с holder'ом: значение может захватить объект, на который его навешивают
     * (ae2wtlib: `CraftingTerminalHandler(Player)`). Внутри принято кастовать holder
     * к нужному подтипу — если тип не тот, конструктор сам кинет исключение.
     */
    public static <T> Builder<T> builder(Function<IAttachmentHolder, T> defaultValueConstructor) {
        return new Builder<>(Objects.requireNonNull(defaultValueConstructor, "defaultValueConstructor"));
    }

    /**
     * Default для нового держателя.
     *
     * В NeoForge публичного метода нет (default создаёт сам держатель), но нам он нужен:
     * хранилище (nfcompat.attachments.Attachments) — отдельный класс.
     *
     * @param holder держатель; {@code null} допустим только для Supplier-формы
     */
    public T defaultValue(@Nullable IAttachmentHolder holder) {
        return this.defaultValueSupplier.apply(holder);
    }

    /** Default без держателя (старый API шима). Для holder-aware типов holder будет null. */
    public T defaultValue() {
        return defaultValue(null);
    }

    public Function<IAttachmentHolder, T> defaultValueSupplier() {
        return this.defaultValueSupplier;
    }

    public static class Builder<T> {
        private final Function<IAttachmentHolder, T> defaultValueSupplier;

        private Builder(Function<IAttachmentHolder, T> defaultValueSupplier) {
            this.defaultValueSupplier = defaultValueSupplier;
        }

        public AttachmentType<T> build() {
            return new AttachmentType<>(this);
        }
    }
}

/*
 * Часть nfcompat. Шим `net.neoforged.neoforge.attachment.IAttachmentHolder`.
 *
 * В NeoForge этот интерфейс навешан на `Entity`, `BlockEntity`, `Level`, `ItemStack`
 * (и на `ChunkAccess`/`SessionData`) прямо в патченной ванили, поэтому чужой код
 * спокойно пишет `player.getData(MY_ATTACHMENT)`. У нас патча нет, поэтому:
 *  - интерфейс навешивается миксинами: nfcompat.mixin.common.EntityAttachmentMixin,
 *    LevelAttachmentMixin, ItemStackAttachmentMixin и (уже существующим)
 *    BlockEntityMixin;
 *  - значения хранит nfcompat.attachments.Attachments (identity + weak).
 *
 * СОЗНАТЕЛЬНЫЕ РАСХОЖДЕНИЯ С NEOFORGE:
 *  1. Методы, которые в NeoForge абстрактные (`hasAttachments`, `hasData`, `getData`,
 *     `getExistingDataOrNull`, `setData`, `removeData`), у нас default и делегируют
 *     в Attachments. Иначе каждый миксин пришлось бы набивать шестью одинаковыми
 *     делегатами, а сторонний класс, решивший реализовать интерфейс сам, — тоже.
 *     Для вызывающего кода разницы нет: набор методов и дескрипторы совпадают.
 *  2. `syncData` — no-op, как и default в NeoForge: синхронизации нет.
 *  3. Нет `IAttachmentSerializer`/`AttachmentSyncHandler` — значит нет и сериализации
 *     с сохранением в NBT/на диск. Всё живёт только в памяти, до выгрузки объекта.
 *  4. `getData` в NeoForge СОХРАНЯЕТ созданный default; у нас так же (см. Attachments.get).
 *
 * Чего не хватает аддонам из замера: ae2wtlib зовёт
 * `AttachmentType.builder(Function<IAttachmentHolder, T>)`, а в holder-aware конструктор
 * (`new CraftingTerminalHandler((Player) holder)`) приходит именно `Player` - поэтому
 * `Entity` обязан реализовывать этот интерфейс, иначе checkcast в лямбде даст CCE.
 */
package net.neoforged.neoforge.attachment;

import java.util.Optional;
import java.util.function.Supplier;

import nfcompat.attachments.Attachments;
import org.jspecify.annotations.Nullable;

/**
 * Объект, которому можно навесить data attachment'ы.
 */
public interface IAttachmentHolder {
    /** Есть ли хоть один attachment (в NeoForge — абстрактный метод). */
    default boolean hasAttachments() {
        return Attachments.hasAny(this);
    }

    default boolean hasData(AttachmentType<?> type) {
        return Attachments.has(this, type);
    }

    /**
     * Неиспользуемый generic `T` нужен, чтобы `DeferredHolder<AttachmentType<T>, ...>`
     * подходил под `Supplier` — ровно как в NeoForge.
     */
    default <T> boolean hasData(Supplier<AttachmentType<T>> type) {
        return hasData(type.get());
    }

    /** Если значения нет — default создаётся и сохраняется (как в NeoForge). */
    default <T> T getData(AttachmentType<T> type) {
        return Attachments.get(this, type);
    }

    default <T> T getData(Supplier<AttachmentType<T>> type) {
        return getData(type.get());
    }

    default <T> Optional<T> getExistingData(AttachmentType<T> type) {
        return Optional.ofNullable(getExistingDataOrNull(type));
    }

    default <T> Optional<T> getExistingData(Supplier<AttachmentType<T>> type) {
        return getExistingData(type.get());
    }

    @Nullable
    default <T> T getExistingDataOrNull(AttachmentType<T> type) {
        return Attachments.getExisting(this, type);
    }

    @Nullable
    default <T> T getExistingDataOrNull(Supplier<AttachmentType<T>> type) {
        return getExistingDataOrNull(type.get());
    }

    /** @return предыдущее значение или {@code null} */
    @Nullable
    default <T> T setData(AttachmentType<T> type, T data) {
        return Attachments.set(this, type, data);
    }

    @Nullable
    default <T> T setData(Supplier<AttachmentType<T>> type, T data) {
        return setData(type.get(), data);
    }

    /** @return снятое значение или {@code null} */
    @Nullable
    default <T> T removeData(AttachmentType<T> type) {
        return Attachments.remove(this, type);
    }

    @Nullable
    default <T> T removeData(Supplier<AttachmentType<T>> type) {
        return removeData(type.get());
    }

    /**
     * В NeoForge синхронизирует attachment с клиентами. У нас канала нет,
     * поэтому no-op — как и default-реализация в самом NeoForge.
     */
    default void syncData(AttachmentType<?> type) {
    }

    default void syncData(Supplier<? extends AttachmentType<?>> type) {
        syncData(type.get());
    }
}

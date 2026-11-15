/*
 * Часть nfcompat. Данные, привязанные к сущности.
 *
 * NeoForge патчит `Entity`/`BlockEntity`/`Level` до `IAttachmentHolder`, добавляя
 * `getData(AttachmentType)` / `setData(AttachmentType, value)`. На Fabric такого нет,
 * поэтому значения хранятся здесь: слабая ссылка на носитель -> карта тип->значение.
 *
 * Ограничения, которые надо помнить:
 *  - значения не сериализуются (в NeoForge это делает `AttachmentType#serialize`);
 *  - не синхронизируются с клиентом (`sync(...)`);
 *  - не переживают выгрузку сущности, если она выгружена и создана заново.
 * Для `AEAttachmentTypes.HOLDING_CTRL` (флаг «зажат Ctrl») этого достаточно.
 */
package nfcompat.attachments;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

import net.neoforged.neoforge.attachment.AttachmentType;

public final class Attachments {
    private static final Map<Object, Map<AttachmentType<?>, Object>> VALUES = Collections
            .synchronizedMap(new WeakHashMap<>());

    private Attachments() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(Object holder, Supplier<AttachmentType<T>> type) {
        return get(holder, type.get());
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(Object holder, AttachmentType<T> type) {
        synchronized (VALUES) {
            var map = VALUES.get(holder);
            if (map != null && map.containsKey(type)) {
                return (T) map.get(type);
            }
        }
        return type.defaultValue();
    }

    public static <T> void set(Object holder, Supplier<AttachmentType<T>> type, T value) {
        set(holder, type.get(), value);
    }

    public static <T> void set(Object holder, AttachmentType<T> type, T value) {
        synchronized (VALUES) {
            VALUES.computeIfAbsent(holder, k -> new java.util.HashMap<>()).put(type, value);
        }
    }
}

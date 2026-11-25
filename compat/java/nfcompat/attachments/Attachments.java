/*
 * Часть nfcompat. Хранилище attachment'ов — бэкенд для шима
 * `net.neoforged.neoforge.attachment.IAttachmentHolder`.
 *
 * ЧТО ЗДЕСЬ ПРОИСХОДИТ:
 *  - NeoForge патчит `Entity`/`BlockEntity`/`Level`/`ItemStack` до `IAttachmentHolder`,
 *    у нас этого патча нет, поэтому интерфейс навешивается миксинами
 *    (`nfcompat.mixin.common.*AttachmentMixin`), а значения живут в этой мапе.
 *  - Ключ — САМ ОБЪЕКТ (identity), слабая ссылка. Слабая, потому что holder'ов
 *    (особенно `ItemStack`) создаются миллионы, и держать их вечно нельзя.
 *    Именно identity, а не equals: у `ItemStack.equals` сравнивает содержимое, и
 *    два одинаковых по содержимому стака делили бы attachment'ы — в NeoForge так не бывает.
 *
 * ЧЕГО НЕТ (сознательно, см. также комментарий в IAttachmentHolder):
 *  - сериализации: в NeoForge её делает `AttachmentType#serialize`;
 *  - синхронизации с клиентом (`sync(...)`): нужен свой payload + канал;
 *  - копирования при copyOnDeath/копировании стака.
 *  Для `AEAttachmentTypes.HOLDING_CTRL` (флаг «зажат Ctrl») и для default-значений
 *  вроде `CraftingTerminalHandler` из ae2wtlib этого достаточно.
 *
 * ПОЧЕМУ НЕ `fabric-data-attachment-api-v1`:
 *  - `AttachmentRegistry.create(...)` требует глобальный `Identifier` на каждый тип,
 *    у NF-ного `AttachmentType` идентификатора нет вообще (он живёт в «виртуальном»
 *    реестре DeferredRegister'а), пришлось бы выдумывать синтетические id;
 *  - у Fabric `initializer` — это `Supplier`, без holder'а, а NeoForge-ная форма
 *    `AttachmentType.builder(Function<IAttachmentHolder, T>)` holder как раз передаёт
 *    (так делает ae2wtlib: `new CraftingTerminalHandler((Player) holder)`);
 *  - регистрация в Fabric API задумана на этапе инициализации мода, а lazy-регистрация
 *    по первому `getData` — уже не тот контракт.
 *  Поэтому: своё хранилище + миксин-интерфейс. Это на один класс больше, зато без
 *  синтетических id, фазовых проверок и двойного учёта.
 */
package nfcompat.attachments;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import org.jspecify.annotations.Nullable;

public final class Attachments {
    /** Ключи, чьи holder'ы уже собраны GC. Чистим при каждом обращении. */
    private static final ReferenceQueue<Object> STALE_KEYS = new ReferenceQueue<>();
    private static final Map<IdentityWeakKey, Map<AttachmentType<?>, Object>> VALUES = new HashMap<>();

    private Attachments() {
    }

    // ------------------------------------------------------------------ чтение

    public static <T> T get(Object holder, Supplier<AttachmentType<T>> type) {
        return get(holder, type.get());
    }

    /**
     * NeoForge-ная семантика: если значения нет, default СОЗДАЁТСЯ И СОХРАНЯЕТСЯ.
     * Это важно для значений, которые потом мутируют на месте.
     */
    @SuppressWarnings("unchecked")
    public static <T> T get(Object holder, AttachmentType<T> type) {
        synchronized (VALUES) {
            var map = mapFor(holder);
            if (map.containsKey(type)) {
                return (T) map.get(type);
            }
        }

        // Default считаем ВНЕ лока: конструктор значения (например,
        // CraftingTerminalHandler) может сам полезть в getData и повесить лок.
        var value = type.defaultValue(asHolder(holder));
        synchronized (VALUES) {
            var map = mapFor(holder);
            var existing = map.get(type);
            if (existing != null || map.containsKey(type)) {
                // Пока мы считали default, кто-то успел положить значение - уважаем его.
                return (T) existing;
            }
            map.put(type, value);
        }
        return value;
    }

    /** Значение без создания default'а (`IAttachmentHolder#getExistingDataOrNull`). */
    @SuppressWarnings("unchecked")
    @Nullable
    public static <T> T getExisting(Object holder, AttachmentType<T> type) {
        synchronized (VALUES) {
            var map = VALUES.get(lookupKey(holder));
            return map == null ? null : (T) map.get(type);
        }
    }

    public static boolean has(Object holder, AttachmentType<?> type) {
        synchronized (VALUES) {
            var map = VALUES.get(lookupKey(holder));
            return map != null && map.containsKey(type);
        }
    }

    public static boolean hasAny(Object holder) {
        synchronized (VALUES) {
            var map = VALUES.get(lookupKey(holder));
            return map != null && !map.isEmpty();
        }
    }

    // ------------------------------------------------------------------ запись

    public static <T> @Nullable T set(Object holder, Supplier<AttachmentType<T>> type, T value) {
        return set(holder, type.get(), value);
    }

    /** @return предыдущее значение, как в NeoForge */
    @SuppressWarnings("unchecked")
    @Nullable
    public static <T> T set(Object holder, AttachmentType<T> type, T value) {
        synchronized (VALUES) {
            return (T) mapFor(holder).put(type, value);
        }
    }

    /** @return снятое значение, как в NeoForge */
    @SuppressWarnings("unchecked")
    @Nullable
    public static <T> T remove(Object holder, AttachmentType<T> type) {
        synchronized (VALUES) {
            var map = mapFor(holder);
            var previous = (T) map.remove(type);
            if (map.isEmpty()) {
                VALUES.remove(lookupKey(holder));
            }
            return previous;
        }
    }

    // ---------------------------------------------------------------- служебное

    private static Map<AttachmentType<?>, Object> mapFor(Object holder) {
        drainStaleKeys();
        var map = VALUES.get(lookupKey(holder));
        if (map == null) {
            map = new HashMap<>();
            VALUES.put(new IdentityWeakKey(holder, STALE_KEYS), map);
        }
        return map;
    }

    /**
     * Ключ только для поиска: в очередь не регистрируется и живёт до конца вызова.
     * Ссылка на holder в этот момент сильная (он аргумент), так что referent не умрёт.
     */
    private static IdentityWeakKey lookupKey(Object holder) {
        return new IdentityWeakKey(holder, null);
    }

    private static void drainStaleKeys() {
        Object ref;
        while ((ref = STALE_KEYS.poll()) != null) {
            VALUES.remove(ref);
        }
    }

    /**
     * В NeoForge holder всегда `IAttachmentHolder`. У нас метод `Attachments.get` зовут
     * и из кода, который про интерфейс не знает (`UpdateHoldingCtrlPacket`), поэтому
     * принимаем `Object`; если объект интерфейс не реализует - передаём null, и
     * holder-aware default'ы должны это пережить (у Supplier-форм holder не нужен вообще).
     */
    @Nullable
    private static IAttachmentHolder asHolder(Object holder) {
        return holder instanceof IAttachmentHolder h ? h : null;
    }

    /** Слабая ссылка с identity-равенством. */
    private static final class IdentityWeakKey extends WeakReference<Object> {
        private final int hash;

        IdentityWeakKey(Object referent, @Nullable ReferenceQueue<Object> queue) {
            super(referent, queue);
            this.hash = System.identityHashCode(referent);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof IdentityWeakKey other)) {
                return false;
            }
            var mine = get();
            var theirs = other.get();
            // Обе ссылки уже очищены - считаем ключи равными, чтобы чистка из очереди
            // STALE_KEYS могла снять запись (оба объекта всё равно мусор).
            return mine == null ? theirs == null : mine == theirs;
        }
    }
}

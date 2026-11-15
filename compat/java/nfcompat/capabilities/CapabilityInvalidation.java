/*
 * Часть nfcompat. Инвалидация кэшей capability.
 *
 * NeoForge патчит `BlockEntity` и `Level`, добавляя `invalidateCapabilities()`: это сигнал
 * «провайдеры для этого объекта/позиции могли измениться». Нужен он потому, что у NeoForge
 * есть кэш (`BlockCapabilityCache`). У нас кэша нет — каждый запрос идёт в
 * `BlockCapability.getCapability(...)` напрямую, — поэтому инвалидировать нечего.
 *
 * Если позже появится кэш (см. TODO в `ModelDataHooks`), сюда придёт его сброс.
 */
package nfcompat.capabilities;

import org.jetbrains.annotations.Nullable;

public final class CapabilityInvalidation {
    private CapabilityInvalidation() {
    }

    public static void invalidate(@Nullable Object holder) {
        // no-op: кэша нет
    }

    /**
     * NeoForge позволяет подписаться на изменение capability у позиции
     * (`ServerLevel#registerCapabilityListener`). Кэша нет — слушать нечего,
     * поэтому вызов принимается и игнорируется.
     */
    public static void registerListener(Object level, Object pos, @Nullable Object listener) {
        // no-op
    }
}

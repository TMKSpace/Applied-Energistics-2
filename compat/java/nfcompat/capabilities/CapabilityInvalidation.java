/*
 * Часть nfcompat. Инвалидация кэшей capability — осознанно пустая.
 *
 * NeoForge патчит `BlockEntity` и `Level`, добавляя `invalidateCapabilities()`: это сигнал
 * «провайдеры для этого объекта/позиции могли измениться». Нужен он ровно для кэша:
 * `BlockCapabilityCache` держит найденный хендлер и сбрасывает его по этому сигналу.
 *
 * У нас кэша нет вовсе: `BlockCapabilityCache.getCapability()` каждый раз спрашивает
 * `BlockCapability.getCapability(level, pos, null, null, context)` — как Fabric
 * `BlockApiLookup`, у которого кэша тоже нет (см. javadoc `BlockCapabilityCache`).
 * Значит устаревших хендлеров не бывает по построению, и инвалидировать нечего.
 *
 * Методы оставлены и вызываются из `src/` (`InterfaceLogic`, `CableBusContainer`,
 * `CapabilityP2PTunnelPart`, `StorageBusPart`) в точках «здесь состояние изменилось».
 * Вызовы — no-op осознанно, а не «забыли реализовать»: если однажды для производительности
 * понадобится кэш, его сброс придёт именно сюда и вызывающий код менять не придётся.
 */
package nfcompat.capabilities;

import org.jetbrains.annotations.Nullable;

public final class CapabilityInvalidation {
    private CapabilityInvalidation() {
    }

    /**
     * NeoForge-ный `invalidateCapabilities()` для объекта-держателя (обычно block entity).
     *
     * <p>У нас no-op: кэша capability нет, каждый запрос идёт в провайдеры напрямую, поэтому
     * сбрасывать нечего.
     */
    public static void invalidate(@Nullable Object holder) {
        // no-op: кэша нет
    }

    /**
     * NeoForge позволяет подписаться на изменение capability у позиции
     * (`ServerLevel#registerCapabilityListener`). Кэша нет — слушать нечего: подписчик всё
     * равно спросит актуальное состояние в свой следующий тик. Вызов принимается и
     * игнорируется.
     */
    public static void registerListener(Object level, Object pos, @Nullable Object listener) {
        // no-op
    }
}

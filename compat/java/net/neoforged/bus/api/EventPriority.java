package net.neoforged.bus.api;

/**
 * Приоритет слушателя события. Порядок важен: HIGHEST вызывается первым.
 */
public enum EventPriority {
    HIGHEST,
    HIGH,
    NORMAL,
    LOW,
    LOWEST
}

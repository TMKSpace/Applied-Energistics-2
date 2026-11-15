package net.neoforged.bus.api;

import java.io.Serializable;
import java.util.function.Consumer;

/**
 * Слушатель события. Обязан быть Serializable — тогда компилятор генерирует
 * writeReplace(), из которого мы достаём фактический тип события.
 */
@FunctionalInterface
public interface EventListener<T extends Event> extends Consumer<T>, Serializable {
}

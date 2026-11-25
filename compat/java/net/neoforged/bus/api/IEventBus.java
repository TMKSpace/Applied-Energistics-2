/*
 * Часть nfcompat. Интерфейс шины событий в стиле NeoForge.
 *
 * Отличие от NeoForge: у нас слушатель - не просто Consumer, а EventListener
 * (Serializable-наследник Consumer). Это нужно, чтобы вытащить тип события
 * из лямбды/ссылки на метод (через SerializedLambda) и разложить слушателей
 * по классам событий.
 *
 * Перегрузки с java.util.function.Consumer - как в NeoForge: ими пользуются
 * сторонние библиотеки (например GuideME), где тип события известен только
 * компилятору. Внутри такие слушатели регистрируются как "неопознанные" и
 * привязываются к классу события при первой удачной доставке.
 */
package net.neoforged.bus.api;

import java.util.function.Consumer;

public interface IEventBus {

    <T extends Event> void addListener(EventListener<T> listener);

    <T extends Event> void addListener(EventPriority priority, EventListener<T> listener);

    /** Слушатель без явного типа события: тип определяется при первой доставке. */
    <T extends Event> void addListener(Consumer<T> listener);

    <T extends Event> void addListener(EventPriority priority, Consumer<T> listener);

    /** Слушатель с явно указанным классом события. */
    <T extends Event> void addListener(Class<T> eventType, Consumer<T> listener);

    <T extends Event> void addListener(EventPriority priority, Class<T> eventType, Consumer<T> listener);

    /**
     * Подписывает объект: instance — методы с {@link SubscribeEvent} (с иерархией классов),
     * {@link Class} — только static-методы класса, {@link java.lang.reflect.Method} — один
     * static-метод. Набор форм — как у NeoForge.
     */
    void register(Object target);

    <T extends Event> T post(T event);

    /** Снимает слушателей, подписанных через {@link #register(Object)}. */
    void unregister(Object target);
}

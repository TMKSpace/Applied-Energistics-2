/*
 * Часть nfcompat. Интерфейс шины событий в стиле NeoForge.
 *
 * Отличие от NeoForge: слушатель — не голый Consumer, а EventListener
 * (Serializable-наследник Consumer). Это нужно, чтобы вытащить тип события
 * из лямбды/ссылки на метод (через SerializedLambda) и разложить слушателей
 * по классам событий.
 */
package net.neoforged.bus.api;

public interface IEventBus {

    <T extends Event> void addListener(EventListener<T> listener);

    <T extends Event> void addListener(EventPriority priority, EventListener<T> listener);

    /** Регистрирует все методы с {@link SubscribeEvent} у объекта. */
    void register(Object target);

    <T extends Event> T post(T event);
}

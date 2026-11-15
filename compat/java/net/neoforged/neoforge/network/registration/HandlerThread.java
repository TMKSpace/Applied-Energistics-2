/*
 * Часть nfcompat. NeoForge-овский HandlerThread.
 *
 * В NeoForge решает, в каком потоке звать обработчик: MAIN (по умолчанию,
 * обработчик оборачивается в MainThreadPayloadHandler) или NETWORK.
 * Здесь семантика та же, только обёртки нет - PayloadRegistrar сам решает,
 * кидать задачу в главный поток или нет.
 */
package net.neoforged.neoforge.network.registration;

public enum HandlerThread {
    /** Главный поток игры (серверный или клиентский). */
    MAIN,

    /** Сетевой поток (netty). */
    NETWORK
}

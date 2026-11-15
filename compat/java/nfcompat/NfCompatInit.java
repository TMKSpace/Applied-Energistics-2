/*
 * Часть nfcompat. Точка входа шима на Fabric.
 *
 * Задачи на этом этапе ровно две:
 * 1. Поймать группу серверного потока и положить её в SidedThreadGroups.SERVER.
 *    AE2 сравнивает Thread.currentThread().getThreadGroup() == SidedThreadGroups.SERVER,
 *    а ThreadGroup у живого потока не подменить — значит её надо именно поймать.
 * 2. Держать ссылку на текущий сервер для ServerLifecycleHooks.
 *
 * Точки входа самого AE2 (NeoForge-овские @Mod-конструкторы) пока не мостим:
 * это следующий этап.
 */
package nfcompat;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.neoforged.fml.util.thread.SidedThreadGroups;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public final class NfCompatInit implements DedicatedServerModInitializer, ClientModInitializer {

    @Override
    public void onInitializeServer() {
        common();
    }

    @Override
    public void onInitializeClient() {
        common();
        // На клиенте серверного потока нет: в интеграционном сервере он появится
        // при открытии мира, и тогда его поймает SERVER_STARTING из common().
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            ServerLifecycleHooks.setCurrentServer(null);
        });
    }

    private static void common() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            SidedThreadGroups.setServerThreadGroup(Thread.currentThread().getThreadGroup());
            ServerLifecycleHooks.setCurrentServer(server);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            ServerLifecycleHooks.setCurrentServer(null);
        });
    }
}

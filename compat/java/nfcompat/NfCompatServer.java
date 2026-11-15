/*
 * Часть nfcompat. Точка входа сервера.
 *
 * Отдельный класс от клиентского, чтобы на выделенном сервере не грузились
 * клиентские классы AE2 (AppEngClient тянет рендер и GUI).
 */
package nfcompat;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.neoforged.fml.util.thread.SidedThreadGroups;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import appeng.core.AppEngServer;

public final class NfCompatServer implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            SidedThreadGroups.setServerThreadGroup(Thread.currentThread().getThreadGroup());
            ServerLifecycleHooks.setCurrentServer(server);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ServerLifecycleHooks.setCurrentServer(null));

        // Конструктор мода: он вешает слушателей и делает первичную регистрацию.
        new AppEngServer(NeoForge.EVENT_BUS, NfCompat.container());
        // То, что на NeoForge делает загрузчик.
        NfCompat.runRegistrationPhases();
    }
}

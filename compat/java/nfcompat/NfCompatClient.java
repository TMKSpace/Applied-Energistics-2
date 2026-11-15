/*
 * Часть nfcompat. Точка входа клиента.
 *
 * AppEngClient наследует AppEngBase, поэтому его конструктор делает и общую часть.
 * После него добавляем клиентский этап инициализации и регистрацию клиентских каналов.
 */
package nfcompat;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.neoforged.fml.DeferredWorkQueue;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import appeng.client.AppEngClient;

public final class NfCompatClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ServerLifecycleHooks.setCurrentServer(null));

        new AppEngClient(NeoForge.EVENT_BUS, NfCompat.container());

        NeoForge.EVENT_BUS.post(new RegisterClientPayloadHandlersEvent());

        var queue = new DeferredWorkQueue();
        NeoForge.EVENT_BUS.post(new FMLClientSetupEvent(NfCompat.container(), queue));
        for (Runnable work : queue.drain()) {
            work.run();
        }

        NfCompat.runRegistrationPhases();
    }
}

/*
 * Часть nfcompat. Точка входа клиента.
 *
 * AppEngClient наследует AppEngBase, поэтому его конструктор делает и общую часть.
 * Порядок важен: типы пакетов регистрируются в общем этапе
 * (`RegisterPayloadHandlersEvent`), а клиентские обработчики
 * (`RegisterClientPayloadHandlersEvent`) ссылаются на уже известные типы — иначе
 * NeoForge'вский PayloadRegistrar падает с «no payload type has been registered».
 */
package nfcompat;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.neoforged.fml.DeferredWorkQueue;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import appeng.client.AppEngClient;

public final class NfCompatClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ServerLifecycleHooks.setCurrentServer(null));

        new AppEngClient(NeoForge.EVENT_BUS, NfCompat.container());

        // 1. Общие этапы: регистрация реестров, типов пакетов, capability.
        NfCompat.runRegistrationPhases();

        // 2. Клиентские обработчики — только после того, как типы пакетов известны.
        NeoForge.EVENT_BUS.post(new RegisterClientPayloadHandlersEvent());

        // 2b. Фабрики клиентских тултипов. NeoForge'вский ClientTooltipComponent.create не
        //     знает чужих типов, а у Fabric для этого есть официальный
        //     TooltipComponentCallback (так же сделано в порте AE2 под 1.20.1).
        NeoForge.EVENT_BUS.post(new RegisterClientTooltipComponentFactoriesEvent(
                nfcompat.client.TooltipFactories.FACTORIES));

        // 3. Клиентская инициализация.
        var queue = new DeferredWorkQueue();
        NeoForge.EVENT_BUS.post(new FMLClientSetupEvent(NfCompat.container(), queue));
        for (Runnable work : queue.drain()) {
            work.run();
        }
    }
}

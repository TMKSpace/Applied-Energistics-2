/*
 * Часть nfcompat. NeoForge-овское событие регистрации КЛИЕНТСКИХ обработчиков.
 *
 * В NeoForge clientbound-payload'ы регистрируются без обработчика (на сервере
 * он не нужен), а сам обработчик вешается отдельно на клиенте через это
 * событие. На Fabric это ClientPlayNetworking.registerGlobalReceiver, куда
 * здесь и уходит регистрация.
 *
 * Публикуется тем же mod event bus'ом, что и RegisterPayloadHandlersEvent
 * (см. NetworkPayloadBootstrap).
 */
package net.neoforged.neoforge.client.network.event;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class RegisterClientPayloadHandlersEvent extends Event implements IModBusEvent {
    public RegisterClientPayloadHandlersEvent() {
    }

    /** Регистрирует клиентский обработчик, вызываемый в главном потоке клиента. */
    public <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type, IPayloadHandler<T> handler) {
        register(type, HandlerThread.MAIN, handler);
    }

    /** Регистрирует клиентский обработчик, вызываемый в указанном потоке. */
    public <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type, HandlerThread thread,
            IPayloadHandler<T> handler) {
        PayloadRegistrar.registerClientHandler(type, handler, thread);
    }
}

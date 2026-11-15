/*
 * Часть nfcompat. Хук, который публикует сетевые события NeoForge.
 *
 * Зачем он нужен: в NeoForge события RegisterPayloadHandlersEvent и
 * RegisterClientPayloadHandlersEvent публикует сам загрузчик, до открытия
 * соединений. На Fabric такого этапа нет - каналы надо распихивать в
 * PayloadTypeRegistry во время инициализации мода, иначе отправить payload
 * будет нечем. AE2 подписывается на эти события в конструкторе своего @Mod
 * (AppEngBase/AppEngClient), поэтому достаточно опубликовать их на mod
 * event bus'е мода, и PayloadRegistrar разложит всё по Fabric API.
 *
 * Кто обязан это позвать: тот, кто будет мостить вход в @Mod-конструктор AE2
 * (сейчас такого моста нет - NfCompatInit только держит сервер). Одна строка:
 *
 *     NetworkPayloadBootstrap.fire(container.getEventBus());
 *
 * Если позвать не позовут, компиляция не пострадает, но на первом же
 * PacketDistributor/ClientPacketDistributor в лог упадёт внятная ошибка
 * "Payload ... is not registered" с id payload'а.
 */
package net.neoforged.neoforge.network;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NetworkPayloadBootstrap {
    private static final Logger LOG = LoggerFactory.getLogger("nfcompat/network");

    private NetworkPayloadBootstrap() {
    }

    /** Публикует оба события на mod event bus'е мода. Повторный вызов безопасен. */
    public static void fire(IEventBus modBus) {
        int before = PayloadRegistrar.registeredTypeCount();
        modBus.post(new RegisterPayloadHandlersEvent());
        ClientHook.fireClientHandlers(modBus);
        int after = PayloadRegistrar.registeredTypeCount();
        LOG.info("nfcompat: зарегистрировано {} payload-типов ({} новых)", after, after - before);
    }

    /**
     * Клиентское событие в отдельном вложенном классе: на выделенном сервере
     * RegisterClientPayloadHandlersEvent не должен даже загружаться.
     */
    private static final class ClientHook {
        private ClientHook() {
        }

        static void fireClientHandlers(IEventBus modBus) {
            if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
                return;
            }
            modBus.post(new net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent());
        }
    }
}

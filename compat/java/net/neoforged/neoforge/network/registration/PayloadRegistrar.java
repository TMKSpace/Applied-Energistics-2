/*
 * Часть nfcompat. NeoForge-овский PayloadRegistrar поверх Fabric networking.
 *
 * Что здесь по-настоящему, а что нет.
 *
 * По-настоящему: метод регистрации сразу и окончательно кладёт тип payload'а
 * в Fabric-овский PayloadTypeRegistry (serverboundPlay / clientboundPlay) и,
 * если обработчик передан, вешает его в ServerPlayNetworking /
 * ClientPlayNetworking как глобальный receiver. Это ровно тот момент, в
 * который NeoForge тоже пишет в свой NetworkRegistry, так что "когда вызывать"
 * совпадает: во время инициализации мода, из события
 * RegisterPayloadHandlersEvent.
 *
 * Чего нет:
 *   - версия канала. В NeoForge она участвует в переговорах и несовпадение
 *     рвёт коннект. У Fabric каналов с версиями нет вообще, поэтому version
 *     запоминается и ни на что не влияет (как и в NeoForge для "не-Neo"
 *     соединений - там версия тоже игнорируется);
 *   - optional(). В NeoForge помечает канал как необязательный при нехватке
 *     payload'ов у другой стороны. Fabric сам по себе так не умеет:
 *     незарегистрированный канал просто не отправляется. Метод оставлен,
 *     чтобы код собирался, и ничего не делает;
 *   - конфигурационная фаза (configurationToClient/Server, commonTo*).
 *     AE2 их не зовёт, а Fabric-овский configuration-фаза живёт в отдельных
 *     реестрах, которые тут пришлось бы держать раздельно. Если понадобится -
 *     добавлять вместе с реальным использованием.
 *   - negotiate-логика NeoForge (ModdedNetworkPayload и т.п.) - её на Fabric
 *     заменяет сам Fabric API.
 */
package net.neoforged.neoforge.network.registration;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.handling.ServerPayloadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PayloadRegistrar {
    private static final Logger LOG = LoggerFactory.getLogger("nfcompat/network");

    /**
     * Идентификаторы уже зарегистрированных типов. В NeoForge повторная
     * регистрация того же типа - ошибка; Fabric молча перезапишет, поэтому
     * ловим сами.
     */
    private static final Set<String> REGISTERED = ConcurrentHashMap.newKeySet();

    private String version;
    private boolean optional = false;
    private HandlerThread thread = HandlerThread.MAIN;

    public PayloadRegistrar(String version) {
        this.version = version;
    }

    private PayloadRegistrar(PayloadRegistrar source) {
        this.version = source.version;
        this.optional = source.optional;
        this.thread = source.thread;
    }

    /** nfcompat: сколько типов payload'ов уже зарегистрировано (для диагностики). */
    public static int registeredTypeCount() {
        return REGISTERED.size();
    }

    /** Клиентский payload фазы PLAY без обработчика (его повесят позже через RegisterClientPayloadHandlersEvent). */
    public <T extends CustomPacketPayload> PayloadRegistrar playToClient(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        registerType(type, codec, false, true);
        return this;
    }

    /** Клиентский payload фазы PLAY сразу с обработчиком. */
    public <T extends CustomPacketPayload> PayloadRegistrar playToClient(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, IPayloadHandler<T> handler) {
        registerType(type, codec, false, true);
        registerClientHandler(type, handler);
        return this;
    }

    /** Серверный payload фазы PLAY. */
    public <T extends CustomPacketPayload> PayloadRegistrar playToServer(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, IPayloadHandler<T> handler) {
        registerType(type, codec, true, false);
        registerServerHandler(type, handler);
        return this;
    }

    /**
     * Двунаправленный payload фазы PLAY. Клиентский обработчик в NeoForge
     * регистрируется отдельно (RegisterClientPayloadHandlersEvent), поэтому
     * здесь его нет.
     */
    public <T extends CustomPacketPayload> PayloadRegistrar playBidirectional(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, IPayloadHandler<T> serverHandler) {
        registerType(type, codec, true, true);
        registerServerHandler(type, serverHandler);
        return this;
    }

    /** Копия регистратора с другим потоком обработчиков. */
    public PayloadRegistrar executesOn(HandlerThread thread) {
        var clone = new PayloadRegistrar(this);
        clone.thread = thread;
        return clone;
    }

    /** Копия регистратора с другой версией. На Fabric версия ни на что не влияет. */
    public PayloadRegistrar versioned(String version) {
        var clone = new PayloadRegistrar(this);
        clone.version = version;
        return clone;
    }

    /** Копия регистратора с включённым optional. На Fabric флаг не поддержан (см. шапку файла). */
    public PayloadRegistrar optional() {
        var clone = new PayloadRegistrar(this);
        clone.optional = true;
        return clone;
    }

    private static <T extends CustomPacketPayload> void registerType(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, boolean serverbound, boolean clientbound) {
        var id = type.id();
        if (!REGISTERED.add(id.toString())) {
            LOG.error("Payload {} регистрируется повторно, NeoForge на этом падает; Fabric перезапишет молча.", id);
        }
        try {
            if (serverbound) {
                PayloadTypeRegistry.serverboundPlay().register(type, codec);
            }
            if (clientbound) {
                PayloadTypeRegistry.clientboundPlay().register(type, codec);
            }
        } catch (RuntimeException e) {
            // Сюда попадаем, если регистрация случается уже после заморозки
            // реестра (например, событие пришло из мира, а не из инициализации).
            LOG.error("Не удалось зарегистрировать payload {} в Fabric PayloadTypeRegistry", id, e);
        }
    }

    private <T extends CustomPacketPayload> void registerServerHandler(CustomPacketPayload.Type<T> type,
            IPayloadHandler<T> handler) {
        if (handler == null) {
            return;
        }
        boolean onMainThread = this.thread == HandlerThread.MAIN;
        boolean fresh = ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) -> {
            var payloadContext = new ServerPayloadContext(context.player());
            if (onMainThread) {
                context.server().execute(() -> handler.handle(payload, payloadContext));
            } else {
                handler.handle(payload, payloadContext);
            }
        });
        if (!fresh) {
            LOG.error("Обработчик для payload {} уже был зарегистрирован на сервере", type.id());
        }
    }

    private <T extends CustomPacketPayload> void registerClientHandler(CustomPacketPayload.Type<T> type,
            IPayloadHandler<T> handler) {
        registerClientHandler(type, handler, this.thread);
    }

    /**
     * Форма для RegisterClientPayloadHandlersEvent. Вынесено в отдельный
     * внутренний класс, чтобы main-часть шима не держала постоянных ссылок на
     * client-only классы Fabric API.
     */
    public static <T extends CustomPacketPayload> void registerClientHandler(CustomPacketPayload.Type<T> type,
            IPayloadHandler<T> handler, HandlerThread thread) {
        ClientBridge.register(type, handler, thread == HandlerThread.MAIN);
    }

    private static final class ClientBridge {
        private ClientBridge() {
        }

        static <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type,
                IPayloadHandler<T> handler, boolean onMainThread) {
            if (net.fabricmc.loader.api.FabricLoader.getInstance()
                    .getEnvironmentType() != net.fabricmc.api.EnvType.CLIENT) {
                // Как и в NeoForge: клиентский обработчик на сервере просто не нужен.
                LOG.warn("Клиентский обработчик payload {} пропущен: это не клиент", type.id());
                return;
            }
            boolean fresh = net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(type,
                    (payload, context) -> {
                        var payloadContext = new net.neoforged.neoforge.client.network.handling.ClientPayloadContext(
                                context.player());
                        if (onMainThread) {
                            context.client().execute(() -> handler.handle(payload, payloadContext));
                        } else {
                            handler.handle(payload, payloadContext);
                        }
                    });
            if (!fresh) {
                LOG.error("Обработчик для payload {} уже был зарегистрирован на клиенте", type.id());
            }
        }
    }
}

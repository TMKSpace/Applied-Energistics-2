/*
 * Часть nfcompat. NeoForge-овский ClientPacketDistributor поверх Fabric networking.
 *
 * Один в один по смыслу: отправить payload'ы на сервер. Отличие только в том,
 * что NeoForge пихает их в ClientPacketListener, а Fabric - через
 * ClientPlayNetworking.send.
 *
 * Ошибка (нет соединения, канал не зарегистрирован) не глотается, а логируется
 * вместе с id payload'а, чтобы было видно, чего именно не хватает.
 */
package net.neoforged.neoforge.client.network;

import java.util.Objects;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ClientPacketDistributor {
    private static final Logger LOG = LoggerFactory.getLogger("nfcompat/network");

    private ClientPacketDistributor() {
    }

    /** Отправить payload(ы) на сервер. */
    public static void sendToServer(CustomPacketPayload payload, CustomPacketPayload... payloads) {
        Objects.requireNonNull(payload, "Cannot send null payload");
        try {
            ClientPlayNetworking.send(payload);
            for (var other : payloads) {
                Objects.requireNonNull(other, "Cannot send null payload");
                ClientPlayNetworking.send(other);
            }
        } catch (RuntimeException e) {
            LOG.error("Не удалось отправить payload {} на сервер", payload.type().id(), e);
        }
    }
}

/*
 * Часть nfcompat. Отправка собственных пакетов игроку.
 *
 * NeoForge патчит `ServerGamePacketListenerImpl`, добавляя `send(CustomPacketPayload)`.
 * На Fabric это `ServerPlayNetworking.send(player, payload)` — тот же эффект через
 * реестр payload'ов.
 */
package nfcompat.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public final class Payloads {
    private Payloads() {
    }

    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }
}

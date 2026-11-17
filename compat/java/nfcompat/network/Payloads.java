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
        // У фиктивного игрока (геймтесты, FakePlayer) соединения нет — отправлять некому.
        // NeoForge в этом месте так же проверяет connection и молча выходит,
        // Fabric'овский ServerPlayNetworking падает с NPE.
        if (player.connection == null) {
            return;
        }
        ServerPlayNetworking.send(player, payload);
    }
}

/*
 * Часть nfcompat. NeoForge-овский PacketDistributor поверх Fabric networking.
 *
 * Каждый sendTo* собран из Fabric-овского ServerPlayNetworking.send плюс
 * ванильных выборок игроков (PlayerList / ChunkMap), то есть ведёт себя так же,
 * как NeoForge-овский оригинал, без Neo-овских bundle-пакетов.
 *
 * Отличия от NeoForge:
 *   - несколько payload'ов в одном вызове (varargs) уходят отдельными пакетами,
 *     а не ClientboundBundlePacket'ом. Смысл тот же (все доедут), атомарности
 *     "всё или ничего" у ней нет и в NeoForge;
 *   - sendToPlayersTrackingEntity* есть, но отправляет тем же способом, что и
 *     NeoForge (через ServerChunkCache), а не через Neo-овский
 *     ClientboundCustomPayloadPacket-кэш.
 */
package net.neoforged.neoforge.network;

import java.util.Objects;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PacketDistributor {
    private static final Logger LOG = LoggerFactory.getLogger("nfcompat/network");

    private PacketDistributor() {
    }

    /** Отправить payload(ы) конкретному игроку. */
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload,
            CustomPacketPayload... payloads) {
        send(player, payload, payloads);
    }

    /** Отправить payload(ы) всем игрокам измерения. */
    public static void sendToPlayersInDimension(ServerLevel level, CustomPacketPayload payload,
            CustomPacketPayload... payloads) {
        for (var player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level() == level) {
                send(player, payload, payloads);
            }
        }
    }

    /**
     * Отправить payload(ы) всем игрокам в радиусе вокруг точки, кроме
     * excluded (если задан).
     */
    public static void sendToPlayersNear(ServerLevel level, @Nullable ServerPlayer excluded, double x, double y, double z,
            double radius, CustomPacketPayload payload, CustomPacketPayload... payloads) {
        var players = level.getServer().getPlayerList().getPlayers();
        double radiusSqr = radius * radius;
        for (var player : players) {
            if (player == excluded || player.level() != level) {
                continue;
            }
            if (player.distanceToSqr(x, y, z) <= radiusSqr) {
                send(player, payload, payloads);
            }
        }
    }

    /** Отправить payload(ы) всем игрокам на сервере. */
    public static void sendToAllPlayers(CustomPacketPayload payload, CustomPacketPayload... payloads) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            LOG.error("sendToAllPlayers вызван без сервера (клиент?), payload {} потерян", payload.type().id());
            return;
        }
        for (var player : server.getPlayerList().getPlayers()) {
            send(player, payload, payloads);
        }
    }

    /** Отправить payload(ы) всем, кто видит сущность. */
    public static void sendToPlayersTrackingEntity(Entity entity, CustomPacketPayload payload,
            CustomPacketPayload... payloads) {
        if (entity.level().isClientSide()) {
            throw new IllegalStateException("Cannot send clientbound payloads on the client");
        }
        if (!(entity.level().getChunkSource() instanceof ServerChunkCache chunkCache)) {
            return;
        }
        // Ванильный ChunkMap умеет рассылать только Packet, поэтому идём по
        // игрокам чанка сущности и отсеиваем тех, кто её не трекает.
        var chunkPos = entity.chunkPosition();
        for (var player : chunkCache.chunkMap.getPlayers(chunkPos, false)) {
            send(player, payload, payloads);
        }
    }

    /** То же, что sendToPlayersTrackingEntity, но игроку-сущности тоже. */
    public static void sendToPlayersTrackingEntityAndSelf(Entity entity, CustomPacketPayload payload,
            CustomPacketPayload... payloads) {
        sendToPlayersTrackingEntity(entity, payload, payloads);
        if (entity instanceof ServerPlayer self) {
            send(self, payload, payloads);
        }
    }

    /** Отправить payload(ы) всем, кто видит чанк. */
    public static void sendToPlayersTrackingChunk(ServerLevel level, ChunkPos chunkPos, CustomPacketPayload payload,
            CustomPacketPayload... payloads) {
        for (var player : level.getChunkSource().chunkMap.getPlayers(chunkPos, false)) {
            send(player, payload, payloads);
        }
    }

    private static void send(ServerPlayer player, CustomPacketPayload payload, CustomPacketPayload... payloads) {
        Objects.requireNonNull(payload, "Cannot send null payload");
        try {
            ServerPlayNetworking.send(player, payload);
            for (var other : payloads) {
                Objects.requireNonNull(other, "Cannot send null payload");
                ServerPlayNetworking.send(player, other);
            }
        } catch (RuntimeException e) {
            // Самая частая причина: тип payload'а никто не зарегистрировал,
            // потому что RegisterPayloadHandlersEvent не был опубликован.
            LOG.error("Не удалось отправить payload {} игроку {}", payload.type().id(), player.getScoreboardName(), e);
        }
    }
}

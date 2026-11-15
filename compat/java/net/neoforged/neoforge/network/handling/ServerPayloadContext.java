/*
 * Часть nfcompat. Серверная реализация IPayloadContext.
 *
 * В NeoForge контекст строится вокруг ICommonPacketListener; здесь - вокруг
 * ServerPlayer и Fabric-овского PacketSender, которых достаточно, чтобы
 * сделать reply/disconnect/enqueueWork.
 */
package net.neoforged.neoforge.network.handling;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class ServerPayloadContext implements IPayloadContext {
    private final MinecraftServer server;
    private final ServerPlayer player;

    public ServerPayloadContext(ServerPlayer player) {
        this.server = player.level().getServer();
        this.player = player;
    }

    @Override
    public ServerPlayer player() {
        return player;
    }

    @Override
    public void reply(CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public void disconnect(Component reason) {
        player.connection.disconnect(reason);
    }

    @Override
    public CompletableFuture<Void> enqueueWork(Runnable task) {
        if (server.isSameThread()) {
            try {
                task.run();
                return CompletableFuture.completedFuture(null);
            } catch (Throwable t) {
                return CompletableFuture.failedFuture(t);
            }
        }
        return server.submit(task);
    }

    @Override
    public <T> CompletableFuture<T> enqueueWork(Supplier<T> task) {
        if (server.isSameThread()) {
            try {
                return CompletableFuture.completedFuture(task.get());
            } catch (Throwable t) {
                return CompletableFuture.failedFuture(t);
            }
        }
        return server.submit(task).exceptionally(t -> {
            throw new CompletionException(t);
        });
    }

    @Override
    public PacketFlow flow() {
        return PacketFlow.SERVERBOUND;
    }
}

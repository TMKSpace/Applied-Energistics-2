/*
 * Часть nfcompat. Клиентская реализация IPayloadContext.
 *
 * Лежит в client-пакете, чтобы main-код шима (PayloadRegistrar) не тянул
 * ClientPlayNetworking в постоянные ссылки: на выделенном сервере класс
 * просто не будет загружен.
 */
package net.neoforged.neoforge.client.network.handling;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ClientPayloadContext implements IPayloadContext {
    private final Minecraft minecraft;
    private final LocalPlayer player;

    public ClientPayloadContext(LocalPlayer player) {
        this.minecraft = Minecraft.getInstance();
        this.player = player;
    }

    @Override
    public LocalPlayer player() {
        return player;
    }

    @Override
    public void reply(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }

    @Override
    public void disconnect(Component reason) {
        var listener = minecraft.getConnection();
        if (listener != null) {
            listener.getConnection().disconnect(reason);
        }
    }

    @Override
    public CompletableFuture<Void> enqueueWork(Runnable task) {
        if (minecraft.isSameThread()) {
            try {
                task.run();
                return CompletableFuture.completedFuture(null);
            } catch (Throwable t) {
                return CompletableFuture.failedFuture(t);
            }
        }
        return minecraft.submit(task);
    }

    @Override
    public <T> CompletableFuture<T> enqueueWork(Supplier<T> task) {
        if (minecraft.isSameThread()) {
            try {
                return CompletableFuture.completedFuture(task.get());
            } catch (Throwable t) {
                return CompletableFuture.failedFuture(t);
            }
        }
        return minecraft.submit(task).exceptionally(t -> {
            throw new CompletionException(t);
        });
    }

    @Override
    public PacketFlow flow() {
        return PacketFlow.CLIENTBOUND;
    }
}

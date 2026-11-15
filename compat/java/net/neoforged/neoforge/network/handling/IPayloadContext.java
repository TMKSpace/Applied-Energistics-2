/*
 * Часть nfcompat. NeoForge-овский IPayloadContext.
 *
 * Урезано сознательно. В NeoForge интерфейс заметно шире, и часть методов
 * упирается в вещи, которых на Fabric нет в принципе:
 *
 *   listener()          - возвращает ICommonPacketListener: это NeoForge-овский
 *                         интерфейс, который MC-шные listener'ы реализуют через
 *                         патчи. На Fabric таких патчей нет, а подсунуть свой
 *                         listener нельзя (тип возврата - чужой класс, который
 *                         обязан быть родителем ServerGamePacketListenerImpl).
 *   handle(Packet)      - нужен NetworkRegistry с очередью пакетов.
 *   finishCurrentTask() - конфигурационная фаза + ConfigurationTask.
 *   channelHandlerContext() - требует Connection#channel() из NeoForge-патча.
 *
 * Оставлено ровно то, что реально зовёт AE2 (enqueueWork, player), плюс
 * reply/disconnect/flow - они честно реализуются через Fabric API.
 *
 * Только PLAY-фаза: конфигурационную фазу шим не поддерживает.
 */
package net.neoforged.neoforge.network.handling;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

public interface IPayloadContext {
    /**
     * Игрок, к которому относится payload: для serverbound - отправитель,
     * для clientbound - LocalPlayer.
     */
    Player player();

    /** Отправить payload обратно отправителю. */
    void reply(CustomPacketPayload payload);

    /** Отключить игрока с указанной причиной. */
    void disconnect(Component reason);

    /**
     * Выполнить задачу в главном потоке игры (серверном или клиентском,
     * смотря чей это контекст). Если мы уже в нём - выполняет сразу.
     */
    CompletableFuture<Void> enqueueWork(Runnable task);

    /** То же самое, но с результатом. */
    <T> CompletableFuture<T> enqueueWork(Supplier<T> task);

    /** Направление payload'а. */
    PacketFlow flow();
}

/*
 * Часть nfcompat. Фейковый игрок.
 *
 * В NeoForge у `ServerPlayer` есть заглушка «соединения», поэтому обычный код AE2 может
 * спокойно звать `player.connection.send(...)`, `openMenu(...)`, `sendSystemMessage(...)`:
 * лоадер просто выбрасывает всё это в пустоту. На Fabric `connection` = null, и первый же
 * такой вызов — NPE (именно так падал геймтест `ae2:memcard_pattern_provider`).
 *
 * Поэтому повторяем поведение NeoForge (LGPL-2.1, `neoforge.common.util.FakePlayer`):
 * `FakePlayerNetHandler` с пустым `Connection` и no-op `send/tick`, плюс no-op `openMenu`
 * и `sendSystemMessage`.
 */
package net.neoforged.neoforge.common.util;

import java.util.OptionalInt;

import com.mojang.authlib.GameProfile;

import io.netty.channel.ChannelFutureListener;


import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;

import org.jetbrains.annotations.Nullable;

public class FakePlayer extends ServerPlayer {
    public FakePlayer(ServerLevel level, GameProfile profile) {
        // ClientInformation обязателен: ServerPlayer#updateOptions в 26.3 дёргает
        // information.language(). NeoForge тоже передаёт createDefault(), а не null.
        // Раньше играло: clientInformation() дёргался только в списке игроков, но при
        // выдаче и обновлении списка -> NPE без него.
        super(level.getServer(), level, profile, ClientInformation.createDefault());

        // Как в NeoForge: у фейкплеера должно быть соединение, которое ничего не делает.
        // Без него любой `player.connection.send(...)` из обычного кода AE2 — NPE.
        this.connection = new FakePlayerNetHandler(level.getServer(), this);
    }

    /** Конкретный наследник: в 26.3 `Connection` нельзя инстанцировать напрямую. */
    private static final class DummyConnection extends net.minecraft.network.Connection {
        private DummyConnection() {
            super(PacketFlow.SERVERBOUND);
        }
    }

    /** Заглушка соединения: всё, что «отправляется», никуда не уходит. */
    private static final class FakePlayerNetHandler extends ServerGamePacketListenerImpl {
        /** Пустое соединение: никакого канала за ним нет, все send перекрыты ниже. */
        private static final net.minecraft.network.Connection DUMMY_CONNECTION = new DummyConnection();

        private FakePlayerNetHandler(MinecraftServer server, ServerPlayer player) {
            super(server, DUMMY_CONNECTION, player,
                    CommonListenerCookie.createInitial(player.getGameProfile(), false));
        }

        @Override
        public void tick() {
        }

        @Override
        public void send(Packet<?> packet) {
        }

        @Override
        public void send(Packet<?> packet, @Nullable ChannelFutureListener listener) {
        }
    }

    /** Единственное отличие от апстрима: фейкплеер не должен ни уходить в спектаторы, ни крафтить. */
    @Override
    public boolean isSpectator() {
        return false;
    }

    @Override
    public boolean isCreative() {
        return false;
    }

    /** NeoForge: фейкплееру меню не открываем (иначе сервер попытается синкнуть контейнер). */
    @Override
    public OptionalInt openMenu(@Nullable MenuProvider menu) {
        return OptionalInt.empty();
    }

    /** NeoForge: сообщения фейкплееру не шлём. */
    @Override
    public void sendSystemMessage(Component component, boolean actionBar) {
    }

    @Override
    public String toString() {
        return "FakePlayer[" + getUUID() + "]";
    }

    static {
        // Ошибка, чтобы не потерять тип при рефакторинге инициализации
        assert Player.class != null;
    }
}

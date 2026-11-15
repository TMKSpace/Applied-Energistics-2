/*
 * Часть nfcompat. Фейковый игрок.
 *
 * В NeoForge это ServerPlayer с пометкой «неродной», которого моды используют как
 * контекст действия (крафт, размещение, добыча). AE2 проверяет `instanceof FakePlayer`
 * и берёт его из Platform.getFakePlayer. Отдельного поведения классу не нужно —
 * важно, что это именно наш тип, а не любой ServerPlayer.
 */
package net.neoforged.neoforge.common.util;

import com.mojang.authlib.GameProfile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public class FakePlayer extends ServerPlayer {
    public FakePlayer(ServerLevel level, GameProfile profile) {
        super(level.getServer(), level, profile,
                level.getServer().getPlayerList().getPlayers().isEmpty()
                        ? null
                        : level.getServer().getPlayerList().getPlayers().get(0).clientInformation());
    }

    /** Ничего не отправляем на клиент: такого игрока там нет. */
    @Override
    public boolean isSpectator() {
        return false;
    }

    @Override
    public boolean isCreative() {
        return false;
    }

    @Override
    public String toString() {
        return "FakePlayer[" + getUUID() + "]";
    }

    static {
        // ссылка, чтобы не потерять связь типов при будущем рефакторинге
        assert Player.class != null;
    }
}

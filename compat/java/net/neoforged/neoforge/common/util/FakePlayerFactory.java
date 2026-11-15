/*
 * Часть nfcompat. Фабрика фейковых игроков.
 *
 * В NeoForge это обёртка над GameProfile + ServerLevel, которая держит одного
 * фейкового игрока на уровень. Ванильного аналога нет, поэтому создаём
 * настоящего ServerPlayer с заданным профилем и без сети.
 */
package net.neoforged.neoforge.common.util;

import com.mojang.authlib.GameProfile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public final class FakePlayerFactory {
    private FakePlayerFactory() {
    }

    @Nullable
    public static ServerPlayer get(ServerLevel level, GameProfile profile) {
        // TODO: кэшировать по уровню и профилю, как в NeoForge; сейчас создаём каждый раз.
        var server = level.getServer();
        if (server == null) {
            return null;
        }
        return new ServerPlayer(server, level, profile, server.getPlayerList().getPlayers().isEmpty()
                ? null
                : server.getPlayerList().getPlayers().get(0).clientInformation());
    }
}

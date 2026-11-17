/*
 * Часть nfcompat. Фабрика фейковых игроков.
 *
 * В NeoForge это обёртка над GameProfile + ServerLevel, которая держит фейковых
 * игроков на уровень. Ванильного аналога нет, поэтому создаём настоящего
 * ServerPlayer с заданным профилем и без сети.
 *
 * Кэш обязателен: AE2 зовёт `Platform.getFakePlayer` из разных мест (проверка
 * крафта, "кто это сделал" в событиях), и каждый вызов нового ServerPlayer —
 * это лишний объект в мире, тик и память. NeoForge тоже отдаёт одного и того же
 * игрока на пару уровень+профиль.
 */
package net.neoforged.neoforge.common.util;

import java.util.HashMap;
import java.util.Map;

import com.mojang.authlib.GameProfile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public final class FakePlayerFactory {
    /**
     * Уровень -> профиль -> игрок. Ключ уровня сравнивается по ссылке (ServerLevel
     * не переопределяет equals), поэтому записи старого мира сбрасываются явно:
     * см. {@link #invalidate(ServerLevel)} и {@link #clear()}.
     */
    private static final Map<ServerLevel, Map<GameProfile, ServerPlayer>> CACHE = new HashMap<>();

    private FakePlayerFactory() {
    }

    @Nullable
    public static synchronized ServerPlayer get(ServerLevel level, GameProfile profile) {
        var server = level.getServer();
        if (server == null) {
            return null;
        }
        // именно FakePlayer: AE2 проверяет instanceof этого класса
        return CACHE.computeIfAbsent(level, l -> new HashMap<>())
                .computeIfAbsent(profile, p -> new FakePlayer(level, p));
    }

    /**
     * Забывает игроков выгруженного измерения: держать ссылку на мёртвый ServerLevel
     * нельзя, иначе вместе с кэшем утекает весь мир.
     */
    public static synchronized void invalidate(ServerLevel level) {
        CACHE.remove(level);
    }

    /** Полный сброс: сервер остановлен, все уровни этого мира больше не существуют. */
    public static synchronized void clear() {
        CACHE.clear();
    }
}

/*
 * Часть nfcompat. Доступ к текущему серверу.
 *
 * В NeoForge здесь же живёт вся загрузка сервера (моды, датапаки, шина). Нам нужен
 * только текущий MinecraftServer — его выставляет наш общий обработчик событий
 * жизненного цикла. Пока значение просто хранится.
 */
package net.neoforged.neoforge.server;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.MinecraftServer;

public final class ServerLifecycleHooks {
    private static volatile MinecraftServer currentServer;

    private ServerLifecycleHooks() {
    }

    @Nullable
    public static MinecraftServer getCurrentServer() {
        return currentServer;
    }

    public static void setCurrentServer(@Nullable MinecraftServer server) {
        currentServer = server;
    }
}

/*
 * Часть nfcompat. Создание RegistryFriendlyByteBuf.
 *
 * NeoForge патчит конструктор: `RegistryFriendlyByteBuf(ByteBuf, RegistryAccess, ConnectionType)`.
 * Третий аргумент говорит, какие каналы знает собеседник — на Fabric такой модели нет,
 * поэтому аргумент принимается и игнорируется.
 */
package nfcompat.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;

public final class Buffers {
    private Buffers() {
    }

    public static RegistryFriendlyByteBuf create(ByteBuf buf, RegistryAccess registryAccess, ConnectionType type) {
        return new RegistryFriendlyByteBuf(buf, registryAccess);
    }
}

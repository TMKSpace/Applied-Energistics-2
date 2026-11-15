/*
 * Часть nfcompat. Регистрация клиентских расширений блоков и предметов.
 *
 * В NeoForge событие несёт реестр всех видов расширений (блок, предмет, жидкость, эффект).
 * AE2 подписывает только блоки, поэтому оставлен только `registerBlock`, а сам реестр
 * держится здесь: `ClientExtensionsManager` из NeoForge целиком не портирован —
 * он завязан на патчи ванильных классов.
 */
package net.neoforged.neoforge.client.extensions.common;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

public class RegisterClientExtensionsEvent extends Event implements IModBusEvent {
    private static final Map<Block, IClientBlockExtensions> BLOCK_EXTENSIONS = new IdentityHashMap<>();

    public RegisterClientExtensionsEvent() {
    }

    public void registerBlock(IClientBlockExtensions extensions, Block... blocks) {
        for (Block block : blocks) {
            BLOCK_EXTENSIONS.put(block, extensions);
        }
    }

    public static IClientBlockExtensions getBlockExtensions(Block block) {
        return BLOCK_EXTENSIONS.get(block);
    }
}

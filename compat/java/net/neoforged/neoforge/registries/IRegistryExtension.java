/*
 * Часть nfcompat. Расширение реестра.
 *
 * В NeoForge `Registry` патчится до `IRegistryExtension` и умеет `addCallback`.
 * В ванили такого нет, поэтому интерфейс объявлен только чтобы компилировался
 * подписной API коллбеков. Вызов `registry.addCallback(...)` из AE2 придётся
 * переписать — см. AE2-PATCHES.md.
 */
package net.neoforged.neoforge.registries;

import net.neoforged.neoforge.registries.callback.RegistryCallback;

public interface IRegistryExtension<T> {
    default void addCallback(RegistryCallback<T> callback) {
        throw new UnsupportedOperationException("nfcompat: коллбеки реестров не поддерживаются");
    }
}

/*
 * Часть nfcompat. Коллбеки реестров.
 *
 * В NeoForge `Registry` патчится до `IRegistryExtension` и умеет `addCallback`: коллбек
 * срабатывает, когда реестр «запекается» (все объекты известны). Добавить метод в ванильный
 * `Registry` нельзя, поэтому AE2 в FABRIC-ветке зовёт этот хелпер.
 *
 * Наши регистрации жадные, то есть на момент вызова объекты уже на месте — поэтому
 * коллбек честно выполняется сразу. Семантика «после запекания» приближена, но не совпадает:
 * если коллбек должен увидеть объекты, добавленные позже, он их не увидит.
 */
package nfcompat.registry;

import java.util.function.Consumer;

import net.minecraft.core.Registry;

public final class RegistryCallbacks {
    private RegistryCallbacks() {
    }

    public static <T> void onBake(Registry<T> registry, Consumer<Registry<T>> callback) {
        callback.accept(registry);
    }
}

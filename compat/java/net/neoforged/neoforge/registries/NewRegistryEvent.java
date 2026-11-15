/*
 * Часть nfcompat. Создание собственного реестра мода.
 *
 * В NeoForge реестр создаётся до заморозки `BuiltInRegistries` и вставляется туда, поэтому
 * его ключ потом работает при сериализации. На Fabric такой точки нет: `BuiltInRegistries`
 * уже заморожен. Возвращаем настоящий `MappedRegistry`, но живёт он только в руках AE2 —
 * для её собственного использования этого достаточно, для сериализации по ключу — нет.
 */
package net.neoforged.neoforge.registries;

import com.mojang.serialization.Lifecycle;

import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

public class NewRegistryEvent extends Event implements IModBusEvent {
    public NewRegistryEvent() {
    }

    public <T> Registry<T> create(RegistryBuilder<T> builder) {
        return new MappedRegistry<>(builder.key(), Lifecycle.stable());
    }
}

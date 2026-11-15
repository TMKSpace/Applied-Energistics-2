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
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

public class NewRegistryEvent extends Event implements IModBusEvent {
    /** Созданные модом реестры: нужны, чтобы `RegisterEvent` мог их найти. */
    private static final java.util.Map<ResourceKey<? extends Registry<?>>, Registry<?>> CREATED = new java.util.concurrent.ConcurrentHashMap<>();

    public NewRegistryEvent() {
    }

    public <T> Registry<T> create(RegistryBuilder<T> builder) {
        Registry<T> registry = new MappedRegistry<>(builder.key(), Lifecycle.stable());
        CREATED.put(builder.key(), registry);
        return registry;
    }

    /** {@return реестр, созданный модом, либо null} */
    @SuppressWarnings("unchecked")
    public static <T> Registry<T> getCreated(ResourceKey<? extends Registry<T>> key) {
        return (Registry<T>) CREATED.get(key);
    }
}

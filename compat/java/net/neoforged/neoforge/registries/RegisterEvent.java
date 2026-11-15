/*
 * Часть nfcompat. Событие «наполни этот ванильный реестр».
 *
 * В NeoForge оно приходит по одному на каждый реестр, до его заморозки, и моды в этот
 * момент вызывают `DeferredRegister#register`. У нас регистрация жадная (объект создаётся
 * сразу в `register(...)`), поэтому событие нужно только для кода AE2, который вешает
 * обработчик: он получит уже заполненные реестры.
 */
package net.neoforged.neoforge.registries;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

public class RegisterEvent extends Event implements IModBusEvent {
    private final ResourceKey<? extends Registry<?>> registryKey;

    public RegisterEvent(ResourceKey<? extends Registry<?>> registryKey) {
        this.registryKey = registryKey;
    }

    public ResourceKey<? extends Registry<?>> getRegistryKey() {
        return registryKey;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <T> Registry<T> getRegistry(ResourceKey<? extends Registry<T>> key) {
        // BuiltInRegistries.REGISTRY — Registry<? extends Registry<?>>, поэтому без сырого каста
        // не сходится; ключ и значение согласованы на стороне вызывающего.
        return (Registry<T>) ((Registry) BuiltInRegistries.REGISTRY).getValue((ResourceKey) key);
    }
}

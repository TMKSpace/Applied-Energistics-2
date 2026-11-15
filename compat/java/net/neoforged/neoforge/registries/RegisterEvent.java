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

    /**
     * NeoForge'вская форма регистрации элемента датапак-реестра вместе с его кодеком.
     * У нас датапак-реестры не наполняются этим путём, поэтому вызов принимается
     * и ничего не делает — соответствующие записи должны приходить из данных.
     */
    public void register(ResourceKey<? extends Registry<?>> registryKey, net.minecraft.resources.Identifier id,
            java.util.function.Supplier<?> codec) {
        // no-op
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <T> Registry<T> getRegistry(ResourceKey<? extends Registry<T>> key) {
        // Сначала собственные реестры мода: они не попадают в BuiltInRegistries,
        // потому что на Fabric тот уже заморожен.
        Registry<T> custom = NewRegistryEvent.getCreated(key);
        if (custom != null) {
            return custom;
        }
        // BuiltInRegistries.REGISTRY — Registry<? extends Registry<?>>, поэтому без сырого каста
        // не сходится; ключ и значение согласованы на стороне вызывающего.
        return (Registry<T>) ((Registry) BuiltInRegistries.REGISTRY).getValue((ResourceKey) key);
    }
}

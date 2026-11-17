/*
 * Часть nfcompat. Параметры создания нового реестра.
 *
 * В NeoForge настоящий билдер с десятком настроек (sync, maxId, intrusiveHolders,
 * default-ключи и т.д.). Здесь только то, что реально передаёт AE2, плюс геттеры:
 * значения не теряются, но часть из них (sync, maxId) на Fabric ни на что не влияет.
 */
package net.neoforged.neoforge.registries;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

public final class RegistryBuilder<T> {
    private final ResourceKey<? extends Registry<T>> key;
    private boolean sync;
    private int maxId = Integer.MAX_VALUE;

    public RegistryBuilder(ResourceKey<? extends Registry<T>> key) {
        this.key = key;
    }

    public RegistryBuilder<T> sync(boolean sync) {
        this.sync = sync;
        return this;
    }

    public RegistryBuilder<T> maxId(int maxId) {
        this.maxId = maxId;
        return this;
    }

    public ResourceKey<? extends Registry<T>> key() {
        return key;
    }
}

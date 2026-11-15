/*
 * Часть nfcompat. Контейнер мода.
 *
 * В NeoForge сюда стекается всё, что относится к моду: метаданные, шина, конфиги, ресурсы.
 * Нам нужны метаданные, шина и регистрация конфигов — последняя уходит в
 * Forge Config API Port (net.neoforged.fml.config/ModConfigSpec он предоставляет сам).
 */
package net.neoforged.fml;

import java.util.ArrayList;
import java.util.List;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.moddiscovery.ModInfo;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.Nullable;

public class ModContainer {
    private final ModInfo modInfo;
    private final IEventBus eventBus;
    private final List<ModConfig> configs = new ArrayList<>();
    private final java.util.Map<Class<?>, net.neoforged.fml.IExtensionPoint> extensions = new java.util.HashMap<>();

    public ModContainer(ModInfo modInfo, IEventBus eventBus) {
        this.modInfo = modInfo;
        this.eventBus = eventBus;
    }

    public String getModId() {
        return modInfo.getModId();
    }

    public ModInfo getModInfo() {
        return modInfo;
    }

    public IEventBus getEventBus() {
        return eventBus;
    }

    public void registerConfig(ModConfig.Type type, ModConfigSpec spec) {
        registerConfig(type, spec, null);
    }

    public void registerConfig(ModConfig.Type type, ModConfigSpec spec, @Nullable String fileName) {
        String name = fileName != null ? fileName : getModId() + "-" + type.getSerializedName() + ".toml";
        var config = new ModConfig(type, spec, getModId(), name, null);
        // Проверка самой спецификации: ловит значения, объявленные с restart-типом,
        // недопустимым для этого вида конфига, и валидаторы, отвергающие свой дефолт.
        spec.validateSpec(config);
        this.configs.add(config);
        // TODO: прочитать <name> с диска через nightconfig TOML, вызвать
        // spec.acceptConfig(loadedConfig) и запостить ModConfigEvent.Loading.
        // Пока значения конфигов остаются дефолтными.
    }

    public List<ModConfig> getConfigs() {
        return configs;
    }

    /** Регистрация точки расширения мода (в NeoForge — экран конфигов и т.п.). */
    public <T extends net.neoforged.fml.IExtensionPoint> T registerExtensionPoint(Class<? extends T> point) {
        return registerExtensionPoint(point, null);
    }

    public <T extends net.neoforged.fml.IExtensionPoint> T registerExtensionPoint(Class<? extends T> point, T impl) {
        if (impl != null) {
            extensions.put(point, impl);
        }
        return impl;
    }

    public <T extends net.neoforged.fml.IExtensionPoint> java.util.Optional<T> getCustomExtension(Class<T> point) {
        return java.util.Optional.ofNullable(point.cast(extensions.get(point)));
    }
}

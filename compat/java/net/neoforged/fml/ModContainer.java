/*
 * Часть nfcompat. Контейнер мода.
 *
 * В NeoForge сюда стекается всё, что относится к моду: метаданные, шина, конфиги.
 * Нам нужны метаданные, шина и регистрация конфигов.
 *
 * Конфиги сделаны как в NeoForge: TOML-файл на диске через night-config,
 * спецификация (`ModConfigSpec`) приводится к файлу через `correct`, а затем
 * привязывается через `acceptConfig`. Значения читаются из файла, а `save()`
 * (который зовёт, например, `ConfigValue.set`) пишет файл обратно.
 */
package net.neoforged.fml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;

import net.fabricmc.loader.api.FabricLoader;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.moddiscovery.ModInfo;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.Nullable;

public class ModContainer {
    private final ModInfo modInfo;
    private final IEventBus eventBus;
    private final List<ModConfig> configs = new ArrayList<>();
    private final java.util.Map<Class<?>, IExtensionPoint> extensions = new java.util.HashMap<>();

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
        registerConfig(type, (IConfigSpec) spec, null);
    }

    public void registerConfig(ModConfig.Type type, ModConfigSpec spec, @Nullable String fileName) {
        registerConfig(type, (IConfigSpec) spec, fileName);
    }

    /**
     * Вариант для чужих библиотек (GuideME передаёт {@code IConfigSpec}, а не конкретный
     * {@code ModConfigSpec}) — в NeoForge это одна и та же перегрузка, у нас нужен мост.
     */
    public void registerConfig(ModConfig.Type type, IConfigSpec spec) {
        registerConfig(type, spec, null);
    }

    public void registerConfig(ModConfig.Type type, IConfigSpec spec, @Nullable String fileName) {
        String name = fileName != null ? fileName : getModId() + "-" + type.getSerializedName() + ".toml";
        Path path = configDir().resolve(name);

        var config = new ModConfig(type, spec, getModId(), name, path);

        // Проверка самой спецификации: ловит restart-типы, недопустимые для этого вида
        // конфига, и валидаторы, отвергающие свой же дефолт.
        spec.validateSpec(config);

        try {
            Files.createDirectories(path.getParent());
        } catch (Exception e) {
            throw new RuntimeException("nfcompat: не удалось создать каталог конфигов " + path.getParent(), e);
        }

        CommentedFileConfig fileConfig = CommentedFileConfig.of(path, TomlFormat.instance());
        // Файл может отсутствовать — correct() допишет дефолты по спецификации.
        if (Files.exists(path)) {
            fileConfig.load();
        }
        spec.correct(fileConfig);
        config.setConfig(new FileLoadedConfig(fileConfig));
        spec.acceptConfig(config.getLoadedConfig());

        this.configs.add(config);

        // AE2 подписана на Loading и в этот момент синхронизирует свои кэши.
        eventBus.post(new ModConfigEvent.Loading(config));
    }

    public List<ModConfig> getConfigs() {
        return configs;
    }

    /**
     * Каталог конфигов мода. В запущенной игре - {@code FabricLoader#getConfigDir()}.
     * В обычной JVM (юнит-тесты) FabricLoader не инициализирован и его configDir == null,
     * поэтому подставляем каталог во временной папке: конфиг всё равно пишется/читается,
     * но ничего не засоряет.
     */
    private static Path configDir() {
        try {
            var dir = FabricLoader.getInstance().getConfigDir();
            if (dir != null) {
                return dir;
            }
        } catch (Throwable ignored) {
            // нет лоадера - уходим в fallback ниже
        }
        return java.nio.file.Paths.get(System.getProperty("java.io.tmpdir"), "nfcompat-test-config");
    }

    /** Регистрация точки расширения мода (в NeoForge — экран конфигов и т.п.). */
    public <T extends IExtensionPoint> T registerExtensionPoint(Class<? extends T> point) {
        return registerExtensionPoint(point, null);
    }

    public <T extends IExtensionPoint> T registerExtensionPoint(Class<? extends T> point, T impl) {
        if (impl != null) {
            extensions.put(point, impl);
        }
        return impl;
    }

    public <T extends IExtensionPoint> Optional<T> getCustomExtension(Class<T> point) {
        return Optional.ofNullable(point.cast(extensions.get(point)));
    }

    /** Привязанный конфиг, который умеет сохранить себя на диск. */
    private record FileLoadedConfig(CommentedFileConfig config) implements IConfigSpec.ILoadedConfig {
        @Override
        public void save() {
            config.save();
        }
    }
}

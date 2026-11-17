/*
 * Часть nfcompat. Один конфиг мода.
 *
 * В NeoForge этот класс живёт в загрузчике и умеет сам читать/писать TOML через
 * nightconfig: держит спецификацию и привязанный файл, правит файл по спеке,
 * пишет его обратно и перезагружает. Здесь то же самое, но без файлового вотчера —
 * перечитывание явное (`load()`), его дёргает `ModContainer` (и команда
 * `/ae2config reload`, см. `nfcompat.config.ConfigReloadCommand`).
 *
 * HARD: `ConfigValue#get()` читает значение именно из `getLoadedConfig().config()`,
 * поэтому привязка (acceptConfig) должна идти ПОСЛЕ correct() — иначе значение из
 * файла не подхватится.
 */
package net.neoforged.fml.config;

import java.nio.file.Files;
import java.nio.file.Path;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import net.minecraft.util.StringRepresentable;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class ModConfig {
    private static final Logger LOGGER = LogManager.getLogger("nfcompat/config");

    /**
     * Тип конфига.
     *
     * nfcompat: в NeoForge 26.3 набор типов отличается от того, что отдаёт
     * Forge Config API Port (у порта нет LOCAL и SYNCED), поэтому enum свой.
     * LOCAL — конфиг, свой у каждого клиента; SYNCED — синхронизируемый с сервером.
     */
    public enum Type implements StringRepresentable {
        STARTUP("startup"),
        COMMON("common"),
        CLIENT("client"),
        SERVER("server"),
        SYNCED("synced"),
        LOCAL("local");

        private final String serializedName;

        Type(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }

    private final Type type;
    private final IConfigSpec spec;
    private final String modId;
    private final String fileName;
    @Nullable
    private final Path path;
    @Nullable
    private volatile IConfigSpec.ILoadedConfig loadedConfig;

    public ModConfig(Type type, IConfigSpec spec, String modId, String fileName, @Nullable Path path) {
        this.type = type;
        this.spec = spec;
        this.modId = modId;
        this.fileName = fileName;
        this.path = path;
    }

    public Type getType() {
        return type;
    }

    public IConfigSpec getSpec() {
        return spec;
    }

    public String getModId() {
        return modId;
    }

    public String getFileName() {
        return fileName;
    }

    /** {@return путь к TOML на диске, или null, если конфиг живёт только в памяти} */
    @Nullable
    public Path getPath() {
        return path;
    }

    @Nullable
    public IConfigSpec.ILoadedConfig getLoadedConfig() {
        return loadedConfig;
    }

    public void setConfig(@Nullable IConfigSpec.ILoadedConfig loadedConfig) {
        this.loadedConfig = loadedConfig;
    }

    /**
     * Читает TOML с диска и привязывает значения к спецификации. Если файла нет —
     * создаёт его с дефолтами по спеке (`correct()` дописывает недостающие ключи).
     * Повторный вызов перечитывает файл заново: это и есть перезагрузка конфига.
     *
     * <p>Падение разбора TOML не глотаем: битый файл иначе тихо превратился бы
     * в дефолты, и игрок бы гадал, почему правки не работают.
     */
    public void load() {
        Path file = this.path;
        if (file == null) {
            // Конфиг без файла (например, NeoForgeClientConfig): читать нечего.
            return;
        }

        var fileConfig = open(file);
        // correct() дописывает недостающие ключи дефолтами, чинит вышедшие за range
        // значения и выкидывает ключи, которых в спеке нет. Слушатель правок есть только
        // у вендоренного ModConfigSpec (в IConfigSpec его нет) — логируем, если можем.
        if (spec instanceof ModConfigSpec modConfigSpec) {
            modConfigSpec.correct(fileConfig, (action, keyPath, incorrectValue, correctedValue) -> LOGGER.warn(
                    "конфиг {}: ключ {} исправлен по спецификации ({} -> {})",
                    fileName, String.join(".", keyPath), incorrectValue, correctedValue));
        } else {
            spec.correct(fileConfig);
        }

        setConfig(new FileLoadedConfig(fileConfig));
        // acceptConfig сам зовёт correct(), если конфиг вдруг не сошёлся, и сбрасывает
        // кэши ConfigValue (afterReload -> resetCaches(NONE)), иначе get() отдал бы
        // значение, прочитанное до перезагрузки.
        spec.acceptConfig(getLoadedConfig());

        // NeoForge тоже пишет файл сразу после загрузки: на диске появляются дефолты
        // (и комментарии), которые игрок может править.
        save();
    }

    /** Пишет текущие значения на диск. Сюда приходят `ConfigValue#save()` и `ModConfigSpec#save()`. */
    public void save() {
        var loaded = this.loadedConfig;
        if (loaded != null) {
            loaded.save();
        }
    }

    private CommentedFileConfig open(Path path) {
        var fileConfig = CommentedFileConfig.of(path, TomlFormat.instance());
        if (!Files.exists(path)) {
            // Пустой конфиг в памяти: correct() наполнит его дефолтами.
            return fileConfig;
        }
        try {
            fileConfig.load();
        } catch (RuntimeException e) {
            throw new RuntimeException("nfcompat: не удалось прочитать конфиг " + path, e);
        }
        return fileConfig;
    }

    /** Загруженный TOML-файл; `save()` пишет его на диск. */
    private record FileLoadedConfig(CommentedFileConfig file) implements IConfigSpec.ILoadedConfig {
        @Override
        public CommentedConfig config() {
            return file;
        }

        @Override
        public void save() {
            file.save();
        }
    }
}

/*
 * Файл nfcompat. Шим FML config.
 *
 * ВАЖНО: этот класс существует не только для AE2. Пакет `net.neoforged.fml.config`
 * в порте занимает ещё и ForgeConfigAPIPort (он приносит настоящую реализацию
 * NeoForge-конфигов: ConfigTracker, LoadedConfig, ModConfig, ModConfigSpec).
 * Классы с одинаковыми именами, и Fabric берёт их из первого мода на classpath:
 * `ae2` сортируется раньше `forgeconfigapiport`, поэтому побеждает НАШ класс,
 * а их ConfigTracker начинает работать поверх него.
 *
 * Отсюда правило: наша версия обязана быть НАДМНОЖЕСТВОМ настоящего API.
 * Ниже подпись и поведение сверены с байткодом ForgeConfigAPIPort 26.3.1
 * (он портирует NeoForge один-в-один):
 *
 *  - `Type.extension()`;
 *  - package-private поле `LoadedConfig loadedConfig` (их ConfigTracker читает
 *    и пишет его напрямую, по имени);
 *  - конструктор `(Type, IConfigSpec, String modId, String fileName, ReentrantLock)`;
 *  - `getFullPath()` и `setConfig(LoadedConfig, Consumer<ModConfig>)`.
 *
 * Если набор разойдётся — падает с NoSuchMethodError/NoSuchFieldError при
 * инициализации любой мод, использующий ForgeConfigAPIPort (Mutant Monsters,
 * Stoneworks, Storage Drawers, Traveler's Backpack, TreeChop и т.д.).
 *
 * Своя часть (nfcompat): путь задаётся сразу (`Path`), `load()`/`save()` читают
 * и пишут TOML для конфигов AE2, `validateSpec` — проверка спеки при регистрации.
 *
 * HARD: `ConfigValue#get()` станет доступен только после `getLoadedConfig().config()`,
 * потому что приватный (acceptConfig) вызван в `setConfig` - иначе значение из
 * файла не подхватится.
 */
package net.neoforged.fml.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.io.ParsingMode;
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
     * nfcompat: у NeoForge 26.3 список шире, чем у ForgeConfigAPIPort (у порта нет
     * LOCAL и SYNCED), поэтому enum свой. LOCAL — конфиг, свой у каждого клиента;
     * SYNCED — синхронизируемый с сервером. Их ConfigTracker работает с нашими
     * константами по именам, так что лишние значения ему не мешают.
     */
    public enum Type implements StringRepresentable {
        STARTUP("startup"),
        COMMON("common"),
        CLIENT("client"),
        SERVER("server"),
        SYNCED("synced"),
        LOCAL("local");

        /** Ключ в имени файла: common, client, server, startup... */
        private final String serializedName;

        Type(String serializedName) {
            this.serializedName = serializedName;
        }

        /** Как в NeoForge: «COMMON», «CLIENT», ... (имя константы, не lowercase). */
        @Override
        public String getSerializedName() {
            return name();
        }

        /**
         * {@return имя типа в нижнем регистре} - в NeoForge это `name().toLowerCase(Locale.ROOT)`,
         * именно оно идёт в имя файла (`<modid>-<extension>.toml` в ConfigTracker
         * у ForgeConfigAPIPort). Не путать с расширением файла: оно всегда toml.
         */
        public String extension() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private final Type type;
    private final IConfigSpec spec;
    private final String fileName;
    private final String modId;

    /**
     * nfcompat: готовый путь к файлу. У конфигов, зарегистрированных через
     * ForgeConfigAPIPort, его нет - путь приходит вместе с `LoadedConfig`.
     */
    @Nullable
    private final Path explicitPath;

    /**
     * Загруженный конфиг. Поле с таким именем и типом читает и пишет
     * ForgeConfigAPIPort (ConfigTracker), поэтому оно пакетное и не final.
     */
    LoadedConfig loadedConfig;

    /** Лок, под которым их конфиг-механизм переключает loadedConfig. */
    final Lock lock;

    /**
     * Конструктор NeoForge / ForgeConfigAPIPort: путь появится позже, вместе
     * с первым `setConfig`. Его вызывает ConfigTracker, а не мы.
     */
    ModConfig(Type type, IConfigSpec spec, String modId, String fileName, ReentrantLock lock) {
        this(type, spec, modId, fileName, lock, null);
    }

    /** nfcompat: регистрация своего конфига с уже известным путём (ModContainer). */
    public ModConfig(Type type, IConfigSpec spec, String modId, String fileName, Path path) {
        this(type, spec, modId, fileName, new ReentrantLock(), path);
    }

    private ModConfig(Type type, IConfigSpec spec, String modId, String fileName, ReentrantLock lock,
            @Nullable Path explicitPath) {
        this.type = type;
        this.spec = spec;
        this.modId = modId;
        this.fileName = fileName;
        this.lock = lock;
        this.explicitPath = explicitPath;
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

    @Nullable
    public IConfigSpec.ILoadedConfig getLoadedConfig() {
        return loadedConfig;
    }

    /** {@return полный путь к файлу конфига, или null, если он ещё не известен} */
    @Nullable
    public Path getFullPath() {
        var loaded = this.loadedConfig;
        return loaded != null ? loaded.path() : explicitPath;
    }

    /** nfcompat: то же, что {@link #getFullPath()} (историческое имя). */
    @Nullable
    public Path getPath() {
        return getFullPath();
    }

    /**
     * Принять загруженный конфиг: запомнить, отдать значения в спеку и
     * уведомить слушателя. Вызывается как нами (`load()`), так и
     * ForgeConfigAPIPort (ConfigTracker, ConfigWatcher).
     */
    void setConfig(LoadedConfig loadedConfig, Consumer<ModConfig> listener) {
        lock.lock();
        try {
            this.loadedConfig = loadedConfig;
            spec.acceptConfig(loadedConfig);
            listener.accept(this);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Читает TOML с диска и приводит значения к спецификации. Если файла нет -
     * создаёт его и заполняет дефолтами (`correct()` выставляет дефолтные
     * значения и ругается на исправленные).
     * <p>
     * Порядок важен: сперва читаем файл целиком, потом правим и сохраняем.
     */
    public void load() {
        Path file = getFullPath();
        if (file == null) {
            // Конфиг без файла (например, NeoForgeClientConfig): читать нечего.
            return;
        }

        var fileConfig = open(file);
        // correct() выставляет дефолтные значения, чинит вышедшие из диапазона
        // в допустимые и сообщает об этом. Слушатель нужен для лога.
        if (spec instanceof ModConfigSpec modConfigSpec) {
            modConfigSpec.correct(fileConfig, (action, keyPath, incorrectValue, correctedValue) -> LOGGER.warn(
                    "Конфиг {}: значение {} исправлено по спецификации ({} -> {})",
                    fileName, String.join(".", keyPath), incorrectValue, correctedValue));
        } else {
            spec.correct(fileConfig);
        }

        setConfig(new LoadedConfig(fileConfig, file, this), cfg -> {
        });

        // NeoForge тоже пишет файл сразу после загрузки: он может появиться неполным
        // (в дефолтах), чтобы короткий файл больше не правился.
        save();
    }

    /** Пишет текущие значения на диск. Делает `ConfigValue#save()` и `ModConfigSpec#save()`. */
    public void save() {
        var loaded = this.loadedConfig;
        if (loaded != null) {
            loaded.save();
        }
    }

    /**
     * Читает TOML в обычный in-memory конфиг.
     *
     * nfcompat: сознательно НЕ `CommentedFileConfig.of(...)` - в nightconfig 3.8 он
     * отдаёт AsyncFileConfig, у которого и чтение, и запись идут через очередь
     * (файл может появиться позже вызова save()). Нам нужен предсказуемый
     * синхронный цикл: прочитали -> поправили -> записали.
     */
    private CommentedConfig open(Path path) {
        CommentedConfig config = TomlFormat.instance().createConfig();
        if (!Files.exists(path)) {
            // Пустой конфиг в начале: correct() заполнит его дефолтами.
            return config;
        }
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            TomlFormat.instance().createParser().parse(reader, config, ParsingMode.MERGE);
        } catch (IOException | RuntimeException e) {
            throw new RuntimeException("nfcompat: не удалось прочитать конфиг " + path, e);
        }
        return config;
    }
}

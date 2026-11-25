/*
 * Часть nfcompat. Контейнер мода.
 *
 * В NeoForge сюда стекается всё, что относится к моду: метаданные, шина, конфиги.
 * Нам нужны метаданные, шина и регистрация конфигов.
 *
 * Конфиги целиком отданы ForgeConfigAPIPort (FCP) — он теперь жёсткая зависимость.
 * `registerConfig` только перекладывает спеку в их `ConfigTracker`: файл TOML,
 * чтение, запись, правка по спецификации и вотчер живут у них (своих классов
 * ModConfig/IConfigSpec/ModConfigSpec в шиме больше нет). Для всех типов, кроме
 * SERVER, FCP читает файл сразу внутри `registerConfig`, поэтому `ConfigValue#get()`
 * в конструкторе мода уже работает.
 *
 * События `ModConfigEvent` (`Loading`/`Reloading`) FCP не шипит и не постит — это
 * наши шим-классы (у него свои `fuzs...ModConfigEvents`). Поэтому:
 *  - `Loading` постим сами, но не в момент `registerConfig` (иначе AE2, которая
 *    вешает слушателя ниже в конструкторе, его пропустит), а после сборки модов —
 *    `postPendingConfigLoads()` из `NfCompat.runRegistrationPhases`;
 *  - `Reloading` — в `reloadConfigs()` уже после того, как FCP перечитал файлы.
 * AE2 подписана на оба и в этот момент пересобирает кэши (`AEConfig.CommonConfig#sync`).
 */
package net.neoforged.fml;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import net.fabricmc.loader.api.FabricLoader;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.config.ConfigTracker;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.moddiscovery.ModInfo;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.Nullable;

public class ModContainer {
    /** Все контейнеры этой JVM: на NeoForge такой список — `ModList`. */
    private static final List<ModContainer> CONTAINERS = new CopyOnWriteArrayList<>();

    /** Переопределение каталога конфигов при перечитывании: null — обычное поведение. */
    @Nullable
    private static volatile Path configDirOverride;

    private final ModInfo modInfo;
    private final IEventBus eventBus;
    private final List<ModConfig> configs = new ArrayList<>();
    /** Конфиги, по которым `Loading` ещё не отправлен (см. `postPendingConfigLoads`). */
    private final List<ModConfig> pendingLoads = new ArrayList<>();
    private final java.util.Map<Class<?>, IExtensionPoint> extensions = new java.util.HashMap<>();

    public ModContainer(ModInfo modInfo, IEventBus eventBus) {
        this.modInfo = modInfo;
        this.eventBus = eventBus;
        CONTAINERS.add(this);
    }

    public String getModId() {
        return modInfo.getModId();
    }

    /** Контейнер мода по id, если он уже построен (аналог `ModList.getModContainerById`). */
    public static Optional<ModContainer> find(String modId) {
        for (var container : CONTAINERS) {
            if (container.getModId().equals(modId)) {
                return Optional.of(container);
            }
        }
        return Optional.empty();
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

    /**
     * Регистрирует конфиг в ForgeConfigAPIPort. Имя файла — {@code fileName} или
     * дефолтное {@code <modId>-<type.extension()>.toml}.
     */
    public void registerConfig(ModConfig.Type type, IConfigSpec spec, @Nullable String fileName) {
        String name = fileName != null ? fileName : getModId() + "-" + type.extension() + ".toml";

        // FCP сам создаёт каталог и файл, читает TOML, правит его по спецификации и
        // ставит вотчер. Для всего, кроме SERVER, это происходит прямо здесь.
        var config = ConfigTracker.INSTANCE.registerConfig(type, spec, getModId(), name);

        this.configs.add(config);

        // Событие откладываем до конца сборки мода (postPendingConfigLoads):
        // AE2 зовёт registerConfig в первой строке своего конструктора, а слушателя
        // Loading вешает ниже. На NeoForge загрузчик грузит конфиги после всех
        // конструкторов, поэтому слушатель в конструкторе событие успевает поймать.
        this.pendingLoads.add(config);
    }

    /**
     * Рассылает {@link ModConfigEvent.Loading} по конфигам, зарегистрированным после
     * последнего вызова. Вызывается один раз, когда моды уже созданы
     * (`NfCompat.runRegistrationPhases`), — ровно там, где это делает NeoForge.
     *
     * @return сколько событий отправлено
     */
    public int postPendingConfigLoads() {
        if (pendingLoads.isEmpty()) {
            return 0;
        }
        var pending = new ArrayList<>(pendingLoads);
        pendingLoads.clear();
        for (var config : pending) {
            eventBus.post(new ModConfigEvent.Loading(config));
        }
        return pending.size();
    }

    /** То же для всех контейнеров: AE2 и поднятых библиотек (GuideME). */
    public static int postAllPendingConfigLoads() {
        int posted = 0;
        for (var container : CONTAINERS) {
            posted += container.postPendingConfigLoads();
        }
        return posted;
    }

    public List<ModConfig> getConfigs() {
        return configs;
    }

    /**
     * Перечитывает конфиги контейнера с диска (это делает FCP) и постит
     * {@link ModConfigEvent.Reloading} на нашу шину. NeoForge делает это файловым
     * вотчером; у нас перечитывание ещё и явное — команда {@code /ae2config reload}
     * (`nfcompat.config.ConfigReloadCommand`).
     *
     * @return сколько НАШИХ конфигов перечитано
     */
    public int reloadConfigs() {
        // Если по конфигу ещё не было Loading (перезагрузка пришла раньше конца сборки
        // мода), сначала шлём его — порядок событий должен остаться Loading -> Reloading.
        postPendingConfigLoads();

        // Только те типы, что у нас реально зарегистрированы; STARTUP не трогаем:
        // такой конфиг NeoForge читает один раз при запуске игры.
        var types = new LinkedHashSet<ModConfig.Type>();
        for (var config : configs) {
            if (config.getType() != ModConfig.Type.STARTUP) {
                types.add(config.getType());
            }
        }

        Path configDir = configDir();
        int reloaded = 0;
        for (var type : types) {
            // ВАЖНО: FCP перечитывает ВСЕ конфиги этого типа, включая конфиги чужих
            // модов, а не только наши (у него нет API «перечитай один конфиг»).
            // Это допустимо: файлы у каждого свои, операция идемпотентна.
            ConfigTracker.INSTANCE.loadConfigs(type, configDir);

            // FCP постит свои Fabric-события, наших ModConfigEvent он не знает.
            for (var config : configs) {
                if (config.getType() == type) {
                    eventBus.post(new ModConfigEvent.Reloading(config));
                    reloaded++;
                }
            }
        }
        return reloaded;
    }

    /** Перечитывает конфиги всех контейнеров (AE2 и поднятых библиотек). */
    public static int reloadAllConfigs() {
        int reloaded = 0;
        for (var container : CONTAINERS) {
            reloaded += container.reloadConfigs();
        }
        return reloaded;
    }

    /** Каталог конфигов мода (см. `configDir()`): подмена нужна только тестам. */
    public static void setConfigDirOverride(@Nullable Path dir) {
        configDirOverride = dir;
    }

    /**
     * Каталог, из которого перечитываются конфиги. В запущенной игре это
     * {@code FabricLoader#getConfigDir()} — тот же, куда их пишет FCP. В обычной JVM
     * (юнит-тесты) FabricLoader не инициализирован и его configDir == null, поэтому
     * подставляем каталог во временной папке, чтобы перечитывание не падало.
     */
    private static Path configDir() {
        var override = configDirOverride;
        if (override != null) {
            return override;
        }
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
    public <T extends IExtensionPoint> void registerExtensionPoint(Class<? extends T> point) {
        registerExtensionPoint(point, null);
    }

    public <T extends IExtensionPoint> void registerExtensionPoint(Class<? extends T> point, T impl) {
        if (impl != null) {
            extensions.put(point, impl);
        }
    }

    public <T extends IExtensionPoint> Optional<T> getCustomExtension(Class<T> point) {
        return Optional.ofNullable(point.cast(extensions.get(point)));
    }
}

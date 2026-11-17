/*
 * Порт из NeoForge (LGPL-2.1-only) для шима nfcompat.
 * Upstream: NeoForge, testframework/src/main/java/net/neoforged/testframework/junit/EphemeralTestServerProvider.java
 * Copyright (c) NeoForged and contributors
 *
 * ЧТО ЭТО. Единственный класс NeoForge test framework, который нужен юнит-тестам AE2
 * (`src/test/java`): JUnit 5 расширение, поднимающее одноразовый (ephemeral) MinecraftServer
 * на всю JUnit-сессию. Апстримный `@BootstrapMinecraft` подписан на него через @ExtendWith,
 * а `BootstrapMinecraftExtension` дергает `grabServer()`. Тесты берут `MinecraftServer`
 * параметром конструктора/метода - его резолвит этот ParameterResolver.
 *
 * ЧТО ИЗМЕНЕНО ОТНОСИТЕЛЬНО АПСТРИМА (адаптация под Fabric + MC 26.3):
 *  - `net.neoforged.fml.loading.FMLLoader` (close callback) убран, вместо него поток сервера
 *    делается daemon-потоком: JUnit-воркер не должен висеть из-за незакрытого сервера.
 *  - `ServerLifecycleHooks.handleServerAboutToStart/handleServerStarting` у нас в шиме нет,
 *    вместо них - `setCurrentServer` + постинг NeoForge-событий на шинный EVENT_BUS.
 *  - Bootstrap Minecraft: в NeoForge моды уже инициализированы до тестов. У нас нет
 *    Fabric-запуска, поэтому `bootstrapMinecraft()` делает это руками и в правильном порядке:
 *    сначала init мода (реестры ещё разморожены, как в Fabric-dev), потом `Bootstrap.bootStrap()`
 *    (он замораживает BuiltInRegistries), и только потом датапак/сервер.
 *  - Сигнатуры `MinecraftServer`/`LevelSettings`/`PrimaryLevelData`/`WorldLoader` приведены
 *    к 26.3 (LevelSettings стал record'ом с DifficultySettings, WorldStem носит
 *    LevelDataAndDimensions.WorldDataAndGenSettings, у сервера появились новые абстрактные
 *    методы про spam-пороги и права).
 *
 * ВАЖНО: класс test-only. Он лежит в compat/java (общий main-сорсет) только ради того, чтобы
 * тесты видели его FQN без правки апстримного src/test. В прод-джар он попадает, но игрой
 * не загружается; JUnit API подключён в main как compileOnly.
 */
package net.neoforged.testframework.junit;

import java.io.IOException;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import com.google.common.base.Stopwatch;
import com.mojang.authlib.services.ServicesKeySet;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Lifecycle;

import net.minecraft.SharedConstants;
import net.minecraft.SystemReport;
import net.minecraft.commands.Commands;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.progress.LoggingLevelLoadListener;
import net.minecraft.server.notifications.NotificationManager;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.debugchart.LocalSampleLogger;
import net.minecraft.util.debugchart.SampleLogger;
import net.minecraft.world.Difficulty;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelDataAndDimensions;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.slf4j.Logger;

import appeng.core.AppEngServer;
import nfcompat.NfCompat;

/**
 * A {@link ParameterResolver} that provides a {@link MinecraftServer} parameter.
 * <p>
 * The server is ephemeral, meaning that it doesn't store any data, and only has a void overworld available.
 * <p>
 * The server instance is lazy (only created if a test needs it) and <strong>singleton</strong> for the whole JUnit
 * session.
 *
 * @see ExtendWith
 */
public class EphemeralTestServerProvider implements ParameterResolver, Extension, BeforeAllCallback {
    public static final AtomicReference<MinecraftServer> SERVER = new AtomicReference<>();
    public static final AtomicBoolean IN_CONSTRUCTION = new AtomicBoolean();

    private static volatile boolean gameBootstrapped;

    /**
     * Bootstrap игры до первого теста любого класса.
     * <p>
     * В NeoForge мод уже загружен и игра забутстрапена к началу любого теста - в том числе
     * для тестов БЕЗ {@code @BootstrapMinecraft} (они спокойно берут Items/ItemStack). У нас
     * запуска игры нет, поэтому расширение регистрируется глобально через автодетект JUnit
     * (см. {@code junit.jupiter.extensions.autodetection.enabled} в build.gradle), и делает
     * ту же работу до первого теста.
     */
    @Override
    public void beforeAll(ExtensionContext context) {
        bootstrapMinecraft();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        return parameterContext.getParameter().getType() == MinecraftServer.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        return grabServer();
    }

    /**
     * Инициализация игры для тестов.
     * <p>
     * Порядок как у любого загрузчика модов, только руками:
     * 1. {@code Bootstrap.bootStrap()} - ванильные записи в BuiltInRegistries и их freeze.
     *    Раньше его звать нельзя: любой доступ к BuiltInRegistries до bootstrap бросает
     *    "Not bootstrapped (called from registry ...)".
     * 2. Разморозка BuiltInRegistries (в ванили API для этого нет - NeoForge/Fabric патчат
     *    {@code MappedRegistry.frozen} своими миксинами; здесь то же самое рефлексией).
     * 3. Init мода (AppEngServer + runRegistrationPhases) - AE2 раскладывает свои
     *    DeferredRegister'ы уже в живые реестры, как на Fabric в dev-рантайме.
     */
    public static synchronized void bootstrapMinecraft() {
        if (gameBootstrapped) {
            return;
        }
        var stopwatch = Stopwatch.createStarted();

        // Без этого Identifier/ResourceLocation валидация падает: версия игры не определена.
        SharedConstants.tryDetectVersion();

        // Ванильные записи + freeze всех BuiltInRegistries.
        Bootstrap.bootStrap();
        unfreezeBuiltInRegistries();

        // Инициализация AE2 ровно как в NfCompatServer#onInitializeServer: конструктор
        // AppEngServer вешает слушателей RegisterEvent и трогает AEBlocks/AEItems/AEComponents
        // (DeferredRegister регистрирует жадно), а runRegistrationPhases прогоняет сами фазы.
        new AppEngServer(NeoForge.EVENT_BUS, NfCompat.container());
        NfCompat.runRegistrationPhases();

        // Регистрация мода закончена - замораживаем реестры обратно, как это делает загрузчик
        // после RegisterEvent. freeze() заодно привязывает значения к holder'ам, data-компоненты
        // и теги: без этого любой ItemStack AE2 падает с "Components not bound yet".
        freezeBuiltInRegistries();

        gameBootstrapped = true;
        LogUtils.getLogger().info("Ephemeral test bootstrap finished in {} ms",
                stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    /**
     * Снимает замок с реестров, которые ванильный bootstrap закрыл.
     * <p>
     * {@code MappedRegistry#freeze()} делает две вещи, обе мешают поздней регистрации мода:
     * ставит {@code frozen = true} (тогда {@code register} бросает IllegalStateException) и
     * обнуляет {@code unregisteredIntrusiveHolders} (тогда конструктор {@code Item}, который
     * создаёт intrusive holder, падает с "This registry can't create intrusive holders").
     * Возвращаем оба поля в размороженное состояние - ровно то, что делают загрузчики модов.
     */
    private static void unfreezeBuiltInRegistries() {
        try {
            var frozenField = MappedRegistry.class.getDeclaredField("frozen");
            frozenField.setAccessible(true);
            var intrusiveField = MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders");
            intrusiveField.setAccessible(true);

            for (var registry : BuiltInRegistries.REGISTRY) {
                if (registry instanceof MappedRegistry<?> mapped) {
                    frozenField.setBoolean(mapped, false);
                }
            }

            // Возвращаем карту intrusive holder'ов НЕ всем реестрам, а только тем, которые
            // ваниль создала с её поддержкой (registerSimple/DefaultedWithIntrusiveHolders):
            // FLUID, BLOCK, ENTITY_TYPE, ITEM, BLOCK_ENTITY_TYPE. Именно их конструкторы
            // (Item, Block, EntityType, ...) зовут createIntrusiveHolder. Остальным реестрам
            // карта не нужна и вредна: MappedRegistry#register в этом случае требует, чтобы
            // значение уже имело holder, и падает с "Missing intrusive holder" на обычной
            // регистрации (так было, например, с SOUND_EVENT).
            restoreIntrusiveHolders(intrusiveField,
                    BuiltInRegistries.FLUID,
                    BuiltInRegistries.BLOCK,
                    BuiltInRegistries.ENTITY_TYPE,
                    BuiltInRegistries.ITEM,
                    BuiltInRegistries.BLOCK_ENTITY_TYPE);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LogUtils.getLogger().warn("Could not unfreeze built-in registries, "
                    + "modded registry entries will not be visible to tests", e);
        }
    }

    private static void restoreIntrusiveHolders(java.lang.reflect.Field intrusiveField, Object... registries)
            throws IllegalAccessException {
        for (var registry : registries) {
            if (registry instanceof MappedRegistry<?> mapped && intrusiveField.get(mapped) == null) {
                intrusiveField.set(mapped, new java.util.IdentityHashMap<>());
            }
        }
    }

    /** Замораживает все ванильные реестры обратно (привязывает значения, компоненты и теги). */
    private static void freezeBuiltInRegistries() {
        for (var registry : BuiltInRegistries.REGISTRY) {
            try {
                registry.freeze();
            } catch (RuntimeException e) {
                LogUtils.getLogger().warn("Could not freeze registry {}", registry.key().identifier(), e);
            }
        }
    }

    public static MinecraftServer grabServer() {
        var current = ServerLifecycleHooks.getCurrentServer();
        if (current != null) {
            return current;
        }

        bootstrapMinecraft();

        if (IN_CONSTRUCTION.compareAndSet(false, true)) {
            try {
                final var tempDir = Files.createTempDirectory("test-mc-server-");
                LevelStorageSource storage = LevelStorageSource.createDefault(tempDir.resolve("world"));
                LevelStorageSource.LevelStorageAccess storageAccess = storage.validateAndCreateAccess("main");
                var packrepository = net.minecraft.server.packs.repository.ServerPacksSource
                        .createPackRepository(storageAccess);
                MinecraftServer.spin(
                        thread -> JUnitServer.create(thread, tempDir, storageAccess, packrepository));
            } catch (Exception ex) {
                LogUtils.getLogger().error(LogUtils.FATAL_MARKER, "Failed to start the minecraft server", ex);
                IN_CONSTRUCTION.set(false);
                throw new RuntimeException(ex);
            }
        }

        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
        while (SERVER.get() == null) {
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("Ephemeral test server did not start within 120 seconds");
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }

        return ServerLifecycleHooks.getCurrentServer();
    }

    public static class JUnitServer extends MinecraftServer {
        private static final Logger LOGGER = LogUtils.getLogger();
        private static final Services NO_SERVICES = new Services(null, ServicesKeySet.EMPTY, null, null, null);
        private static final GameRules TEST_GAME_RULES = Util.make(new GameRules(FeatureFlags.REGISTRY.allFlags()),
                rules -> {
                    rules.set(GameRules.SPAWN_MOBS, false, null);
                    rules.set(GameRules.ADVANCE_WEATHER, false, null);
                });
        private static final WorldOptions WORLD_OPTIONS = new WorldOptions(0L, false, false);

        public static JUnitServer create(
                Thread thread, Path tempDir, LevelStorageSource.LevelStorageAccess access,
                net.minecraft.server.packs.repository.PackRepository resources) {
            // Сервер не должен держать JUnit-воркер: иначе Gradle-тест висит после последнего теста.
            thread.setDaemon(true);

            resources.reload();
            WorldDataConfiguration config = new WorldDataConfiguration(
                    new DataPackConfig(new ArrayList<>(resources.getAvailableIds()), List.of()),
                    FeatureFlags.REGISTRY.allFlags());
            LevelSettings levelsettings = new LevelSettings(
                    "Test Level",
                    GameType.CREATIVE,
                    new LevelSettings.DifficultySettings(Difficulty.NORMAL, false, false),
                    true,
                    config);
            WorldLoader.PackConfig packConfig = new WorldLoader.PackConfig(resources, config, false, true);
            WorldLoader.InitConfig initConfig = new WorldLoader.InitConfig(packConfig,
                    Commands.CommandSelection.DEDICATED, LevelBasedPermissionSet.OWNER);

            try {
                LOGGER.debug("Starting resource loading");
                Stopwatch stopwatch = Stopwatch.createStarted();
                WorldStem worldstem = Util.blockUntilDone(
                        exec -> WorldLoader.load(
                                initConfig,
                                ctx -> {
                                    Registry<LevelStem> registry = new MappedRegistry<>(Registries.LEVEL_STEM,
                                            Lifecycle.stable()).freeze();
                                    WorldDimensions.Complete complete = ctx.datapackWorldRegistries()
                                            .lookupOrThrow(Registries.WORLD_PRESET)
                                            .getOrThrow(WorldPresets.FLAT)
                                            .value()
                                            .createWorldDimensions()
                                            .bake(registry);
                                    return new WorldLoader.DataLoadOutput<>(
                                            new LevelDataAndDimensions.WorldDataAndGenSettings(
                                                    new PrimaryLevelData(levelsettings,
                                                            complete.specialWorldProperty(),
                                                            complete.lifecycle()),
                                                    new WorldGenSettings(WORLD_OPTIONS,
                                                            new WorldDimensions(complete.dimensions()))),
                                            complete.dimensionsRegistryAccess());
                                },
                                WorldStem::new,
                                Util.backgroundExecutor(),
                                exec))
                        .get();
                stopwatch.stop();
                LOGGER.debug("Finished resource loading after {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
                return new JUnitServer(thread, access, resources, worldstem, tempDir);
            } catch (Exception exception) {
                LOGGER.warn("Failed to load vanilla datapack, bit oops", exception);
                throw new IllegalStateException(exception);
            }
        }

        private final Path tempDir;
        private final NotificationManager notificationService = NOTIFICATION_SERVICE;
        private static final NotificationManager NOTIFICATION_SERVICE = new NotificationManager();

        public JUnitServer(
                Thread thread,
                LevelStorageSource.LevelStorageAccess access,
                net.minecraft.server.packs.repository.PackRepository pack,
                WorldStem stem,
                Path tempDir) {
            super(thread, access, pack, stem, java.util.Optional.of(TEST_GAME_RULES), Proxy.NO_PROXY,
                    DataFixers.getDataFixer(), NO_SERVICES, LoggingLevelLoadListener.forDedicatedServer(), false,
                    NOTIFICATION_SERVICE);
            this.tempDir = tempDir;
        }

        @Override
        public boolean initServer() throws IOException {
            this.setPlayerList(
                    new PlayerList(this, this.registries(), this.playerDataStorage, this.notificationService) {
                    });
            // Шим-замена ServerLifecycleHooks.handleServerAboutToStart/handleServerStarting:
            // сначала текущий сервер (его читают AE2 и grabServer), потом NeoForge-события.
            ServerLifecycleHooks.setCurrentServer(this);
            NeoForge.EVENT_BUS.post(new ServerAboutToStartEvent(this));
            LOGGER.info("Started ephemeral JUnit server");
            return true;
        }

        @Override
        public void tickServer(BooleanSupplier sup) {
            super.tickServer(sup);
            // Consider the server started the first time it ticks
            SERVER.set(this);
            if (ServerLifecycleHooks.getCurrentServer() == this
                    && !startedEventFired) {
                startedEventFired = true;
                NeoForge.EVENT_BUS.post(new ServerStartedEvent(this));
            }
        }

        private boolean startedEventFired;

        @Override
        public boolean saveEverything(boolean p_195515_, boolean p_195516_, boolean p_195517_) {
            // The server is ephemeral
            return false;
        }

        @Override
        public void stopServer() {
            LOGGER.info("Stopping server");
            getPlayerList().removeAll();

            try {
                storageSource.deleteLevel();
                this.storageSource.close();
            } catch (IOException ioexception) {
                LOGGER.error("Failed to unlock level {}", this.storageSource.getLevelId(), ioexception);
            }
            // tempDir удалять не нужно: он в системной temp, ОС подчистит. Но сервер держит
            // его на случай отладки - путь пишем в лог.
            LOGGER.debug("Ephemeral server temp dir: {}", tempDir);
            LogManager.shutdown();
        }

        @Override
        public void waitUntilNextTick() {
            this.runAllTasks();
        }

        @Override
        public SystemReport fillServerSystemReport(SystemReport report) {
            report.setDetail("Type", "Test ephemeral server");
            return report;
        }

        @Override
        public boolean isHardcore() {
            return false;
        }

        @Override
        public LevelBasedPermissionSet operatorUserPermissions() {
            return LevelBasedPermissionSet.ALL;
        }

        @Override
        public PermissionSet getFunctionCompilationPermissions() {
            return LevelBasedPermissionSet.OWNER;
        }

        @Override
        public boolean shouldRconBroadcast() {
            return false;
        }

        @Override
        public boolean isDedicatedServer() {
            return false;
        }

        @Override
        public int getRateLimitPacketsPerSecond() {
            return 0;
        }

        @Override
        public int getCommandSpamThresholdSeconds() {
            return 0;
        }

        @Override
        public int getChatSpamThresholdSeconds() {
            return 0;
        }

        @Override
        public boolean useNativeTransport() {
            return false;
        }

        @Override
        public boolean isPublished() {
            return false;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }

        @Override
        public boolean isSingleplayerOwner(NameAndId p_433457_) {
            return false;
        }

        private final LocalSampleLogger sampleLogger = new LocalSampleLogger(1);

        @Override
        protected SampleLogger getTickTimeLogger() {
            return sampleLogger;
        }

        @Override
        public boolean isTickTimeLoggingEnabled() {
            return false;
        }

        @Override
        public int getMaxPlayers() {
            return 1;
        }
    }
}

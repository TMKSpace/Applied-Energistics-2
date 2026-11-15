# -*- coding: utf-8 -*-
"""Генерация FML-слоя шима: net.neoforged.fml.*, api.distmarker, neoforge.common.Tags."""
import io
import os

ROOT = r"D:\Code\java\ae2-fabric-probe\compat\java"

FILES = {}

FILES["net/neoforged/api/distmarker/Dist.java"] = '''/*
 * Часть nfcompat. Сторона, на которой загружен мод.
 *
 * В NeoForge это часть API загрузчика; на Fabric аналог — EnvType, но константы те же две.
 */
package net.neoforged.api.distmarker;

public enum Dist {
    CLIENT,
    DEDICATED_SERVER;

    public boolean isClient() {
        return this == CLIENT;
    }

    public boolean isDedicatedServer() {
        return this == DEDICATED_SERVER;
    }
}
'''

FILES["net/neoforged/fml/loading/moddiscovery/ModInfo.java"] = '''/*
 * Часть nfcompat. Метаданные мода.
 *
 * В NeoForge ModInfo — большой record из mods.toml. У нас он собирается из
 * fabric.mod.json через FabricLoader.
 */
package net.neoforged.fml.loading.moddiscovery;

public final class ModInfo {
    private final String modId;
    private final String displayName;
    private final String version;

    public ModInfo(String modId, String displayName, String version) {
        this.modId = modId;
        this.displayName = displayName;
        this.version = version;
    }

    public String getModId() {
        return modId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return modId + "[" + version + "]";
    }
}
'''

FILES["net/neoforged/fml/loading/LoadingModList.java"] = '''/*
 * Часть nfcompat. Список загружаемых модов на момент загрузки.
 *
 * В NeoForge это отдельная структура сканирования. У нас — просто снимок из FabricLoader.
 */
package net.neoforged.fml.loading;

import java.util.List;

import net.neoforged.fml.loading.moddiscovery.ModInfo;

public final class LoadingModList {
    private final List<ModInfo> mods;

    public LoadingModList(List<ModInfo> mods) {
        this.mods = List.copyOf(mods);
    }

    public List<ModInfo> getMods() {
        return mods;
    }
}
'''

FILES["net/neoforged/fml/loading/FMLEnvironment.java"] = '''/*
 * Часть nfcompat. Окружение загрузчика.
 */
package net.neoforged.fml.loading;

import net.fabricmc.loader.api.FabricLoader;
import net.neoforged.api.distmarker.Dist;

public final class FMLEnvironment {
    public static final Dist dist = FabricLoader.getInstance().getEnvironmentType() == net.fabricmc.api.EnvType.CLIENT
            ? Dist.CLIENT
            : Dist.DEDICATED_SERVER;

    private FMLEnvironment() {
    }

    public static boolean isProduction() {
        return !FabricLoader.getInstance().isDevelopmentEnvironment();
    }
}
'''

FILES["net/neoforged/fml/loading/FMLLoader.java"] = '''/*
 * Часть nfcompat. Точка входа загрузчика.
 *
 * AE2 зовёт getCurrentOrNull()/getCurrent() ради dist, production-флага и списка модов.
 * Разницы между "текущим" и "главным" загрузчиком на Fabric нет, поэтому оба метода
 * возвращают один и тот же объект.
 */
package net.neoforged.fml.loading;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.moddiscovery.ModInfo;
import org.jetbrains.annotations.Nullable;

public final class FMLLoader {
    private static final FMLLoader CURRENT = new FMLLoader();

    private final LoadingModList loadingModList;

    private FMLLoader() {
        List<ModInfo> mods = new ArrayList<>();
        for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            var metadata = container.getMetadata();
            mods.add(new ModInfo(metadata.getId(), metadata.getName(),
                    metadata.getVersion() == null ? "0" : metadata.getVersion().getFriendlyString()));
        }
        this.loadingModList = new LoadingModList(mods);
    }

    public static FMLLoader getCurrent() {
        return CURRENT;
    }

    @Nullable
    public static FMLLoader getCurrentOrNull() {
        return CURRENT;
    }

    public Dist getDist() {
        return FMLEnvironment.dist;
    }

    public boolean isProduction() {
        return FMLEnvironment.isProduction();
    }

    public LoadingModList getLoadingModList() {
        return loadingModList;
    }
}
'''

FILES["net/neoforged/fml/util/thread/SidedThreadGroups.java"] = '''/*
 * Часть nfcompat. Группы потоков клиента и сервера.
 *
 * В NeoForge загрузчик сам создаёт ThreadGroup для каждой стороны, а код модов сравнивает
 * Thread.currentThread().getThreadGroup() с ними. На Fabric группу серверного потока
 * создаёт сам Minecraft, поэтому SERVER здесь выставляется снаружи: на старте сервера
 * в него кладут текущую группу потока. До этого момента там заглушка.
 *
 * TODO: подключить хук на старт сервера (Fabric ServerLifecycleEvents) и заполнить SERVER.
 */
package net.neoforged.fml.util.thread;

public final class SidedThreadGroups {
    /** Группа серверного потока. Заполняется хуком на старте сервера. */
    public static volatile ThreadGroup SERVER = new ThreadGroup("nfcompat-server-pending");

    private SidedThreadGroups() {
    }

    public static void setServerThreadGroup(ThreadGroup group) {
        SERVER = group;
    }
}
'''

FILES["net/neoforged/fml/common/Mod.java"] = '''/*
 * Часть nfcompat. Аннотация точки входа мода.
 *
 * На Fabric точки входа берутся из fabric.mod.json, поэтому аннотация нужна только
 * для компиляции исходников AE2. Никакой автоматики за ней нет.
 */
package net.neoforged.fml.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import net.neoforged.api.distmarker.Dist;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Mod {
    String value();

    Dist dist() default Dist.CLIENT;
}
'''

FILES["net/neoforged/fml/common/EventBusSubscriber.java"] = '''/*
 * Часть nfcompat. Автоматическая подписка класса на шину.
 *
 * На Fabric этого механизма нет: подписка идёт через fabric.mod.json и явные вызовы.
 * Заглушка нужна для компиляции.
 */
package net.neoforged.fml.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import net.neoforged.api.distmarker.Dist;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EventBusSubscriber {
    String modid() default "";

    Dist[] value() default {};

    Bus bus() default Bus.GAME;

    enum Bus {
        GAME,
        MOD
    }
}
'''

FILES["net/neoforged/fml/ModContainer.java"] = '''/*
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

public class ModContainer {
    private final ModInfo modInfo;
    private final IEventBus eventBus;
    private final List<ModConfig> configs = new ArrayList<>();

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

    public void registerConfig(ModConfig.Type type, ModConfigSpec spec, String fileName) {
        // TODO: на Fabric конфиги должен подхватывать Forge Config API Port,
        // сейчас объект ModConfig только фиксируется здесь.
        if (spec != null) {
            spec.getClass();
        }
        this.configs.add(null);
    }

    public List<ModConfig> getConfigs() {
        return configs;
    }
}
'''

FILES["net/neoforged/fml/ModList.java"] = '''/*
 * Часть nfcompat. Список загруженных модов.
 *
 * Реализован поверх FabricLoader: isLoaded -> isModLoaded, контейнеры строятся
 * из метаданных fabric.mod.json.
 */
package net.neoforged.fml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.fabricmc.loader.api.FabricLoader;
import net.neoforged.fml.loading.moddiscovery.ModInfo;

public final class ModList {
    private static final ModList INSTANCE = new ModList();

    private final Map<String, ModContainer> byId = new HashMap<>();

    private ModList() {
        for (var container : FabricLoader.getInstance().getAllMods()) {
            var metadata = container.getMetadata();
            var info = new ModInfo(metadata.getId(), metadata.getName(),
                    metadata.getVersion() == null ? "0" : metadata.getVersion().getFriendlyString());
            byId.put(info.getModId(), new ModContainer(info, null));
        }
    }

    public static ModList get() {
        return INSTANCE;
    }

    public boolean isLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    public Optional<ModContainer> getModContainerById(String modId) {
        return Optional.ofNullable(byId.get(modId));
    }

    public List<ModContainer> getMods() {
        return new ArrayList<>(byId.values());
    }
}
'''

FILES["net/neoforged/fml/ModWorkManager.java"] = '''/*
 * Часть nfcompat. Исполнители для параллельной загрузки.
 *
 * У NeoForge синхронный исполнитель — главный поток загрузки, параллельный — пул.
 * На Fabric аналогичного этапа нет, поэтому оба — обычные общие пулы.
 */
package net.neoforged.fml;

import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

public final class ModWorkManager {
    private static final Executor SYNC = Runnable::run;
    private static final Executor PARALLEL = ForkJoinPool.commonPool();

    private ModWorkManager() {
    }

    public static Executor syncExecutor() {
        return SYNC;
    }

    public static Executor parallelExecutor() {
        return PARALLEL;
    }
}
'''

FILES["net/neoforged/fml/DeferredWorkQueue.java"] = '''/*
 * Часть nfcompat. Очередь отложенной работы мода.
 *
 * В NeoForge через неё мод откладывает работу со своих потоков в главный поток.
 * Здесь просто запоминаем задачи; вычитывает их тот, кто создал очередь.
 */
package net.neoforged.fml;

import java.util.ArrayList;
import java.util.List;

public class DeferredWorkQueue {
    private final List<Runnable> work = new ArrayList<>();

    public void enqueueWork(Runnable runnable) {
        work.add(runnable);
    }

    public List<Runnable> drain() {
        var copy = List.copyOf(work);
        work.clear();
        return copy;
    }
}
'''

FILES["net/neoforged/fml/ModLoader.java"] = '''/*
 * Часть nfcompat. Запуск событий загрузчика.
 *
 * postEvent — просто пост на общую шину.
 * dispatchParallelEvent в NeoForge раздаёт событие каждому моду на своём потоке;
 * у нас моды не перечисляются, поэтому событие создаётся и постится один раз.
 */
package net.neoforged.fml;

import java.util.function.BiFunction;
import java.util.function.Consumer;

import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.lifecycle.ParallelDispatchEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class ModLoader {
    private ModLoader() {
    }

    public static void postEvent(Event event) {
        NeoForge.EVENT_BUS.post(event);
    }

    /**
     * Упрощённый аналог NeoForge: одно событие на «все моды» (фактически на наш мод).
     */
    public static <T extends ParallelDispatchEvent> void dispatchParallelEvent(
            String description,
            java.util.concurrent.Executor syncExecutor,
            java.util.concurrent.Executor parallelExecutor,
            Runnable initialWork,
            BiFunction<ModContainer, DeferredWorkQueue, T> eventCreator) {
        initialWork.run();
        var queue = new DeferredWorkQueue();
        var container = new ModContainer(new net.neoforged.fml.loading.moddiscovery.ModInfo(
                "appliedenergistics2", "Applied Energistics 2", "0"), NeoForge.EVENT_BUS);
        var event = eventCreator.apply(container, queue);
        NeoForge.EVENT_BUS.post(event);
        for (Runnable r : queue.drain()) {
            r.run();
        }
    }

    /** Перегрузка с обработчиком события, как в NeoForge. */
    public static <T extends ParallelDispatchEvent> void dispatchParallelEvent(
            String description,
            java.util.concurrent.Executor syncExecutor,
            java.util.concurrent.Executor parallelExecutor,
            Runnable initialWork,
            BiFunction<ModContainer, DeferredWorkQueue, T> eventCreator,
            Consumer<T> eventHandler) {
        initialWork.run();
        var queue = new DeferredWorkQueue();
        var container = new ModContainer(new net.neoforged.fml.loading.moddiscovery.ModInfo(
                "appliedenergistics2", "Applied Energistics 2", "0"), NeoForge.EVENT_BUS);
        var event = eventCreator.apply(container, queue);
        NeoForge.EVENT_BUS.post(event);
        eventHandler.accept(event);
        for (Runnable r : queue.drain()) {
            r.run();
        }
    }
}
'''

FILES["net/neoforged/fml/InterModComms.java"] = '''/*
 * Часть nfcompat. Межмодовые сообщения.
 *
 * В NeoForge это шина обмена сообщениями между модами на этапе загрузки. На Fabric
 * прямого аналога нет; сообщения просто никуда не уходят, но факт отправки сохраняем.
 */
package net.neoforged.fml;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class InterModComms {
    public record Message(String senderModId, String modId, String key, Supplier<?> value) {
    }

    private static final List<Message> SENT = new ArrayList<>();

    private InterModComms() {
    }

    public static boolean sendTo(String modId, String key, Supplier<?> value) {
        SENT.add(new Message("appliedenergistics2", modId, key, value));
        return true;
    }

    public static List<Message> getSentMessages() {
        return List.copyOf(SENT);
    }
}
'''

FILES["net/neoforged/fml/event/lifecycle/ParallelDispatchEvent.java"] = '''/*
 * Часть nfcompat. Базовое событие этапа загрузки, которое NeoForge раздаёт по модам.
 */
package net.neoforged.fml.event.lifecycle;

import net.neoforged.bus.api.Event;
import net.neoforged.fml.DeferredWorkQueue;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.IModBusEvent;

public class ParallelDispatchEvent extends Event implements IModBusEvent {
    private final ModContainer modContainer;
    private final DeferredWorkQueue deferredWorkQueue;

    public ParallelDispatchEvent(ModContainer modContainer, DeferredWorkQueue deferredWorkQueue) {
        this.modContainer = modContainer;
        this.deferredWorkQueue = deferredWorkQueue;
    }

    public ModContainer getModContainer() {
        return modContainer;
    }

    public DeferredWorkQueue getDeferredWorkQueue() {
        return deferredWorkQueue;
    }

    public void enqueueWork(Runnable runnable) {
        deferredWorkQueue.enqueueWork(runnable);
    }
}
'''

for name, cls in (("FMLCommonSetupEvent", "общая инициализация"),
                  ("FMLClientSetupEvent", "инициализация клиента"),
                  ("FMLDedicatedServerSetupEvent", "инициализация выделенного сервера")):
    FILES["net/neoforged/fml/event/lifecycle/%s.java" % name] = '''/*
 * Часть nfcompat. Событие этапа загрузки: %s.
 *
 * У нас этот этап не разбит на фазы: событие существует как тип и может быть
 * отправлено вручную. Подписчики AE2 получат его, когда шим решит, что пора.
 */
package net.neoforged.fml.event.lifecycle;

public class %s extends ParallelDispatchEvent {
    public %s(net.neoforged.fml.ModContainer modContainer, net.neoforged.fml.DeferredWorkQueue deferredWorkQueue) {
        super(modContainer, deferredWorkQueue);
    }
}
''' % (cls, name, name)

FILES["net/neoforged/fml/event/lifecycle/InterModEnqueueEvent.java"] = '''/*
 * Часть nfcompat. Этап, на котором моды рассылают IMC-сообщения другим модам.
 */
package net.neoforged.fml.event.lifecycle;

import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

public class InterModEnqueueEvent extends Event implements IModBusEvent {
    public InterModEnqueueEvent() {
    }
}
'''

FILES["net/neoforged/fml/event/config/ModConfigEvent.java"] = '''/*
 * Часть nfcompat. События конфигов.
 *
 * Forge Config API Port предоставляет сам конфиг-механизм (ModConfigSpec, ModConfig),
 * но не эти события, поэтому они объявлены здесь. Поля названы как в NeoForge.
 */
package net.neoforged.fml.event.config;

import net.neoforged.bus.api.Event;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.IModBusEvent;

public abstract class ModConfigEvent extends Event implements IModBusEvent {
    private final ModConfig config;

    protected ModConfigEvent(ModConfig config) {
        this.config = config;
    }

    public ModConfig getConfig() {
        return config;
    }

    /** Конфиг загружен впервые. */
    public static class Loading extends ModConfigEvent {
        public Loading(ModConfig config) {
            super(config);
        }
    }

    /** Конфиг перечитан. */
    public static class Reloading extends ModConfigEvent {
        public Reloading(ModConfig config) {
            super(config);
        }
    }

    /** Конфиг выгружен. */
    public static class Unloading extends ModConfigEvent {
        public Unloading(ModConfig config) {
            super(config);
        }
    }
}
'''

FILES["net/neoforged/neoforge/common/util/FakePlayerFactory.java"] = '''/*
 * Часть nfcompat. Фабрика фейковых игроков.
 *
 * В NeoForge это обёртка над GameProfile + ServerLevel, которая держит одного
 * фейкового игрока на уровень. Ванильного аналога нет, поэтому создаём
 * настоящего ServerPlayer с заданным профилем и без сети.
 */
package net.neoforged.neoforge.common.util;

import com.mojang.authlib.GameProfile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public final class FakePlayerFactory {
    private FakePlayerFactory() {
    }

    @Nullable
    public static ServerPlayer get(ServerLevel level, GameProfile profile) {
        // TODO: кэшировать по уровню и профилю, как в NeoForge; сейчас создаём каждый раз.
        var server = level.getServer();
        if (server == null) {
            return null;
        }
        return new ServerPlayer(server, level, profile, server.getPlayerList().getPlayers().isEmpty()
                ? null
                : server.getPlayerList().getPlayers().get(0).clientInformation());
    }
}
'''

TAGS_HEAD = '''/*
 * Часть nfcompat. Convention-теги NeoForge (namespace "c").
 *
 * В NeoForge Tags.java — 154 КБ констант на все случаи жизни. Здесь только те,
 * что реально использует AE2 (33 штуки). Каждая — тот же TagKey с тем же путём,
 * что и в NeoForge, поэтому данные тегов совпадают.
 */
package net.neoforged.neoforge.common;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;

public final class Tags {
    private static final String NAMESPACE = "c";

    private Tags() {
    }

    private static TagKey<Item> item(String path) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(NAMESPACE, path));
    }

    private static TagKey<Block> block(String path) {
        return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(NAMESPACE, path));
    }

    private static TagKey<Biome> biome(String path) {
        return TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(NAMESPACE, path));
    }
'''

TAGS_ITEMS = [
    ("BUDDING_BLOCKS", "budding_blocks"), ("BUDS", "buds"), ("CHESTS", "chests"),
    ("CHESTS_WOODEN", "chests/wooden"), ("CLUSTERS", "clusters"),
    ("DUSTS", "dusts"), ("DUSTS_GLOWSTONE", "dusts/glowstone"), ("DUSTS_REDSTONE", "dusts/redstone"),
    ("ENDER_PEARLS", "ender_pearls"), ("GEMS", "gems"), ("GEMS_DIAMOND", "gems/diamond"),
    ("GEMS_QUARTZ", "gems/quartz"), ("GLASS_BLOCKS", "glass_blocks"),
    ("GLASS_BLOCKS_CHEAP", "glass_blocks/cheap"), ("INGOTS_COPPER", "ingots/copper"),
    ("INGOTS_GOLD", "ingots/gold"), ("INGOTS_IRON", "ingots/iron"),
    ("NUGGETS_GOLD", "nuggets/gold"), ("NUGGETS_IRON", "nuggets/iron"),
    ("RODS_WOODEN", "rods/wooden"), ("STONES", "stones"), ("STORAGE_BLOCKS", "storage_blocks"),
]
TAGS_BLOCKS = [
    ("BUDDING_BLOCKS", "budding_blocks"), ("BUDS", "buds"), ("CHESTS", "chests"),
    ("CHESTS_WOODEN", "chests/wooden"), ("CLUSTERS", "clusters"),
    ("GLASS_BLOCKS", "glass_blocks"), ("GLASS_BLOCKS_CHEAP", "glass_blocks/cheap"),
    ("RELOCATION_NOT_SUPPORTED", "relocation_not_supported"),
    ("STONES", "stones"), ("STORAGE_BLOCKS", "storage_blocks"),
]
TAGS_BIOMES = [
    ("IS_COLD", "is_cold"), ("IS_OCEAN", "is_ocean"), ("IS_PLAINS", "is_plains"),
    ("IS_SANDY", "is_sandy"), ("IS_SNOWY", "is_snowy"),
]

parts = [TAGS_HEAD]
parts.append("\n    public static final class Items {\n        private Items() {\n        }\n\n")
for name, path in TAGS_ITEMS:
    parts.append('        public static final TagKey<Item> %s = item("%s");\n' % (name, path))
parts.append("    }\n\n    public static final class Blocks {\n        private Blocks() {\n        }\n\n")
for name, path in TAGS_BLOCKS:
    parts.append('        public static final TagKey<Block> %s = block("%s");\n' % (name, path))
parts.append("    }\n\n    public static final class Biomes {\n        private Biomes() {\n        }\n\n")
for name, path in TAGS_BIOMES:
    parts.append('        public static final TagKey<Biome> %s = biome("%s");\n' % (name, path))
parts.append("    }\n}\n")
FILES["net/neoforged/neoforge/common/Tags.java"] = "".join(parts)

for rel, text in FILES.items():
    path = os.path.join(ROOT, rel.replace("/", os.sep))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)
    print("wrote", rel)
print("итого:", len(FILES))

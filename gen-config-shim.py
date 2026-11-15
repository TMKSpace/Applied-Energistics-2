# -*- coding: utf-8 -*-
"""Свой config-слой: ModConfigSpec вендорится из NeoForge, fml.config пишем сами."""
import io
import os
import shutil

ROOT = r"D:\Code\java\ae2-fabric-probe\compat\java"
NF = os.path.expandvars(r"%LOCALAPPDATA%\Temp\opencode\nf")

FILES = {}

FILES["net/neoforged/fml/Logging.java"] = '''/*
 * Часть nfcompat. Маркеры логов загрузчика.
 *
 * В NeoForge это класс FML с маркерами для разных подсистем. Нам нужен один — CORE,
 * его использует вендоренный ModConfigSpec.
 */
package net.neoforged.fml;

import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

public final class Logging {
    public static final Marker CORE = MarkerManager.getMarker("CORE");
    public static final Marker LOADING = MarkerManager.getMarker("LOADING");

    private Logging() {
    }
}
'''

FILES["net/neoforged/fml/config/IConfigSpec.java"] = '''/*
 * Часть nfcompat. Контракт «спецификации» конфига.
 *
 * В NeoForge объявлен в загрузчике (FancyModLoader). Здесь переписан по тому, что
 * реально вызывает вендоренный ModConfigSpec.
 */
package net.neoforged.fml.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableCommentedConfig;
import org.jetbrains.annotations.Nullable;

public interface IConfigSpec {
    /** {@return true, если в спецификации нет ни одного значения} */
    boolean isEmpty();

    /** Проверяет, что спецификация допустима для данного типа конфига. */
    void validateSpec(ModConfig config);

    /** {@return true, если переданный конфиг соответствует спецификации} */
    boolean isCorrect(UnmodifiableCommentedConfig config);

    /** Правит переданный конфиг по спецификации. */
    void correct(CommentedConfig config);

    /** Привязывает загруженный конфиг к спецификации (или отвязывает, если null). */
    void acceptConfig(@Nullable ILoadedConfig config);

    /**
     * Загруженный на диск конфиг.
     */
    interface ILoadedConfig {
        CommentedConfig config();

        void save();
    }
}
'''

FILES["net/neoforged/fml/config/ModConfig.java"] = '''/*
 * Часть nfcompat. Один конфиг мода.
 *
 * В NeoForge этот класс живёт в загрузчике и умеет сам читать/писать toml через
 * nightconfig. У нас он только описывает конфиг; чтение и запись — TODO
 * (см. комментарий в ModContainer#registerConfig).
 */
package net.neoforged.fml.config;

import java.nio.file.Path;

import org.jetbrains.annotations.Nullable;

import net.minecraft.util.StringRepresentable;

public final class ModConfig {
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

        public String extension() {
            return serializedName + ".toml";
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

    @Nullable
    public Path getFullPath() {
        return path;
    }

    @Nullable
    public IConfigSpec.ILoadedConfig getLoadedConfig() {
        return loadedConfig;
    }

    public void setConfig(@Nullable IConfigSpec.ILoadedConfig loadedConfig) {
        this.loadedConfig = loadedConfig;
    }
}
'''

FILES["nfcompat/NfCompatInit.java"] = '''/*
 * Часть nfcompat. Точка входа шима на Fabric.
 *
 * Задачи на этом этапе ровно две:
 * 1. Поймать группу серверного потока и положить её в SidedThreadGroups.SERVER.
 *    AE2 сравнивает Thread.currentThread().getThreadGroup() == SidedThreadGroups.SERVER,
 *    а ThreadGroup у живого потока не подменить — значит её надо именно поймать.
 * 2. Держать ссылку на текущий сервер для ServerLifecycleHooks.
 *
 * Точки входа самого AE2 (NeoForge-овские @Mod-конструкторы) пока не мостим:
 * это следующий этап.
 */
package nfcompat;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.neoforged.fml.util.thread.SidedThreadGroups;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public final class NfCompatInit implements DedicatedServerModInitializer, ClientModInitializer {

    @Override
    public void onInitializeServer() {
        common();
    }

    @Override
    public void onInitializeClient() {
        common();
        // На клиенте серверного потока нет: в интеграционном сервере он появится
        // при открытии мира, и тогда его поймает SERVER_STARTING из common().
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            ServerLifecycleHooks.setCurrentServer(null);
        });
    }

    private static void common() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            SidedThreadGroups.setServerThreadGroup(Thread.currentThread().getThreadGroup());
            ServerLifecycleHooks.setCurrentServer(server);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            ServerLifecycleHooks.setCurrentServer(null);
        });
    }
}
'''

for rel, text in FILES.items():
    path = os.path.join(ROOT, rel.replace("/", os.sep))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)
    print("wrote", rel)

spec_src = os.path.join(NF, r"src\main\java\net\neoforged\neoforge\common\ModConfigSpec.java")
spec_dst = os.path.join(ROOT, "net", "neoforged", "neoforge", "common", "ModConfigSpec.java")
shutil.copyfile(spec_src, spec_dst)
print("vendored ModConfigSpec.java:", os.path.getsize(spec_dst), "bytes")

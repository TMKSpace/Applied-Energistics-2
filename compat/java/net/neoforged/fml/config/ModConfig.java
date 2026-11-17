/*
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
    public IConfigSpec.ILoadedConfig getLoadedConfig() {
        return loadedConfig;
    }

    public void setConfig(@Nullable IConfigSpec.ILoadedConfig loadedConfig) {
        this.loadedConfig = loadedConfig;
    }
}

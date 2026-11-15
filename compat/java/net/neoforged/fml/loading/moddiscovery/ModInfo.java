/*
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

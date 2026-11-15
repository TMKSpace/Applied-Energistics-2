/*
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

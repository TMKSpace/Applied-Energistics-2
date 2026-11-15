/*
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

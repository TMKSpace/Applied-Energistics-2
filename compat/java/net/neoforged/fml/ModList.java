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
        // Вне запуска игры FabricLoader недоступен (юнит-тесты) - тогда список модов пуст,
        // а isLoaded() отвечает false. Раньше здесь падал статический инициализатор, и
        // appeng.util.Platform (через ModList.get().isLoaded("ponder")) ронял весь класс.
        try {
            for (var container : FabricLoader.getInstance().getAllMods()) {
                var metadata = container.getMetadata();
                var info = new ModInfo(metadata.getId(), metadata.getName(),
                        metadata.getVersion() == null ? "0" : metadata.getVersion().getFriendlyString());
                byId.put(info.getModId(), new ModContainer(info, null));
            }
        } catch (Throwable ignored) {
            // Нет лоадера - нет модов.
        }
    }

    public static ModList get() {
        return INSTANCE;
    }

    public boolean isLoaded(String modId) {
        try {
            return FabricLoader.getInstance().isModLoaded(modId);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public Optional<ModContainer> getModContainerById(String modId) {
        return Optional.ofNullable(byId.get(modId));
    }

    /**
     * NeoForge собирает при сканировании модов их scan data (аннотации, цели и т.п.),
     * и AE2 этим ищет классы, помеченные её аннотациями (`@TestPlotClass`).
     * На Fabric такого сканирования нет, поэтому список пуст: тест-плоты просто
     * не будут найдены автоматически. Появится, если понадобится — через
     * `ClassGraph`/индекс Fabric, либо явным списком.
     */
    public List<net.neoforged.neoforgespi.language.ModFileScanData> getAllScanData() {
        return nfcompat.gametest.ModAnnotationScanner.scanAll();
    }

    public List<ModContainer> getMods() {
        return new ArrayList<>(byId.values());
    }
}

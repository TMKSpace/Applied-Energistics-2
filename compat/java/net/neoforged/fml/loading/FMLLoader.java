/*
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

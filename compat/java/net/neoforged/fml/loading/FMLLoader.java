/*
 * Шим nfcompat. Фасад загрузчика модов.
 *
 * AE2 зовёт getCurrentOrNull()/getCurrent() ради dist, списка модов и close-callback'ов.
 * Внутри настоящего запуска игры всё это даёт FabricLoader. В обычной JVM (юнит-тесты)
 * лоадера нет - и NeoForge в такой ситуации возвращает null (на это рассчитаны проверки
 * вида `var loader = FMLLoader.getCurrentOrNull(); return loader == null || ...` в
 * appeng.util.Platform). Поэтому и мы возвращаем null, а не падаем в статическом
 * инициализаторе.
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
    @Nullable
    private static final FMLLoader CURRENT = createCurrent();

    @Nullable
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

    @Nullable
    private static FMLLoader createCurrent() {
        try {
            return new FMLLoader();
        } catch (Throwable ignored) {
            // FabricLoader не инициализирован - мы не в запуске игры (юнит-тесты).
            return null;
        }
    }

    /**
     * @return текущий лоадер или {@code null}, если игра не запускалась через Fabric
     *         (юнит-тесты). Совпадает с семантикой NeoForge.
     */
    @Nullable
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
        if (loadingModList == null) {
            throw new IllegalStateException("FMLLoader is not available outside of a Fabric launch");
        }
        return loadingModList;
    }
}

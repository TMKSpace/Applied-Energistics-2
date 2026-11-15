/*
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

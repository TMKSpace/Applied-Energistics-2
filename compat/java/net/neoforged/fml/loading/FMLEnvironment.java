/*
 * Шим nfcompat. Окружение загрузки модов.
 *
 * Отличие от NeoForge: у нас нет отдельного "FML" этапа, всё окружение даёт FabricLoader.
 * ВАЖНО: FabricLoader доступен только внутри настоящего запуска игры. В обычной JVM
 * (юнит-тесты AE2, которые гоняет Gradle) его нет, поэтому dist/isProduction обязаны
 * переживать отсутствие лоадера, иначе статический инициализатор FMLEnvironment роняет
 * appeng.util.Platform (а через него - AEConfig и весь AppEngBase).
 */
package net.neoforged.fml.loading;

import net.fabricmc.loader.api.FabricLoader;
import net.neoforged.api.distmarker.Dist;

public final class FMLEnvironment {
    public static final Dist dist = detectDist();

    private FMLEnvironment() {
    }

    private static Dist detectDist() {
        try {
            return FabricLoader.getInstance()
                    .getEnvironmentType() == net.fabricmc.api.EnvType.CLIENT
                            ? Dist.CLIENT
                            : Dist.DEDICATED_SERVER;
        } catch (Throwable ignored) {
            // Без FabricLoader (юнит-тесты) считаем себя выделенным сервером: клиентских
            // рендер-путей в тестах нет.
            return Dist.DEDICATED_SERVER;
        }
    }

    public static boolean isProduction() {
        try {
            return !FabricLoader.getInstance().isDevelopmentEnvironment();
        } catch (Throwable ignored) {
            // Без лоадера - как в dev-окружении.
            return false;
        }
    }
}

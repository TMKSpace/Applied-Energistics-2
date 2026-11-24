/*
 * Часть nfcompat. Замена тех мест NeoForge `FMLLoader`, которые нужны AE2
 * (`appeng.util.Platform`), но класс которого мы намеренно НЕ отдаём в рантайме.
 *
 * ПОЧЕМУ НЕЛЬЗЯ ОТДАВАТЬ `net.neoforged.fml.loading.FMLLoader`.
 * Чужие Fabric-моды определяют "мы на NeoForge?" через
 * `Class.forName("net.neoforged.fml.loading.FMLLoader")` (так делает, например,
 * библиотека yumi из Trinkets Updated). Если шим содержит этот класс, проверка
 * ошибочно срабатывает, мод включает свои неофорджевские миксины/ветки и падает
 * на наших же фейковых `net.neoforged.*` (yumi: `RegisterEvent` ctor,
 * `DeferredRegister` internals). Поэтому класс из сборки удалён, а два нужных
 * AE2 факта берутся прямо из FabricLoader.
 */
package nfcompat;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

public final class PlatformCompat {

    private PlatformCompat() {
    }

    /**
     * @return true, если доступны клиентские классы (рендереры). Вне запуска Fabric
     *         (юнит-тесты, где FabricLoader не инициализирован) - true, как в NeoForge
     *         при `loader == null`.
     */
    public static boolean hasClientClasses() {
        try {
            return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
        } catch (Throwable ignored) {
            return true;
        }
    }

    /**
     * Вне запуска Fabric считаем, что это dev-среда (в NeoForge так же: `loader == null`).
     */
    public static boolean isDevelopmentEnvironment() {
        try {
            return FabricLoader.getInstance().isDevelopmentEnvironment();
        } catch (Throwable ignored) {
            return true;
        }
    }
}

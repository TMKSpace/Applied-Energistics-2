/*
 * Файл nfcompat. Мост к ForgeConfigAPIPort (FCP).
 *
 * Зачем: на Fabric именно FCP приносит NeoForge-конфиги вместе с красивым
 * экраном (`net.neoforged.neoforge.client.gui.ConfigurationScreen`) и ModMenu-
 * интеграцией. Наш шим тоже умеет читать `ModConfigSpec`, но экран у нас -
 * заглушка. Поэтому, когда FCP установлен, мы регистрируем спеки AE2 ещё и в
 * его `ConfigTracker`: тогда его экран видит наши настройки, а файлы читает/
 * пишет он же.
 *
 * Почему рефлексия, а не прямая ссылка: FCP не является зависимостью сборки
 * (иначе мод без него не запустился бы). Имена классов совпадают с нашими
 * (net.neoforged.*), поэтому в рантайме `Class.forName` находит наши
 * ModConfig.Type/IConfigSpec, и их `registerConfig` принимает наши объекты.
 *
 * Если FCP нет или его API не совпал - молча остаёмся на своей загрузке:
 * наша загрузка конфигов работает независимо от него.
 */
package nfcompat.compat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.fabricmc.loader.api.FabricLoader;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;

public final class ForgeConfigApiPortBridge {
    private static final Logger LOGGER = LogManager.getLogger("nfcompat/fcp");
    private static final boolean PRESENT = FabricLoader.getInstance().isModLoaded("forgeconfigapiport");

    /** Ставим, если регистрация не удалась: второй раз не пробуем и не спамим в лог. */
    private static boolean broken;

    private ForgeConfigApiPortBridge() {
    }

    public static boolean isPresent() {
        return PRESENT && !broken;
    }

    /**
     * Регистрирует спеку в `ConfigTracker` из FCP (для их экрана и их загрузки).
     * Свою загрузку (`ModConfig#load`) это не отменяет: значения общие, потому
     * что спека - один и тот же объект.
     */
    public static void register(ModConfig.Type type, IConfigSpec spec, String modId, String fileName) {
        try {
            var trackerClass = Class.forName("net.neoforged.fml.config.ConfigTracker");
            var tracker = trackerClass.getField("INSTANCE").get(null);
            trackerClass
                    .getMethod("registerConfig", ModConfig.Type.class, IConfigSpec.class, String.class, String.class)
                    .invoke(tracker, type, spec, modId, fileName);
        } catch (ReflectiveOperationException | LinkageError e) {
            broken = true;
            LOGGER.warn("nfcompat: ForgeConfigAPIPort не принял конфиг {} ({}), остаёмся на своей загрузке",
                    fileName, e.toString());
        }
    }
}

/*
 * Файл nfcompat. Экран конфига AE2 в Mod Menu.
 *
 * Когда установлен ForgeConfigAPIPort, берём ЕГО ConfigurationScreen: это
 * полноценный NeoForge-экран (секции, range, тултипы, перезапуск, списки),
 * а наша шим-заглушка была пустой рамкой.
 *
 * Класс их экрана не является зависимостью сборки, поэтому создаём его
 * рефлексией. Конструктор: (String modId, Screen parent).
 */
package nfcompat.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import net.minecraft.client.gui.screens.Screen;

public final class FcpConfigScreen {
    private static final Logger LOGGER = LogManager.getLogger("nfcompat/fcp");

    private FcpConfigScreen() {
    }

    @Nullable
    public static Screen create(@Nullable Screen parent) {
        try {
            var screenClass = Class.forName("net.neoforged.neoforge.client.gui.ConfigurationScreen");
            return (Screen) screenClass.getConstructor(String.class, Screen.class)
                    .newInstance("ae2", parent);
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.warn("nfcompat: не удалось открыть экран конфига ForgeConfigAPIPort ({})", e.toString());
            return null;
        }
    }
}

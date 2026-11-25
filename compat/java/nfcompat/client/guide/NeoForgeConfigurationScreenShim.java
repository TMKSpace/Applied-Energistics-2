/*
 * Файл nfcompat. Заглушка NeoForge-экрана конфига для GuideME.
 *
 * Зачем этот класс существует и почему у него такое длинное имя:
 * GuideME (шейдится в наш джар) регистрирует экран конфига ссылкой на конструктор
 * NeoForge:
 *
 *     container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new)
 *
 * то есть метод-хендл `new net/neoforged/neoforge/client/gui/ConfigurationScreen(ModContainer, Screen)`.
 * С установленным ForgeConfigAPIPort этот класс наш (у их версии конструктор
 * `(String, Screen)`), поэтому линковка падает с NoSuchMethodError, и GuideME
 * не доделывает инициализацию (теряются onReceiveRecipes / onPlayerConnect /
 * onPlayerDisconnect).
 *
 * Пока в шиме живёт ForgeConfigAPIPort-версия экрана, отдать GuideME настоящий
 * NeoForge-конструктор нельзя. Поэтому имя класса в его пуле констант подменяется
 * при сборке на этот шим (build.gradle, patchGuideMeScreen): строка той же длины,
 * значит замена байт в байт. Полное имя выше - 53 символа, ровно как у
 * net/neoforged/neoforge/client/gui/ConfigurationScreen.
 *
 * Экран пустой: GuideME - библиотека, его настройки в Mod Menu открывают редко.
 */
package nfcompat.client.guide;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModContainer;
import org.jetbrains.annotations.Nullable;

public final class NeoForgeConfigurationScreenShim extends Screen {
    public NeoForgeConfigurationScreenShim(ModContainer container, @Nullable Screen parent) {
        super(Component.literal("GuideME (config/guideme-client.toml)"));
    }
}

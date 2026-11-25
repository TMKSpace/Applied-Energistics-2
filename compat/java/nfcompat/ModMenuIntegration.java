/*
 * Часть nfcompat. Точка входа Mod Menu: кнопка «Настройки» у AE2.
 *
 * Mod Menu ищет entrypoint `modmenu` и ожидает класс, реализующий `ModMenuApi`. Здесь мы
 * отдаём экран из `ClothConfigScreen` (он строится по нашей `ModConfigSpec`).
 *
 * Библиотеки не поставляем: Cloth Config и Mod Menu — `compileOnly` (см. build.gradle),
 * в рантайме их приносит модпак. Если Cloth нет, а Mod Menu есть, возвращаем фабрику,
 * отдающую `null` — Mod Menu в этом случае просто не покажет кнопку конфига, и это лучше,
 * чем NoClassDefFoundError на его же кнопке.
 */
package nfcompat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import nfcompat.client.FcpConfigScreen;
import nfcompat.compat.ForgeConfigApiPortBridge;
import nfcompat.config.ClothConfigScreen;

public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // Экран ForgeConfigAPIPort - настоящий NeoForge-ConfigurationScreen
        // (секции, range, тултипы), если он установлен - он приоритетнее.
        if (ForgeConfigApiPortBridge.isPresent()) {
            return FcpConfigScreen::create;
        }
        if (!FabricLoader.getInstance().isModLoaded("cloth-config")) {
            return parent -> null;
        }
        return ClothConfigScreen::create;
    }
}

/*
 * Файл nfcompat. Крюк входа Mod Menu: кнопка <Конфигурация> у AE2.
 *
 * Mod Menu зовёт entrypoint `modmenu` у каждого мода, реализующего `ModMenuApi`.
 * Получившийся экран - это `ConfigurationScreen` из ForgeConfigAPIPort (полноценный NeoForge-
 * экран: секции, range, тултипы, перезапуск). Наши спеки регистрируются в их
 * `ConfigTracker` (см. nfcompat.compat.ForgeConfigApiPortBridge), поэтому экран
 * видит реальные настройки AE2.
 *
 * Без ForgeConfigAPIPort экрана нет вообще: Cloth Config мы не поддерживаем,
 * а своя шим-заглушка удалена. Тогда возвращаем фабрику, отдающую null, - Mod Menu
 * просто не покажет кнопку конфига и не упадёт (ни при старте, ни при открытии).
 */
package nfcompat;

import net.minecraft.client.gui.screens.Screen;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import nfcompat.client.FcpConfigScreen;
import nfcompat.compat.ForgeConfigApiPortBridge;

public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        if (ForgeConfigApiPortBridge.isPresent()) {
            return FcpConfigScreen::create;
        }
        return parent -> null;
    }
}

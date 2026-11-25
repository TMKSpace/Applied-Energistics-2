/*
 * Файл nfcompat. Крюк входа Mod Menu: кнопка <Конфигурация> у AE2.
 *
 * Mod Menu зовёт entrypoint `modmenu` у каждого мода, реализующего `ModMenuApi`.
 * Получившийся экран - это `ConfigurationScreen` из ForgeConfigAPIPort (полноценный NeoForge-
 * экран: секции, range, тултипы, перезапуск). Наши спеки регистрируются в их
 * `ConfigTracker` (см. ModContainer.registerConfig), поэтому экран видит реальные
 * настройки AE2.
 *
 * ForgeConfigAPIPort теперь жёсткая зависимость (см. depends в fabric.mod.json),
 * поэтому экран создаём напрямую, без проверок и рефлексии.
 */
package nfcompat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import net.neoforged.neoforge.client.gui.ConfigurationScreen;

public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new ConfigurationScreen("ae2", parent);
    }
}

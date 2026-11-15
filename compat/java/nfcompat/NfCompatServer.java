/*
 * Часть nfcompat. Точка входа сервера.
 *
 * Отдельный класс от клиентского, чтобы на выделенном сервере не грузились
 * клиентские классы AE2 (AppEngClient тянет рендер и GUI).
 */
package nfcompat;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.neoforged.neoforge.common.NeoForge;

import appeng.core.AppEngServer;

public final class NfCompatServer implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        // Тики, жизненный цикл сервера, команды и ThreadGroup серверного потока —
        // всё это на NeoForge рассылает загрузчик, у нас — LifecycleBridge.
        LifecycleBridge.install();

        // Конструктор мода: он вешает слушателей и делает первичную регистрацию.
        new AppEngServer(NeoForge.EVENT_BUS, NfCompat.container());
        // То, что на NeoForge делает загрузчик.
        NfCompat.runRegistrationPhases();
    }
}

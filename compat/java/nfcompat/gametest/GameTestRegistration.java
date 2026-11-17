/*
 * Часть nfcompat. Реестровый геймтест NeoForge -> ванильный 26.3.
 *
 * В 26.3 геймтесты не аннотационные, а реестровые: `Registries.TEST_ENVIRONMENT` и
 * `Registries.TEST_INSTANCE` — обычные datapack-реестры, которые грузит
 * `RegistryDataLoader.load(...)`, а `GameTestServer` потом выбирает тесты из
 * `TEST_INSTANCE` (`GameTestServer#getTestsForSelection`).
 *
 * NeoForge в этом месте патчит `RegistryDataLoader`: если включён
 * `GameTestHooks.isGametestEnabled()`, он постит `RegisterGameTestsEvent` с ПИСЬМЕННЫМИ
 * (ещё не замороженными) реестрами. AE2 слушает это событие в
 * `AppEngBase#registerTests` и складывает туда свои тестплоты через
 * `GameTestPlotAdapter.registerAll`.
 *
 * На Fabric ванильный путь остаётся тем же (см. отчёт спайка):
 * `fabric-gametest-api-v1` НЕ подменяет реестровый путь — он только стартует тот же
 * `GameTestServer` из `FabricGameTestRunner` и ДОБАВЛЯЕТ в `TEST_INSTANCE` свои
 * аннотационные тесты (`TestAnnotationLocator`). Значит нам не хватает ровно одного:
 * поста события до заморозки реестров. Это делает миксин
 * `nfcompat.gametest.mixin.RegistryDataLoaderMixin` и вызывает этот класс.
 */
package nfcompat.gametest;

import java.util.List;

import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.RegistryLoadTask;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;

import nfcompat.gametest.mixin.RegistryLoadTaskAccessor;

public final class GameTestRegistration {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("nfcompat/gametest");

    private GameTestRegistration() {
    }

    /**
     * Аналог неофорджевской патч-точки в `RegistryDataLoader`. Зовётся из миксина
     * в самом начале `lambda$load$2` — то есть после того, как все элементы реестров
     * прочитаны с диска, но ДО `WritableRegistry#freeze`.
     *
     * @param loadTasks задачи загрузки всех реестров текущего прогона
     */
    public static void registerIfEnabled(List<RegistryLoadTask<?>> loadTasks) {
        if (!GameTestHooks.isGametestEnabled()) {
            LOGGER.info("[gametest] registry path hook reached, but disabled "
                    + "(neoforge.gametest.enabled={})",
                    System.getProperty("neoforge.gametest.enabled"));
            return;
        }

        WritableRegistry<?> environments = null;
        WritableRegistry<?> tests = null;

        for (var task : loadTasks) {
            var registry = ((RegistryLoadTaskAccessor) task).nfcompat$registry();
            if (Registries.TEST_ENVIRONMENT.equals(registry.key())) {
                environments = registry;
            } else if (Registries.TEST_INSTANCE.equals(registry.key())) {
                tests = registry;
            }
        }

        if (environments == null || tests == null) {
            // Один из реестров в этом прогоне не грузился (например, клиентский
            // network-путь) — постить нечего и незачем.
            LOGGER.info("[gametest] TEST_* registries missing in this load: environment={}, tests={}",
                    environments != null, tests != null);
            return;
        }

        @SuppressWarnings("unchecked")
        var envRegistry = (WritableRegistry<TestEnvironmentDefinition<?>>) environments;
        @SuppressWarnings("unchecked")
        var testRegistry = (WritableRegistry<GameTestInstance>) tests;

        // Мод-шина шима — та же, на которую AppEngBase вешает addListener(this::registerTests).
        NeoForge.EVENT_BUS.post(new RegisterGameTestsEvent(envRegistry, testRegistry));
        LOGGER.info("[gametest] RegisterGameTestsEvent posted, appeng.tests={}, tests in registry now: {}",
                System.getProperty("appeng.tests"), testRegistry.size());
        LOGGER.info("[gametest] TEST_INSTANCE ids: {}", testRegistry.keySet());
    }
}

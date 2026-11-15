/*
 * Часть nfcompat. Клиентские reload-слушатели NeoForge -> Fabric ResourceManagerHelper.
 *
 * На NeoForge `AddClientReloadListenersEvent` получает настоящий ReloadableResourceManager
 * и складывает слушателей прямо в него. У нас такого объекта нет, поэтому мы:
 *  1) даём событию собрать слушателей в свою карту;
 *  2) регистрируем каждый в Fabric ResourceManagerHelper с тем же Identifier;
 *  3) переносим порядок из графа зависимостей NeoForge (только для наших же слушателей —
 *     ванильные Fabric всё равно сортирует сам).
 *
 * Без этого AE2 не получает ResourceManager в StyleManager, и открытие ЛЮБОГО экрана
 * AE2 падает с "ResourceManager was not set" — клиент выкидывает с Network Protocol Error.
 */
package nfcompat.client;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class ReloadListeners {

    private ReloadListeners() {
    }

    public static void registerAll() {
        var event = new AddClientReloadListenersEvent();
        NeoForge.EVENT_BUS.post(event);

        var registry = event.getRegistry();
        if (registry.isEmpty()) {
            System.out.println("[nfcompat] клиентских reload-слушателей не зарегистрировано");
            return;
        }

        var ids = new IdentityHashMap<PreparableReloadListener, Identifier>();
        registry.forEach((id, listener) -> ids.put(listener, id));

        var graph = event.getGraph();
        var helper = ResourceManagerHelper.get(PackType.CLIENT_RESOURCES);

        for (var entry : registry.entrySet()) {
            var id = entry.getKey();
            var listener = entry.getValue();

            List<Identifier> dependencies = new ArrayList<>();
            for (var predecessor : graph.predecessors(listener)) {
                var dependencyId = ids.get(predecessor);
                // Ванильные слушатели в этой карте отсутствуют: Fabric сортирует их сам,
                // а неизвестный id в зависимостях был бы ошибкой.
                if (dependencyId != null) {
                    dependencies.add(dependencyId);
                }
            }

            helper.registerReloadListener(new ShimReloadListener(id, dependencies, listener));
        }

        System.out.println("[nfcompat] клиентские reload-слушатели: " + registry.keySet());
    }
}

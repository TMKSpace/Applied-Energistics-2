/*
 * Часть nfcompat. Запуск событий загрузчика.
 *
 * postEvent — просто пост на общую шину.
 * dispatchParallelEvent в NeoForge раздаёт событие каждому моду на своём потоке;
 * у нас моды не перечисляются, поэтому событие создаётся и постится один раз.
 */
package net.neoforged.fml;

import java.util.function.BiFunction;
import java.util.function.Consumer;

import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.lifecycle.ParallelDispatchEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class ModLoader {
    private ModLoader() {
    }

    public static void postEvent(Event event) {
        NeoForge.EVENT_BUS.post(event);
    }

    /**
     * Упрощённый аналог NeoForge: одно событие на «все моды» (фактически на наш мод).
     */
    public static <T extends ParallelDispatchEvent> void dispatchParallelEvent(
            String description,
            java.util.concurrent.Executor syncExecutor,
            java.util.concurrent.Executor parallelExecutor,
            Runnable initialWork,
            BiFunction<ModContainer, DeferredWorkQueue, T> eventCreator) {
        initialWork.run();
        var queue = new DeferredWorkQueue();
        var container = new ModContainer(new net.neoforged.fml.loading.moddiscovery.ModInfo(
                "appliedenergistics2", "Applied Energistics 2", "0"), NeoForge.EVENT_BUS);
        var event = eventCreator.apply(container, queue);
        NeoForge.EVENT_BUS.post(event);
        for (Runnable r : queue.drain()) {
            r.run();
        }
    }

    /** Перегрузка с обработчиком события, как в NeoForge. */
    public static <T extends ParallelDispatchEvent> void dispatchParallelEvent(
            String description,
            java.util.concurrent.Executor syncExecutor,
            java.util.concurrent.Executor parallelExecutor,
            Runnable initialWork,
            BiFunction<ModContainer, DeferredWorkQueue, T> eventCreator,
            Consumer<T> eventHandler) {
        initialWork.run();
        var queue = new DeferredWorkQueue();
        var container = new ModContainer(new net.neoforged.fml.loading.moddiscovery.ModInfo(
                "appliedenergistics2", "Applied Energistics 2", "0"), NeoForge.EVENT_BUS);
        var event = eventCreator.apply(container, queue);
        NeoForge.EVENT_BUS.post(event);
        eventHandler.accept(event);
        for (Runnable r : queue.drain()) {
            r.run();
        }
    }
}

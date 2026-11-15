/*
 * Часть nfcompat. Базовое событие этапа загрузки, которое NeoForge раздаёт по модам.
 */
package net.neoforged.fml.event.lifecycle;

import net.neoforged.bus.api.Event;
import net.neoforged.fml.DeferredWorkQueue;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.IModBusEvent;

public class ParallelDispatchEvent extends Event implements IModBusEvent {
    private final ModContainer modContainer;
    private final DeferredWorkQueue deferredWorkQueue;

    public ParallelDispatchEvent(ModContainer modContainer, DeferredWorkQueue deferredWorkQueue) {
        this.modContainer = modContainer;
        this.deferredWorkQueue = deferredWorkQueue;
    }

    public ModContainer getModContainer() {
        return modContainer;
    }

    public DeferredWorkQueue getDeferredWorkQueue() {
        return deferredWorkQueue;
    }

    /**
     * NeoForge возвращает CompletableFuture, и AE2 этим пользуется
     * (`enqueueWork(...).whenComplete(...)`). Работы у нас выполняются сразу,
     * поэтому и future уже завершён.
     */
    public java.util.concurrent.CompletableFuture<Void> enqueueWork(Runnable runnable) {
        deferredWorkQueue.enqueueWork(runnable);
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }
}

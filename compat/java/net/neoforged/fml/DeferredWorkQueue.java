/*
 * Часть nfcompat. Очередь отложенной работы мода.
 *
 * В NeoForge через неё мод откладывает работу со своих потоков в главный поток.
 * Здесь просто запоминаем задачи; вычитывает их тот, кто создал очередь.
 */
package net.neoforged.fml;

import java.util.ArrayList;
import java.util.List;

public class DeferredWorkQueue {
    private final List<Runnable> work = new ArrayList<>();

    public void enqueueWork(Runnable runnable) {
        work.add(runnable);
    }

    public List<Runnable> drain() {
        var copy = List.copyOf(work);
        work.clear();
        return copy;
    }
}

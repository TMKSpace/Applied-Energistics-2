/*
 * Часть nfcompat. Исполнители для параллельной загрузки.
 *
 * У NeoForge синхронный исполнитель — главный поток загрузки, параллельный — пул.
 * На Fabric аналогичного этапа нет, поэтому оба — обычные общие пулы.
 */
package net.neoforged.fml;

import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

public final class ModWorkManager {
    private static final Executor SYNC = Runnable::run;
    private static final Executor PARALLEL = ForkJoinPool.commonPool();

    private ModWorkManager() {
    }

    public static Executor syncExecutor() {
        return SYNC;
    }

    public static Executor parallelExecutor() {
        return PARALLEL;
    }
}

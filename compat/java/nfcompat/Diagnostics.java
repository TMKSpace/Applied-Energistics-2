/*
 * Часть nfcompat. Диагностика рендера через block model data.
 *
 * Зачем осталось: `RenderSectionRegion`/`RenderChunkRegion` при сборке меша отдают
 * не всякий block entity, поэтому model data иногда приходит пустой. Если блок AE2
 * однажды отрисуется пустым, эти две строки сразу скажут, виноват ли поиск данных
 * модели, а не сама модель.
 *
 * Каждое сообщение пишется ограниченное число раз, чтобы не залить лог.
 */
package nfcompat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class Diagnostics {
    private static final Map<String, AtomicInteger> COUNTS = new ConcurrentHashMap<>();

    private Diagnostics() {
    }

    /** `ModelDataHooks`: model data пуста и в клиентском мире. */
    public static void modelDataMissing(Object pos) {
        count("model_data_missing", 6, "[nfcompat/diag] ModelData: пусто в клиентском мире, pos " + pos);
    }

    /** `ClientModelDataLookup`: что отдаёт getBlockEntity для model data. */
    public static void modelDataOfEntity(Object entity) {
        count("model_data_entity", 6, "[nfcompat/diag] ModelData: block entity = "
                + (entity == null ? "null" : entity.getClass().getName()));
    }

    private static void count(String key, int limit, String message) {
        var counter = COUNTS.computeIfAbsent(key, k -> new AtomicInteger());
        if (counter.incrementAndGet() <= limit) {
            System.out.println(message);
        }
    }
}

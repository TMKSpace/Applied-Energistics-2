/*
 * Часть nfcompat. Диагностика рендера через block model data.
 *
 * Кабель рисует НЕ блок-сущность: `CableBusRenderer` рисует только установленные
 * части, а сам кабель приходит из блок-модели (`CableBusModel.collectParts`), которая
 * берёт `CableBusRenderState` из model data. Пустое model data = пустой кабель при
 * живом хитбоксе, поэтому нужен ответ на вопрос «где рвётся» без отладчика.
 *
 * Каждое сообщение пишется ограниченное число раз, чтобы не залить лог.
 * Когда кабель заработает, отсюда можно всё выкинуть вместе с вызовами.
 */
package nfcompat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class Diagnostics {
    private static final Map<String, AtomicInteger> COUNTS = new ConcurrentHashMap<>();

    private Diagnostics() {
    }

    /** `CableBusModel.collectParts`: model data пришла пустой (renderState == null). */
    public static void cableBusNoRenderState(Object pos) {
        count("cable_bus_no_render_state", 6,
                "[nfcompat/diag] CableBusModel: renderState == null в " + pos);
    }

    /** `CableBusRenderer.submit`: рендерер вызван, но состояний частей нет. */
    public static void cableBusSubmitEmpty() {
        count("cable_bus_submit_empty", 6,
                "[nfcompat/diag] CableBusRenderer: submit без состояний частей");
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

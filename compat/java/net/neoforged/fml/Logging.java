/*
 * Часть nfcompat. Маркеры логов загрузчика.
 *
 * В NeoForge это класс FML с маркерами для разных подсистем. Нам нужен один — CORE,
 * его использует вендоренный ModConfigSpec.
 */
package net.neoforged.fml;

import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

public final class Logging {
    public static final Marker CORE = MarkerManager.getMarker("CORE");
    public static final Marker LOADING = MarkerManager.getMarker("LOADING");

    private Logging() {
    }
}

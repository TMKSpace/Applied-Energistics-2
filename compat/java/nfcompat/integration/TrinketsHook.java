/*
 * Часть nfcompat. Необязательная интеграция с Trinkets (Fabric-аналог Curios).
 *
 * Точка входа Fabric: если мода нет — сразу выходим, и класс TrinketsCuriosAccess
 * (а с ним и `eu.pb4.trinkets.*`) не загружается вообще. Ссылка на Trinkets-классы
 * в байткоде есть, но JVM резолвит её лениво, при выполнении инструкции, до которой
 * дело не доходит.
 *
 * try/catch(Throwable) — страховка на случай несовместимой версии Trinkets: лучше
 * потерять слоты аксессуаров, чем не дать игре загрузиться.
 */
package nfcompat.integration;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import appeng.integration.modules.curios.TrinketsCuriosAccess;

public final class TrinketsHook {

    /** `trinkets_updated` — id форка «Trinkets Updated», который живёт на 26.3. */
    private static final String TRINKETS_MOD_ID = "trinkets_updated";

    private static final Logger LOGGER = LoggerFactory.getLogger("nfcompat/trinkets");

    private TrinketsHook() {
    }

    public static void onInitialize() {
        if (!FabricLoader.getInstance().isModLoaded(TRINKETS_MOD_ID)) {
            return;
        }

        try {
            TrinketsCuriosAccess.install();
            LOGGER.info("Curios-совместимость подключена через Trinkets Updated");
        } catch (Throwable e) {
            LOGGER.warn("Trinkets найден, но мост Curios->Trinkets не поднялся; слоты аксессуаров не работают", e);
        }
    }
}

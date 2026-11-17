/*
 * Часть nfcompat. Именованные фазы рендера NeoForge (см. `RenderPhaseKey`).
 *
 * GuideME берёт отсюда `SOLID` и `TRANSLUCENT_BLOCKS_AND_ITEMS` для `submitSpecial`.
 * У нас эти константы ничего не делают: `submitSpecial` — no-op, поэтому 3D-сцены в гайде
 * пока не рисуются (TODO, P2). Классы обязаны существовать хотя бы для линковки.
 */
package net.neoforged.neoforge.client.submit;

public final class RenderPhaseKeys {

    public static final RenderPhaseKey SOLID = new RenderPhaseKey("solid");
    public static final RenderPhaseKey TRANSLUCENT_BLOCKS_AND_ITEMS =
            new RenderPhaseKey("translucent_blocks_and_items");

    private RenderPhaseKeys() {
    }
}

/*
 * Часть nfcompat. Заглушки неофорджевских «ключей фазы рендера».
 *
 * В NeoForge `RenderPhaseKey` — маркер, которым `SubmitNodeCollector.submitSpecial` раскладывает
 * геометрию по фазам (`SOLID`, `TRANSLUCENT_BLOCKS_AND_ITEMS`, ...). В ванили 26.3 фаз нет:
 * геометрия отдаётся через `RenderType`/`submitCustomGeometry`.
 *
 * GuideME зовёт `submitSpecial(RenderPhaseKeys.SOLID, node)` при сборке мешей 3D-сцены гайда.
 * Классов в шиме не было (NoClassDefFoundError на первой же 3D-сцене), поэтому держим пустые
 * маркеры, а сам `submitSpecial` добавлен к `SubmitNodeCollector` как no-op
 * (`nfcompat.mixin.client.SubmitNodeCollectorMixin`). Итог: сцены не падают, но и не рисуются —
 * это записано в TODO как P2 (нужен настоящий маппинг фаз на `RenderType`).
 */
package net.neoforged.neoforge.client.submit;

/** Маркер фазы рендера. Содержимое не нужно: у нас `submitSpecial` — no-op. */
public final class RenderPhaseKey {
    private final String name;

    public RenderPhaseKey(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "RenderPhaseKey[" + name + "]";
    }
}

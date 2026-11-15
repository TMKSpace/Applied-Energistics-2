/*
 * Часть nfcompat. Менеджер принудительно загруженных чанков.
 *
 * В NeoForge он ведёт учёт форс-лоадов по владельцу (мод/блок-сущность) и умеет
 * восстанавливать их после перезагрузки. AE2 ссылается на него только в javadoc
 * (`SpatialAnchorBlockEntity`), а фактическую работу делает через наш `TicketController`,
 * поэтому здесь — только объявление, чтобы javadoc-ссылка разрешалась.
 */
package net.neoforged.neoforge.common.world.chunk;

public final class ForcedChunkManager {
    private ForcedChunkManager() {
    }
}

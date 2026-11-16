/*
 * Часть шима NeoForge для Fabric.
 *
 * Публичный вход в TicketOwnershipData для кода AE2 (Fabric-ветки). Сам SavedData — пакетно
 * приватный, как и вся вендоренная бухгалтерия тикетов; наружу торчит только этот фасад,
 * чтобы AE2 мог спросить "этот чанк держим мы?" не видя деталей хранения.
 */

package net.neoforged.neoforge.common.world.chunk;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

public final class TicketOwnershipQueries {

    private TicketOwnershipQueries() {
    }

    /**
     * @return {@code true}, если чанк форсит именно AE2, то есть на нём есть хотя бы один наш тикет
     *         (обычный или "с натуральным спавном").
     *
     * <p>Чужой форс-лоад (другой мод, {@code /forceload}) в {@link TicketOwnershipData} не пишется,
     * поэтому чужие чанки наш ответ не задевает: мы отвечаем по своей бухгалтерии, а не по
     * ванильному {@code ServerLevel#setChunkForced}.
     */
    public static boolean isForcedByUs(Level level, long packedChunk) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        return TicketOwnershipData.get(serverLevel).isForcedByUs(serverLevel, packedChunk);
    }

    /**
     * То же самое, но по координатам чанка.
     */
    public static boolean isForcedByUs(Level level, int chunkX, int chunkZ) {
        return isForcedByUs(level, ChunkPos.pack(chunkX, chunkZ));
    }
}

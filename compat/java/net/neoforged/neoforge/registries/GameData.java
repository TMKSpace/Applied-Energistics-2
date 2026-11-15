/*
 * Часть nfcompat. Шим net.neoforged.neoforge.registries.GameData для Fabric.
 *
 * В NeoForge GameData держит "ванильные" id-мапы (blockstate id <-> BlockState, id предметов
 * для сети и т.п.), потому что NeoForge пересобирает их под себя. На Fabric/MC 26.3
 * этими мапами владеет сам MC: Block.BLOCK_STATE_REGISTRY - обычный IdMap<BlockState>,
 * синхронизируемый ванильным протоколом. Отдаём его.
 *
 * Не реализовано (в AE2 не используется, но если понадобится - доделывать):
 * getItemStackIdMap(), getEntityIdMap() и прочие хелперы NeoForge.
 */
package net.neoforged.neoforge.registries;

import net.minecraft.core.IdMap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class GameData {

    /** id <-> BlockState. */
    public static IdMap<BlockState> getBlockStateIDMap() {
        return Block.BLOCK_STATE_REGISTRY;
    }
}

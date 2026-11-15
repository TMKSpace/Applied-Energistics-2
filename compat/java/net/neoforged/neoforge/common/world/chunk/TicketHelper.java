/*
 * Vendored from NeoForge (LGPL-2.1-only), adapted for the Fabric shim.
 * Upstream: net/neoforged/neoforge/common/world/chunk/TicketHelper.java
 *
 * SHIM: NeoForge stores per-owner block/entity ticket maps inside a patched TicketStorage. Vanilla MC 26.3
 * only has a flat set of forced chunks, so the shim's TicketController keeps the per-owner bookkeeping in
 * memory and this helper removes chunks through ServerLevel#setChunkForced.
 */

package net.neoforged.neoforge.common.world.chunk;

import java.util.Collections;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * Class to help mods remove no longer valid tickets before they are activated on load.
 */
public class TicketHelper {
    private final ServerLevel level;
    private final Identifier controllerId;
    private final Map<BlockPos, TicketSet> blockTickets;

    TicketHelper(ServerLevel level, Identifier controllerId, Map<BlockPos, TicketSet> blockTickets) {
        this.level = level;
        this.controllerId = controllerId;
        this.blockTickets = blockTickets;
    }

    /**
     * {@return the id of the controller this helper belongs to}
     */
    public Identifier getControllerId() {
        return controllerId;
    }

    /**
     * {@return all "BLOCK" tickets this controller had registered and which block positions are forcing which chunks}
     *
     * <p>Shim note: the returned map is an unmodifiable snapshot, so it is safe to call the remove methods while
     * iterating it.
     */
    public Map<BlockPos, TicketSet> getBlockTickets() {
        return Collections.unmodifiableMap(blockTickets);
    }

    /**
     * Removes all tickets that a given block was responsible for; both normal and ones forcing natural spawning.
     *
     * @param owner Block that was responsible.
     */
    public void removeAllTickets(BlockPos owner) {
        var existingTickets = blockTickets.remove(owner);
        if (existingTickets == null) {
            return;
        }
        for (long chunk : existingTickets.normal()) {
            unforce(chunk);
        }
        for (long chunk : existingTickets.naturalSpawning()) {
            unforce(chunk);
        }
    }

    /**
     * Removes the ticket for the given chunk that a given block was responsible for.
     */
    public void removeTicket(BlockPos owner, long chunk, boolean forceNaturalSpawning) {
        var existingTickets = blockTickets.get(owner);
        if (existingTickets == null || !existingTickets.getChunks(forceNaturalSpawning).contains(chunk)) {
            return;
        }
        existingTickets.getChunks(forceNaturalSpawning).remove(chunk);
        if (existingTickets.isEmpty()) {
            blockTickets.remove(owner);
        }
        unforce(chunk);
    }

    private void unforce(long packedChunkPos) {
        var chunkPos = ChunkPos.unpack(packedChunkPos);
        level.setChunkForced(chunkPos.x(), chunkPos.z(), false);
    }
}

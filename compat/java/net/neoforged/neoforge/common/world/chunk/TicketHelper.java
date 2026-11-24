/*
 * Vendored from NeoForge (LGPL-2.1-only), adapted for the Fabric shim.
 * Upstream: net/neoforged/neoforge/common/world/chunk/TicketHelper.java
 *
 * SHIM: NeoForge stores per-owner block/entity ticket maps inside a patched TicketStorage. The shim keeps that
 * bookkeeping in TicketOwnershipData; removals go through the owning TicketController so the persisted owner
 * counters stay in sync with what is actually force-loaded.
 */

package net.neoforged.neoforge.common.world.chunk;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;

/**
 * Class to help mods remove no longer valid tickets before they are activated on load.
 */
public class TicketHelper {
    private final ServerLevel level;
    private final Identifier controllerId;
    private final Map<BlockPos, TicketSet> blockTickets;
    private final TicketController controller;

    TicketHelper(ServerLevel level, Identifier controllerId, Map<BlockPos, TicketSet> blockTickets,
            TicketController controller) {
        this.level = level;
        this.controllerId = controllerId;
        this.blockTickets = blockTickets;
        this.controller = controller;
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
     * <p>Shim note: this is a real snapshot (both the map and the chunk sets are copied), so it is safe to call the
     * remove methods while iterating it. AE2 does exactly that in {@code ChunkLoadingService.validateTickets}:
     * it walks the map and drops tickets of block entities that no longer exist. A live view here would make
     * {@link HashMap#forEach} throw {@link java.util.ConcurrentModificationException} at the end of the walk,
     * which {@code TicketController.runCallbackValidation} would swallow as an error and skip
     * {@code replaceBlockTickets}.
     */
    public Map<BlockPos, TicketSet> getBlockTickets() {
        var copy = new HashMap<BlockPos, TicketSet>(blockTickets.size());
        blockTickets.forEach((owner, tickets) -> copy.put(owner, new TicketSet(
                new LongOpenHashSet(tickets.normal().toLongArray()),
                new LongOpenHashSet(tickets.naturalSpawning().toLongArray()))));
        return Collections.unmodifiableMap(copy);
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
            unforce(owner, chunk);
        }
        for (long chunk : existingTickets.naturalSpawning()) {
            unforce(owner, chunk);
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
        controller.dropTicket(level, owner, chunk, forceNaturalSpawning);
    }

    private void unforce(BlockPos owner, long packedChunkPos) {
        controller.dropAllTickets(level, owner, packedChunkPos);
    }
}

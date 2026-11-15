/*
 * Vendored from NeoForge (LGPL-2.1-only), adapted for the Fabric shim.
 * Upstream: net/neoforged/neoforge/common/world/chunk/TicketSet.java
 */

package net.neoforged.neoforge.common.world.chunk;

import it.unimi.dsi.fastutil.longs.LongSet;

/**
 * Represents a pair of chunk-loaded ticket sets.
 *
 * @param normal          the normally loaded tickets
 * @param naturalSpawning the tickets that also force natural spawning to occur
 */
public record TicketSet(LongSet normal, LongSet naturalSpawning) {
    /**
     * Checks if all sets of this ticket set are empty.
     */
    public boolean isEmpty() {
        return normal.isEmpty() && naturalSpawning.isEmpty();
    }

    /**
     * @param forceNaturalSpawning {@code true} to get the forced natural spawning tickets, otherwise returns the normal tickets.
     */
    public LongSet getChunks(boolean forceNaturalSpawning) {
        return forceNaturalSpawning ? naturalSpawning() : normal();
    }
}

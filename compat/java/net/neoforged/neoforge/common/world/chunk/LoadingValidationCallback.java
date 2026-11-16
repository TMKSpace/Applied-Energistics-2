/*
 * Vendored from NeoForge (LGPL-2.1-only), adapted for the Fabric shim.
 * Upstream: net/neoforged/neoforge/common/world/chunk/LoadingValidationCallback.java
 */

package net.neoforged.neoforge.common.world.chunk;

import net.minecraft.server.level.ServerLevel;

@FunctionalInterface
public interface LoadingValidationCallback {
    /**
     * Called back when tickets are about to be loaded and reinstated to allow mods to invalidate and remove specific
     * tickets that may no longer be valid.
     *
     * <p>Shim note: Fabric has no equivalent hook, so the shim calls this itself on the first tick of a level
     * (see {@link TicketController#validateTickets(ServerLevel)}). By then the forced chunks of the previous
     * session are loaded and their block entities are available.
     *
     * @param level        The level
     * @param ticketHelper Ticket helper to remove any invalid tickets.
     */
    void validateTickets(ServerLevel level, TicketHelper ticketHelper);
}

/*
 * Vendored from NeoForge (LGPL-2.1-only), adapted for the Fabric shim.
 * Upstream: net/neoforged/neoforge/common/world/chunk/TicketController.java
 *
 * SHIM: vanilla MC 26.3 has no per-ticket controllers, only ServerLevel#setChunkForced. The controller keeps
 * the per-owner chunk bookkeeping that NeoForge stores inside the patched TicketStorage. Bookkeeping lives in
 * TicketOwnershipData (a SavedData in the overworld), so owners survive a server restart, and only our own
 * forces are ever released: a chunk held by two owners stays loaded when one of them goes away.
 */

package net.neoforged.neoforge.common.world.chunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

/**
 * A class used to manage chunk loading tickets associated with a specific ID.
 *
 * <p>Controllers must be registered via {@link RegisterTicketControllersEvent}. Fabric has no event that maps to
 * NeoForge's "validate tickets after the level loaded", so the shim drives {@link #validateTickets(ServerLevel)}
 * itself from the level load event (see {@code nfcompat.LifecycleBridge}).
 */
public final class TicketController {
    private static final Logger LOGGER = LoggerFactory.getLogger("nfcompat/TicketController");

    /**
     * Levels whose tickets still need the (block-entity dependent) validation callback. Drained on the first tick
     * of the level by {@link #runDeferredValidation(ServerLevel)}.
     */
    private static final Map<ServerLevel, List<TicketController>> PENDING_VALIDATION = new HashMap<>();

    private final Identifier id;
    @Nullable
    private final LoadingValidationCallback callback;

    public TicketController(Identifier id, @Nullable LoadingValidationCallback callback) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.callback = callback;
    }

    /**
     * Creates a ticket controller without a validation callback.
     */
    public TicketController(Identifier id) {
        this(id, null);
    }

    public Identifier id() {
        return id;
    }

    @Nullable
    public LoadingValidationCallback callback() {
        return callback;
    }

    /**
     * Forces a chunk to be loaded with the "owner" of the ticket being a given block position.
     *
     * <p>The vanilla force is only touched when the chunk's owner count actually changes: the first owner forces
     * it, the last one releases it, and an owner in the middle of a shared chunk does neither.
     *
     * @param add                  {@code true} to force the chunk, {@code false} to unforce the chunk.
     * @param forceNaturalSpawning {@code true} to make the chunk allow natural mob spawns even if there is no player
     *                             nearby. Vanilla has no such flag; the shim only records it for statistics.
     * @return {@code true} if this owner's ticket set changed (first add resp. last remove for this owner).
     */
    public boolean forceChunk(ServerLevel level, BlockPos owner, int chunkX, int chunkZ, boolean add,
            boolean forceNaturalSpawning) {
        var data = TicketOwnershipData.get(level);
        long chunk = ChunkPos.pack(chunkX, chunkZ);

        if (add) {
            boolean ownerHadIt = !data.addTicket(level, owner, chunk, forceNaturalSpawning);
            boolean firstOwner = data.totalTickets(level, chunk) == 1;
            if (firstOwner && !data.isForcedByController(level, chunk)) {
                // setChunkForced only reports a change if we were the ones establishing the force; if the chunk
                // was already force-loaded by something else (e.g. /forceload) we must not claim ownership of it,
                // otherwise removing our ticket would unload a chunk we never loaded.
                if (level.setChunkForced(chunkX, chunkZ, true)) {
                    data.setForcedByController(level, chunk, true);
                }
            }
            return !ownerHadIt;
        }

        boolean ownerHadIt = data.removeTicket(level, owner, chunk, forceNaturalSpawning);
        if (ownerHadIt && data.totalTickets(level, chunk) == 0 && data.isForcedByController(level, chunk)) {
            level.setChunkForced(chunkX, chunkZ, false);
            data.setForcedByController(level, chunk, false);
        }
        return ownerHadIt;
    }

    /**
     * Drops every ticket an owner had on a chunk (both normal and natural-spawning ones). Used by
     * {@link TicketHelper} when the level validator decides that an owner no longer exists.
     */
    void dropAllTickets(ServerLevel level, BlockPos owner, long packedChunk) {
        var data = TicketOwnershipData.get(level);
        if (!data.removeAllTickets(level, owner, packedChunk)) {
            return;
        }
        if (data.totalTickets(level, packedChunk) == 0 && data.isForcedByController(level, packedChunk)) {
            var chunkPos = ChunkPos.unpack(packedChunk);
            level.setChunkForced(chunkPos.x(), chunkPos.z(), false);
            data.setForcedByController(level, packedChunk, false);
        }
    }

    /**
     * Same as {@link #dropAllTickets}, but only for one ticket type.
     */
    void dropTicket(ServerLevel level, BlockPos owner, long packedChunk, boolean forceNaturalSpawning) {
        var data = TicketOwnershipData.get(level);
        if (!data.removeTicket(level, owner, packedChunk, forceNaturalSpawning)) {
            return;
        }
        if (data.totalTickets(level, packedChunk) == 0 && data.isForcedByController(level, packedChunk)) {
            var chunkPos = ChunkPos.unpack(packedChunk);
            level.setChunkForced(chunkPos.x(), chunkPos.z(), false);
            data.setForcedByController(level, packedChunk, false);
        }
    }

    /**
     * Called right after a {@link ServerLevel} has been created: restores the persisted owners of this level and
     * schedules the (block-entity dependent) validation callback for the first tick of the level.
     *
     * <p>The restore happens immediately because {@code ServerLevel#setChunkForced(true)} loads the chunk
     * synchronously in 26.3. The callback is deferred: at level load time the level has no chunks loaded and no
     * ticking grid yet, so a validator inspecting block entities would see an empty world and drop every ticket.
     */
    public void validateTickets(ServerLevel level) {
        TicketOwnershipData.get(level).restoreForcedChunks(level);
        if (callback == null) {
            return;
        }
        var pending = PENDING_VALIDATION.computeIfAbsent(level, l -> new ArrayList<>());
        if (!pending.contains(this)) {
            pending.add(this);
        }
    }

    /**
     * Runs the deferred validation callbacks for the given level, if any were scheduled.
     */
    public static void runDeferredValidation(ServerLevel level) {
        var pending = PENDING_VALIDATION.remove(level);
        if (pending == null) {
            return;
        }
        for (var controller : pending) {
            controller.runCallbackValidation(level);
        }
    }

    /**
     * Drops anything scheduled for a level that is going away (a reload can unload levels mid-startup).
     */
    public static void clearDeferredValidation(ServerLevel level) {
        PENDING_VALIDATION.remove(level);
    }

    private void runCallbackValidation(ServerLevel level) {
        if (callback == null) {
            return;
        }
        var data = TicketOwnershipData.get(level);
        // Chunks of existing owners are loaded by now (restore forced them), so their block entities can be
        // inspected by the validator. Load the owner's own chunk explicitly as well: an anchor is only "real"
        // if its block entity can be seen, and we must not mistake "chunk not loaded" for "anchor removed".
        var snapshot = new HashMap<>(data.blockTickets(level));
        try {
            for (var owner : snapshot.keySet()) {
                var ownerChunk = ChunkPos.containing(owner);
                level.getChunk(ownerChunk.x(), ownerChunk.z());
            }
        } catch (Throwable t) {
            LOGGER.error("Ticket controller {} could not load owner chunks for {}, keeping all tickets",
                    id, level.dimension().identifier(), t);
            return;
        }
        try {
            callback.validateTickets(level, new TicketHelper(level, id, snapshot, this));
        } catch (Throwable t) {
            // Never let a broken validator kill a level load: the forced chunks stay as they are.
            LOGGER.error("Ticket controller {} failed to validate tickets for {}", id, level.dimension().identifier(), t);
            return;
        }
        data.replaceBlockTickets(level, snapshot);
    }
}

/*
 * Vendored from NeoForge (LGPL-2.1-only), adapted for the Fabric shim.
 * Upstream: net/neoforged/neoforge/common/world/chunk/TicketController.java
 *
 * SHIM: vanilla MC 26.3 has no per-ticket controllers, only ServerLevel#setChunkForced. The controller keeps
 * the per-owner chunk bookkeeping that NeoForge stores inside the patched TicketStorage, and forwards the actual
 * loading/unloading to setChunkForced. Logical chunk positions are what Minecraft persists, so behaviour is very
 * close, but there is no separation between "normal" and "natural spawning" tickets and no entity tickets.
 */

package net.neoforged.neoforge.common.world.chunk;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

/**
 * A class used to manage chunk loading tickets associated with a specific ID.
 *
 * <p>Controllers must be registered via {@link RegisterTicketControllersEvent}. Registration in the shim only
 * tracks the controller so {@link #validateTickets(ServerLevel)} can be driven manually; Fabric has no
 * equivalent event to fire it.
 */
public final class TicketController {
    private final Identifier id;
    @Nullable
    private final LoadingValidationCallback callback;

    /**
     * Bookkeeping of forced chunks per level and owner block position, kept because vanilla has no per-owner storage.
     */
    private final Map<ServerLevel, Map<BlockPos, MutableTicketSet>> blockTickets = new HashMap<>();

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
     * @param add                  {@code true} to force the chunk, {@code false} to unforce the chunk.
     * @param forceNaturalSpawning {@code true} to make the chunk allow natural mob spawns even if there is no player
     *                             nearby. Vanilla has no such flag; the shim only records it for bookkeeping.
     */
    public boolean forceChunk(ServerLevel level, BlockPos owner, int chunkX, int chunkZ, boolean add, boolean forceNaturalSpawning) {
        long chunk = ChunkPos.pack(chunkX, chunkZ);
        var perLevel = blockTickets.computeIfAbsent(level, l -> new HashMap<>());

        if (add) {
            var ticketSet = perLevel.computeIfAbsent(owner, o -> new MutableTicketSet());
            if (forceNaturalSpawning) {
                ticketSet.naturalSpawning.add(chunk);
            } else {
                ticketSet.normal.add(chunk);
            }
        } else {
            var ticketSet = perLevel.get(owner);
            if (ticketSet != null) {
                ticketSet.normal.remove(chunk);
                ticketSet.naturalSpawning.remove(chunk);
                if (ticketSet.isEmpty()) {
                    perLevel.remove(owner);
                }
            }
        }

        return level.setChunkForced(chunkX, chunkZ, add);
    }

    /**
     * Runs the validation callback for the given level, if one was supplied. This has no counterpart on Fabric and
     * is therefore never called automatically.
     */
    public void validateTickets(ServerLevel level) {
        if (callback == null) {
            return;
        }
        var perLevel = blockTickets.computeIfAbsent(level, l -> new HashMap<>());
        var snapshot = new HashMap<BlockPos, TicketSet>();
        perLevel.forEach((pos, set) -> snapshot.put(pos, set.toImmutable()));
        callback.validateTickets(level, new TicketHelper(level, id, snapshot));
        // Anything the callback removed is gone from the snapshot; drop it from our own bookkeeping too.
        perLevel.keySet().retainAll(snapshot.keySet());
        for (var entry : snapshot.entrySet()) {
            perLevel.put(entry.getKey(), MutableTicketSet.of(entry.getValue()));
        }
    }

    /**
     * Mutable variant of {@link TicketSet} used for the in-memory bookkeeping.
     */
    private static final class MutableTicketSet {
        private final LongOpenHashSet normal = new LongOpenHashSet();
        private final LongOpenHashSet naturalSpawning = new LongOpenHashSet();

        private static MutableTicketSet of(TicketSet set) {
            var result = new MutableTicketSet();
            result.normal.addAll(set.normal());
            result.naturalSpawning.addAll(set.naturalSpawning());
            return result;
        }

        private boolean isEmpty() {
            return normal.isEmpty() && naturalSpawning.isEmpty();
        }

        private TicketSet toImmutable() {
            return new TicketSet(new LongOpenHashSet(normal), new LongOpenHashSet(naturalSpawning));
        }
    }
}

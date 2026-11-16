/*
 * Часть шима NeoForge для Fabric.
 *
 * NeoForge хранит тикеты чанков (владелец -> набор чанков) внутри пропатченного
 * TicketStorage, который сам сериализуется в forcedchunks.dat. Ванильный MC 26.3
 * умеет только плоский набор форс-лоадов (ServerLevel#setChunkForced), владельцев
 * не помнит и после перезапуска сервера теряет связь "кто держит чанк".
 *
 * Этот SavedData — замена той части TicketStorage, которая отвечает за владельцев:
 *   dimension -> chunk -> owner -> счётчик тикетов (обычный/с натуральным спавном)
 *
 * Плюс отдельный набор чанков, которые форсировали именно мы: если чанк уже был
 * форс-лоаднут кем-то другим (например, /forceload), при снятии последнего нашего
 * тикета мы его не разгружаем.
 *
 * Хранится в overworld-е (как и прочие глобальные данные), поэтому после
 * перезапуска сервера владельцы восстанавливаются на загрузке каждого измерения.
 */

package net.neoforged.neoforge.common.world.chunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

final class TicketOwnershipData extends SavedData {

    /** Сколько тикетов на конкретный чанк выдал конкретный владелец. */
    private static final class Counts {
        private int normal;
        private int natural;

        private boolean isEmpty() {
            return normal <= 0 && natural <= 0;
        }

        private int total() {
            return normal + natural;
        }
    }

    private record OwnerEntry(Identifier dimension, ChunkPos chunk, BlockPos owner, int normal, int natural) {
        static final Codec<OwnerEntry> CODEC = RecordCodecBuilder.create(builder -> builder.group(
                Identifier.CODEC.fieldOf("dimension").forGetter(OwnerEntry::dimension),
                ChunkPos.CODEC.fieldOf("chunk").forGetter(OwnerEntry::chunk),
                BlockPos.CODEC.fieldOf("owner").forGetter(OwnerEntry::owner),
                Codec.INT.optionalFieldOf("normal", 0).forGetter(OwnerEntry::normal),
                Codec.INT.optionalFieldOf("natural", 0).forGetter(OwnerEntry::natural))
                .apply(builder, OwnerEntry::new));
    }

    private record ForcedEntry(Identifier dimension, ChunkPos chunk) {
        static final Codec<ForcedEntry> CODEC = RecordCodecBuilder.create(builder -> builder.group(
                Identifier.CODEC.fieldOf("dimension").forGetter(ForcedEntry::dimension),
                ChunkPos.CODEC.fieldOf("chunk").forGetter(ForcedEntry::chunk))
                .apply(builder, ForcedEntry::new));
    }

    private static final Codec<TicketOwnershipData> CODEC = RecordCodecBuilder.create(builder -> builder.group(
            OwnerEntry.CODEC.listOf().optionalFieldOf("owners", List.of())
                    .forGetter(TicketOwnershipData::ownerEntries),
            ForcedEntry.CODEC.listOf().optionalFieldOf("forced", List.of())
                    .forGetter(TicketOwnershipData::forcedEntries))
            .apply(builder, TicketOwnershipData::new));

    private static final SavedDataType<TicketOwnershipData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("neoforge", "chunk_ticket_ownership"),
            TicketOwnershipData::new,
            CODEC,
            DataFixTypes.LEVEL);

    private final Map<Identifier, Long2ObjectMap<Map<BlockPos, Counts>>> tickets = new HashMap<>();
    private final Map<Identifier, LongSet> forcedByUs = new HashMap<>();

    private TicketOwnershipData() {
    }

    private TicketOwnershipData(List<OwnerEntry> owners, List<ForcedEntry> forced) {
        for (var entry : owners) {
            if (entry.normal() <= 0 && entry.natural() <= 0) {
                continue;
            }
            var perChunk = dimension(entry.dimension(), true);
            var perOwner = perChunk.computeIfAbsent(entry.chunk().pack(), pos -> new HashMap<>());
            var counts = perOwner.computeIfAbsent(entry.owner().immutable(), pos -> new Counts());
            counts.normal += Math.max(0, entry.normal());
            counts.natural += Math.max(0, entry.natural());
        }
        for (var entry : forced) {
            forcedByUs.computeIfAbsent(entry.dimension(), dim -> new LongOpenHashSet())
                    .add(entry.chunk().pack());
        }
    }

    // ------------------------------------------------------------------
    // Сериализация
    // ------------------------------------------------------------------

    private List<OwnerEntry> ownerEntries() {
        var result = new ArrayList<OwnerEntry>();
        for (var dimEntry : tickets.entrySet()) {
            for (var chunkEntry : dimEntry.getValue().long2ObjectEntrySet()) {
                var chunkPos = ChunkPos.unpack(chunkEntry.getLongKey());
                for (var ownerEntry : chunkEntry.getValue().entrySet()) {
                    var counts = ownerEntry.getValue();
                    if (counts.isEmpty()) {
                        continue;
                    }
                    result.add(new OwnerEntry(dimEntry.getKey(), chunkPos, ownerEntry.getKey(),
                            counts.normal, counts.natural));
                }
            }
        }
        return result;
    }

    private List<ForcedEntry> forcedEntries() {
        var result = new ArrayList<ForcedEntry>();
        for (var dimEntry : forcedByUs.entrySet()) {
            for (long chunk : dimEntry.getValue()) {
                result.add(new ForcedEntry(dimEntry.getKey(), ChunkPos.unpack(chunk)));
            }
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Доступ
    // ------------------------------------------------------------------

    /**
     * Данные лежат в overworld-е. При загрузке самого overworld-а levels-карта сервера
     * уже содержит его (Fabric постит LOAD после put), поэтому для остальных измерений
     * overworld к этому моменту тоже доступен.
     */
    static TicketOwnershipData get(ServerLevel level) {
        return storageLevel(level).getDataStorage().computeIfAbsent(TYPE);
    }

    private static ServerLevel storageLevel(ServerLevel level) {
        if (level.dimension() == Level.OVERWORLD) {
            return level;
        }
        var overworld = level.getServer().getLevel(Level.OVERWORLD);
        // На совсем экзотическом порядке загрузки измерений лучше хранить рядом с уровнем,
        // чем упасть на NPE.
        return overworld != null ? overworld : level;
    }

    private Long2ObjectMap<Map<BlockPos, Counts>> dimension(ServerLevel level, boolean create) {
        return dimension(level.dimension().identifier(), create);
    }

    private @Nullable Long2ObjectMap<Map<BlockPos, Counts>> dimension(Identifier dimensionId, boolean create) {
        var existing = tickets.get(dimensionId);
        if (existing != null || !create) {
            return existing;
        }
        var created = new Long2ObjectOpenHashMap<Map<BlockPos, Counts>>();
        tickets.put(dimensionId, created);
        return created;
    }

    // ------------------------------------------------------------------
    // Тикеты
    // ------------------------------------------------------------------

    /**
     * @return {@code true}, если у этого владельца раньше не было тикета на этот чанк.
     */
    boolean addTicket(ServerLevel level, BlockPos owner, long chunk, boolean natural) {
        var perOwner = dimension(level, true).computeIfAbsent(chunk, pos -> new HashMap<>());
        var counts = perOwner.computeIfAbsent(owner.immutable(), pos -> new Counts());
        boolean wasEmpty = counts.isEmpty();
        if (natural) {
            counts.natural++;
        } else {
            counts.normal++;
        }
        setDirty();
        return wasEmpty;
    }

    /**
     * @return {@code true}, если у владельца был тикет этого типа на этот чанк.
     */
    boolean removeTicket(ServerLevel level, BlockPos owner, long chunk, boolean natural) {
        var perChunk = dimension(level, false);
        if (perChunk == null) {
            return false;
        }
        var perOwner = perChunk.get(chunk);
        if (perOwner == null) {
            return false;
        }
        var counts = perOwner.get(owner);
        if (counts == null) {
            return false;
        }
        int before = natural ? counts.natural : counts.normal;
        if (before <= 0) {
            return false;
        }
        if (natural) {
            counts.natural--;
        } else {
            counts.normal--;
        }
        if (counts.isEmpty()) {
            perOwner.remove(owner);
        }
        if (perOwner.isEmpty()) {
            perChunk.remove(chunk);
        }
        setDirty();
        return true;
    }

    /**
     * Снимает с владельца все тикеты на чанк, независимо от типа.
     */
    boolean removeAllTickets(ServerLevel level, BlockPos owner, long chunk) {
        var perChunk = dimension(level, false);
        if (perChunk == null) {
            return false;
        }
        var perOwner = perChunk.get(chunk);
        if (perOwner == null) {
            return false;
        }
        var removed = perOwner.remove(owner);
        if (removed == null) {
            return false;
        }
        if (perOwner.isEmpty()) {
            perChunk.remove(chunk);
        }
        setDirty();
        return !removed.isEmpty();
    }

    /** Сколько всего тикетов (все владельцы, оба типа) держат чанк. */
    int totalTickets(ServerLevel level, long chunk) {
        var perChunk = dimension(level, false);
        if (perChunk == null) {
            return 0;
        }
        var perOwner = perChunk.get(chunk);
        if (perOwner == null) {
            return 0;
        }
        int total = 0;
        for (var counts : perOwner.values()) {
            total += counts.total();
        }
        return total;
    }

    /** Снимок "владелец -> чанки" для {@link TicketHelper}. */
    Map<BlockPos, TicketSet> blockTickets(ServerLevel level) {
        var result = new HashMap<BlockPos, TicketSet>();
        var perChunk = dimension(level, false);
        if (perChunk == null) {
            return result;
        }
        for (var chunkEntry : perChunk.long2ObjectEntrySet()) {
            long chunk = chunkEntry.getLongKey();
            for (var ownerEntry : chunkEntry.getValue().entrySet()) {
                var counts = ownerEntry.getValue();
                if (counts.isEmpty()) {
                    continue;
                }
                var set = result.computeIfAbsent(ownerEntry.getKey(),
                        pos -> new TicketSet(new LongOpenHashSet(), new LongOpenHashSet()));
                if (counts.normal > 0) {
                    set.normal().add(chunk);
                }
                if (counts.natural > 0) {
                    set.naturalSpawning().add(chunk);
                }
            }
        }
        return result;
    }

    /** Записывает результат валидации (что осталось после callback-а) обратно. */
    void replaceBlockTickets(ServerLevel level, Map<BlockPos, TicketSet> blockTickets) {
        dimension(level, true).clear();
        for (var ownerEntry : blockTickets.entrySet()) {
            for (long chunk : ownerEntry.getValue().normal()) {
                putRestoredTicket(level, ownerEntry.getKey(), chunk, false);
            }
            for (long chunk : ownerEntry.getValue().naturalSpawning()) {
                putRestoredTicket(level, ownerEntry.getKey(), chunk, true);
            }
        }
        setDirty();
    }

    private void putRestoredTicket(ServerLevel level, BlockPos owner, long chunk, boolean natural) {
        var perOwner = dimension(level, true).computeIfAbsent(chunk, pos -> new HashMap<>());
        var counts = perOwner.computeIfAbsent(owner.immutable(), pos -> new Counts());
        if (natural) {
            counts.natural = Math.max(1, counts.natural);
        } else {
            counts.normal = Math.max(1, counts.normal);
        }
    }

    // ------------------------------------------------------------------
    // Форс-лоад
    // ------------------------------------------------------------------

    boolean isForcedByController(ServerLevel level, long chunk) {
        var forced = forcedByUs.get(level.dimension().identifier());
        return forced != null && forced.contains(chunk);
    }

    void setForcedByController(ServerLevel level, long chunk, boolean forced) {
        var dimId = level.dimension().identifier();
        if (forced) {
            forcedByUs.computeIfAbsent(dimId, dim -> new LongOpenHashSet()).add(chunk);
        } else {
            var forcedChunks = forcedByUs.get(dimId);
            if (forcedChunks != null) {
                forcedChunks.remove(chunk);
                if (forcedChunks.isEmpty()) {
                    forcedByUs.remove(dimId);
                }
            }
        }
        setDirty();
    }

    /**
     * Приводит ванильный форс-лоад в соответствие с сохранёнными владельцами:
     * снимает форс с осиротевших чанков и восстанавливает форс для всех записанных.
     *
     * <p>Вызывается на загрузке уровня. {@code ServerLevel#setChunkForced(true)} в 26.3
     * синхронно подгружает чанк, поэтому после этого метода блок-сущности якорей уже
     * доступны для валидации.
     */
    void restoreForcedChunks(ServerLevel level) {
        var dimId = level.dimension().identifier();
        var perChunk = tickets.get(dimId);
        var forced = forcedByUs.get(dimId);

        if (perChunk != null) {
            // Пустые владельцы/чанки не должны переживать загрузку.
            for (var iterator = perChunk.long2ObjectEntrySet().iterator(); iterator.hasNext();) {
                var entry = iterator.next();
                entry.getValue().entrySet().removeIf(ownerEntry -> ownerEntry.getValue().isEmpty());
                if (entry.getValue().isEmpty()) {
                    iterator.remove();
                }
            }
            if (perChunk.isEmpty()) {
                tickets.remove(dimId);
                perChunk = null;
            }
        }

        if (forced != null) {
            for (long chunk : new ArrayList<>(forced)) {
                if (perChunk == null || !perChunk.containsKey(chunk)) {
                    var chunkPos = ChunkPos.unpack(chunk);
                    level.setChunkForced(chunkPos.x(), chunkPos.z(), false);
                    forced.remove(chunk);
                }
            }
            if (forced.isEmpty()) {
                forcedByUs.remove(dimId);
                forced = null;
            }
        }

        if (perChunk == null) {
            return;
        }

        for (long chunk : new ArrayList<>(perChunk.keySet())) {
            if (forced != null && forced.contains(chunk)) {
                // Мы уже форсировали его раньше; вызов идемпотентен и (если ваниль
                // потеряла состояние) вернёт форс на место.
                var chunkPos = ChunkPos.unpack(chunk);
                level.setChunkForced(chunkPos.x(), chunkPos.z(), true);
            } else {
                var chunkPos = ChunkPos.unpack(chunk);
                if (level.setChunkForced(chunkPos.x(), chunkPos.z(), true)) {
                    setForcedByController(level, chunk, true);
                }
            }
        }
        setDirty();
    }
}

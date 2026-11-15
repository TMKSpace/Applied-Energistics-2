# -*- coding: utf-8 -*-
"""Патч-сет AE2, часть 2: SavedDataType. В ванили конструктор 4-аргументный."""
import io
import os

ROOT = r"D:\Code\java\ae2-fabric-probe"

IMPORTS_FABRIC = (
    "//#if FABRIC\n"
    "import net.minecraft.util.datafix.DataFixTypes;\n"
    "%s"
    "//#endif\n"
)

def patch(rel, pairs):
    path = os.path.join(ROOT, rel.replace("/", os.sep))
    with io.open(path, encoding="utf-8") as fh:
        text = fh.read()
    orig = text
    for old, new in pairs:
        if old not in text:
            print("!! НЕ НАЙДЕНО в %s:\n%s" % (rel, old))
            continue
        text = text.replace(old, new, 1)
    if text != orig:
        with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
            fh.write(text)
        print("patched", rel)

# ---------------------------------------------------------------- PlayerRegistryInternal
patch("src/main/java/appeng/api/features/PlayerRegistryInternal.java", [
    ("package appeng.api.features;\n",
     "package appeng.api.features;\n\n"
     + (IMPORTS_FABRIC % "import net.neoforged.neoforge.server.ServerLifecycleHooks;\n")),
    ("""    private static final SavedDataType<PlayerRegistryInternal> TYPE = new SavedDataType<>(
            ID,
            level -> new PlayerRegistryInternal(level.getServer()),
            level -> RecordCodecBuilder.create(builder -> builder.group(
                    PLAYER_REGISTRY_DATA_CODEC.fieldOf("players").forGetter(PlayerRegistryInternal::getData))
                    .apply(builder, data -> new PlayerRegistryInternal(level.getServer(), data))));""",
     """    private static final SavedDataType<PlayerRegistryInternal> TYPE = new SavedDataType<>(
//#if FABRIC
            // в ванили нет уровнезависимых фабрик — сервер берём из хука старта
            ID,
            () -> new PlayerRegistryInternal(ServerLifecycleHooks.getCurrentServer()),
            RecordCodecBuilder.create(builder -> builder.group(
                    PLAYER_REGISTRY_DATA_CODEC.fieldOf("players").forGetter(PlayerRegistryInternal::getData))
                    .apply(builder, data -> new PlayerRegistryInternal(ServerLifecycleHooks.getCurrentServer(), data))),
            DataFixTypes.LEVEL);
//#else
            ID,
            level -> new PlayerRegistryInternal(level.getServer()),
            level -> RecordCodecBuilder.create(builder -> builder.group(
                    PLAYER_REGISTRY_DATA_CODEC.fieldOf("players").forGetter(PlayerRegistryInternal::getData))
                    .apply(builder, data -> new PlayerRegistryInternal(level.getServer(), data))));
//#endif"""),
])

# ---------------------------------------------------------------- ChunkLoadState
patch("src/main/java/appeng/server/services/ChunkLoadState.java", [
    ("package appeng.server.services;\n",
     "package appeng.server.services;\n\n"
     + (IMPORTS_FABRIC % "import net.neoforged.neoforge.server.ServerLifecycleHooks;\n")),
    ("""    private static final SavedDataType<ChunkLoadState> TYPE = new SavedDataType<>(
            AppEng.makeId("chunk_load_state"),
            ChunkLoadState::new,
            level -> RecordCodecBuilder.create(builder -> builder.group(
                    ForcedChunk.CODEC.listOf().fieldOf("forcedChunks").forGetter(ChunkLoadState::getForcedChunks))
                    .apply(builder, data -> new ChunkLoadState(level, data))));""",
     """    private static final SavedDataType<ChunkLoadState> TYPE = new SavedDataType<>(
//#if FABRIC
            // ИЗВЕСТНОЕ РАСХОЖДЕНИЕ: в ванили кодек не получает уровень, поэтому берём
            // overworld. Для состояния форс-лоада чанков это неверно, если данные принадлежат
            // другому измерению. Нужен хук на DimensionDataStorage — TODO.
            AppEng.makeId("chunk_load_state"),
            ChunkLoadState::new,
            RecordCodecBuilder.create(builder -> builder.group(
                    ForcedChunk.CODEC.listOf().fieldOf("forcedChunks").forGetter(ChunkLoadState::getForcedChunks))
                    .apply(builder, data -> new ChunkLoadState(
                            ServerLifecycleHooks.getCurrentServer().overworld(), data))),
            DataFixTypes.LEVEL);
//#else
            AppEng.makeId("chunk_load_state"),
            ChunkLoadState::new,
            level -> RecordCodecBuilder.create(builder -> builder.group(
                    ForcedChunk.CODEC.listOf().fieldOf("forcedChunks").forGetter(ChunkLoadState::getForcedChunks))
                    .apply(builder, data -> new ChunkLoadState(level, data))));
//#endif"""),
])

# ---------------------------------------------------------------- CompassRegion
patch("src/main/java/appeng/server/services/compass/CompassRegion.java", [
    ("package appeng.server.services.compass;\n",
     "package appeng.server.services.compass;\n\n"
     + (IMPORTS_FABRIC % "")),
    ("""            .memoize((regionX, regionZ) -> new SavedDataType<>(
                    AppEng.makeId("compass_" + regionX + "_" + regionZ),
                    () -> new CompassRegion(regionX, regionZ),
                    RecordCodecBuilder.create(builder -> builder.group(
                            SECTION_CODEC.listOf().fieldOf("sections").forGetter(CompassRegion::sections))
                            .apply(builder, sections -> new CompassRegion(regionX, regionZ, sections)))));""",
     """            .memoize((regionX, regionZ) -> new SavedDataType<>(
//#if FABRIC
                    AppEng.makeId("compass_" + regionX + "_" + regionZ),
                    () -> new CompassRegion(regionX, regionZ),
                    RecordCodecBuilder.create(builder -> builder.group(
                            SECTION_CODEC.listOf().fieldOf("sections").forGetter(CompassRegion::sections))
                            .apply(builder, sections -> new CompassRegion(regionX, regionZ, sections))),
                    DataFixTypes.LEVEL));
//#else
                    AppEng.makeId("compass_" + regionX + "_" + regionZ),
                    () -> new CompassRegion(regionX, regionZ),
                    RecordCodecBuilder.create(builder -> builder.group(
                            SECTION_CODEC.listOf().fieldOf("sections").forGetter(CompassRegion::sections))
                            .apply(builder, sections -> new CompassRegion(regionX, regionZ, sections)))));
//#endif"""),
])

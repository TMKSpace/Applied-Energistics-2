# -*- coding: utf-8 -*-
"""Патч-сет AE2: места, где NeoForge меняет сигнатуры ванили. Оформляем маркерами //#if FABRIC."""
import io
import os

ROOT = r"D:\Code\java\ae2-fabric-probe"

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
    else:
        print("без изменений", rel)

# ---------------------------------------------------------------- 1. OitPipelineSet.builder
# В ванили первый аргумент — String, NeoForge патчит на Identifier.
def oit(indent, ident, tail):
    return (
        '%s//#if FABRIC\n'
        '%s.builder(AppEng.makeId("%s").toString()%s\n'
        '%s//#else\n'
        '%s.builder(AppEng.makeId("%s")%s\n'
        '%s//#endif\n'
    ) % (indent, indent, ident, tail, indent, indent, ident, tail, indent)

patch("src/main/java/appeng/client/render/AERenderPipelines.java", [
    ('            .builder(AppEng.makeId("area_overlay_face"),\n',
     oit('            ', 'area_overlay_face', ',')),

    ('            .builder(AppEng.makeId("area_overlay_line"), RenderPipeline.builder(RenderPipelines.OIT_LINES_SNIPPET)\n',
     oit('            ', 'area_overlay_line',
         ', RenderPipeline.builder(RenderPipelines.OIT_LINES_SNIPPET)')),

    ('            .builder(AppEng.makeId("lines_occluded"),\n',
     oit('            ', 'lines_occluded', ',')),

    ('            .builder(AppEng.makeId("lightning_fx"), RenderPipeline.builder(RenderPipelines.OIT_PARTICLE_SNIPPET)\n',
     oit('            ', 'lightning_fx',
         ', RenderPipeline.builder(RenderPipelines.OIT_PARTICLE_SNIPPET)')),
])

# ---------------------------------------------------------------- 2. EntityType.Builder
patch("src/main/java/appeng/core/definitions/AEEntities.java", [(
    'builder -> builder.setTrackingRange(16).setUpdateInterval(4).setShouldReceiveVelocityUpdates(true));',
    # в ванили методы зовутся иначе, а «получать скорость» — поведение по умолчанию
    '//#if FABRIC\n'
    '                builder -> builder.clientTrackingRange(16).updateInterval(4));\n'
    '//#else\n'
    '                builder -> builder.setTrackingRange(16).setUpdateInterval(4).setShouldReceiveVelocityUpdates(true));\n'
    '//#endif'
)])

# ---------------------------------------------------------------- 3. BlockEntityType ctor
patch("src/main/java/appeng/core/definitions/AEBlockEntities.java", [(
    """            var blocks = Arrays.stream(blockDefinitions)
                    .map(BlockDefinition::block)
                    .toArray(AEBaseEntityBlock[]::new);

            var type = new BlockEntityType<>(supplier, blocks);""",
    """//#if FABRIC
            // ванильный конструктор берёт Set<Block>, NeoForge добавляет вариант с varargs
            var blocks = Arrays.stream(blockDefinitions)
                    .<Block>map(BlockDefinition::block)
                    .collect(java.util.stream.Collectors.toSet());

            var type = new BlockEntityType<>(supplier, blocks);
//#else
            var blocks = Arrays.stream(blockDefinitions)
                    .map(BlockDefinition::block)
                    .toArray(AEBaseEntityBlock[]::new);

            var type = new BlockEntityType<>(supplier, blocks);
//#endif"""
)])

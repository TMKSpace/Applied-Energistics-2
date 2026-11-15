# -*- coding: utf-8 -*-
"""Правки AE2: AEBlockEntities (Block не импортирован) + AEKeyTypesInternal (addCallback)."""
import io

ROOT = r"D:\Code\java\ae2-fabric-probe\src\main\java"

def patch(path, old, new):
    with io.open(path, encoding="utf-8") as fh:
        text = fh.read()
    if old not in text:
        print("!! НЕ НАЙДЕНО:", path)
        return
    with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text.replace(old, new, 1))
    print("поправлен", path.split("\\src\\main\\java\\")[-1])

# 1) AEBlockEntities: в FABRIC-ветке `Block` не импортирован -> полное имя
patch(ROOT + r"\appeng\core\definitions\AEBlockEntities.java",
      """            var blocks = Arrays.stream(blockDefinitions)
                    .<Block>map(BlockDefinition::block)
                    .collect(java.util.stream.Collectors.toSet());""",
      """            var blocks = Arrays.stream(blockDefinitions)
                    .map(d -> (net.minecraft.world.level.block.Block) d.block())
                    .collect(java.util.stream.Collectors.toSet());""")

# 2) AEKeyTypesInternal: addCallback есть только у патченного NeoForge Registry
patch(ROOT + r"\appeng\api\stacks\AEKeyTypesInternal.java",
      """        registry.addCallback((BakeCallback<AEKeyType>) (ignored -> {
            var types = new HashSet<AEKeyType>();
            for (var aeKeyType : registry) {
                types.add(aeKeyType);
            }
            allTypes = Set.copyOf(types);
        }));""",
      """//#if FABRIC
        // в ванильном Registry нет addCallback — идём через хелпер шима
        nfcompat.registry.RegistryCallbacks.onBake(registry, ignored -> {
            var types = new HashSet<AEKeyType>();
            for (var aeKeyType : registry) {
                types.add(aeKeyType);
            }
            allTypes = Set.copyOf(types);
        });
//#else
        registry.addCallback((BakeCallback<AEKeyType>) (ignored -> {
            var types = new HashSet<AEKeyType>();
            for (var aeKeyType : registry) {
                types.add(aeKeyType);
            }
            allTypes = Set.copyOf(types);
        }));
//#endif""")

# -*- coding: utf-8 -*-
"""Снятие @Override (по списку из лога) + несколько однострочных сигнатур."""
import io
import os

ROOT = r"D:\Code\java\ae2-fabric-probe"
SA = os.path.join(ROOT, "src", "main", "java")
SC = os.path.join(ROOT, "src", "client", "java")

# file -> номера строк с @Override из build-probe74
OVERRIDES = {
    r"appeng\block\misc\TinyTNTBlock.java": [89],
    r"appeng\block\networking\CableBusBlock.java": [150, 172, 333],
    r"appeng\block\spatial\MatrixFrameBlock.java": [73, 88],
    r"appeng\items\tools\NetworkToolItem.java": [92],
    r"appeng\items\parts\FacadeItem.java": [57],
    r"appeng\client\model\SpatialPylonModel.java": [211],
    r"appeng\client\render\BasicUnbakedModel.java": [35],
    r"appeng\client\render\cablebus\CableBusModel.java": [170],
    r"appeng\client\render\cablebus\FacadeBuilder.java": [307],
    r"appeng\client\renderer\blockentity\SkyStoneChestRenderer.java": [104],
    r"appeng\entity\TinyTNTPrimedEntity.java": [134],
    r"appeng\items\tools\powered\powersink\AEBasePoweredItem.java": [71],
    r"appeng\items\tools\powered\AbstractPortableCell.java": [87],
    r"appeng\items\tools\MemoryCardItem.java": [329],
    r"appeng\items\materials\UpgradeCardItem.java": [62],
    r"appeng\items\storage\BasicStorageCell.java": [177],
    r"appeng\debug\EraserItem.java": [55],
    r"appeng\debug\MeteoritePlacerItem.java": [89],
    r"appeng\debug\DebugCardItem.java": [96],
    r"appeng\debug\ReplicatorCardItem.java": [87],
    r"appeng\crafting\pattern\EncodedPatternItem.java": [91],
    r"appeng\menu\implementations\MenuTypeBuilder.java": [160],
}

total = 0
for rel, linenos in OVERRIDES.items():
    path = None
    for base in (SA, SC):
        p = os.path.join(base, rel)
        if os.path.exists(p):
            path = p
            break
    if not path:
        print("!! нет файла", rel)
        continue
    with io.open(path, encoding="utf-8") as fh:
        lines = fh.read().split("\n")
    for ln in sorted(linenos, reverse=True):
        idx = ln - 1
        if idx >= len(lines) or lines[idx].strip() != "@Override":
            print("!! %s:%d не @Override (%r)" % (rel, ln, lines[idx].strip() if idx < len(lines) else "EOF"))
            continue
        indent = lines[idx][:len(lines[idx]) - len(lines[idx].lstrip())]
        lines[idx:idx + 1] = [
            indent + "//#if FABRIC",
            indent + "//#else",
            lines[idx],
            indent + "//#endif",
        ]
        total += 1
    with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("\n".join(lines))
print("@Override снято:", total)

# --- однострочные сигнатурные правки ---
SIMPLE = [
    # FluidType#getSound: у нас сигнатура (FluidStack, SoundAction)
    (r"appeng\client\AEClientboundPacketHandler.java",
     "soundEvent = fluid.getFluidType().getSound(SoundActions.BUCKET_FILL);",
     "soundEvent = fluid.getFluidType().getSound(fluid, SoundActions.BUCKET_FILL);"),
    # BlockState#getSoundType в ванили без контекста
    (r"appeng\parts\PartPlacement.java",
     "var ss = state.getSoundType(level, pos, player);",
     "var ss = state.getSoundType();"),
    # Block#getExplosionResistance в ванили без контекста
    (r"appeng\entity\TinyTNTPrimedEntity.java",
     ".max(block.getExplosionResistance(state, this.level(), point, ex), fluidResistance);",
     ".max(block.getExplosionResistance(), fluidResistance);"),
]

for rel, old, new in SIMPLE:
    path = None
    for base in (SA, SC):
        p = os.path.join(base, rel)
        if os.path.exists(p):
            path = p
            break
    if not path:
        print("!! нет файла", rel)
        continue
    with io.open(path, encoding="utf-8") as fh:
        text = fh.read()
    if old not in text:
        print("!! не найдено:", rel, "::", old[:40])
        continue
    ls = text.rfind("\n", 0, text.index(old)) + 1
    indent = ""
    for ch in text[ls:]:
        if ch in " \t":
            indent += ch
        else:
            break
    block = "%s//#if FABRIC\n%s%s\n%s//#else\n%s%s\n%s//#endif" % ("", indent, new, indent, indent, old, indent)
    with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text.replace(old, block, 1))
    print("правка:", rel)

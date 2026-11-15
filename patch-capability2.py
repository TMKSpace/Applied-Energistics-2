# -*- coding: utf-8 -*-
"""getCapability: переносим вызов на объект capability (маркерами, по одному месту)."""
import io
import os
import re

ROOT = r"D:\Code\java\ae2-fabric-probe"
SA = os.path.join(ROOT, "src", "main", "java")
SC = os.path.join(ROOT, "src", "client", "java")


def resolve(rel):
    for base in (SA, SC):
        p = os.path.join(base, rel.replace("/", os.sep))
        if os.path.exists(p):
            return p
    return None


def patch(rel, old, new):
    path = resolve(rel)
    if not path:
        print("!! нет файла", rel)
        return
    with io.open(path, encoding="utf-8") as fh:
        text = fh.read()
    if old not in text:
        print("!! не найдено:", rel, "::", old.strip()[:60])
        return
    indent = re.match(r'[ \t]*', old).group(0)
    block = "//#if FABRIC\n%s\n%s//#else\n%s\n%s//#endif" % (new, indent, old, indent)
    with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text.replace(old, block, 1))
    print("правка:", rel)


patch("appeng/api/implementations/blockentities/ICraftingMachine.java",
"""        return blockEntity.getLevel().getCapability(
                AECapabilities.CRAFTING_MACHINE, blockEntity.getBlockPos(), blockEntity.getBlockState(),
                blockEntity, side);""",
"""        return AECapabilities.CRAFTING_MACHINE.getCapability(blockEntity.getLevel(), blockEntity.getBlockPos(),
                blockEntity.getBlockState(), blockEntity, side);""")

patch("appeng/api/implementations/blockentities/ICraftingMachine.java",
"        return level.getCapability(AECapabilities.CRAFTING_MACHINE, pos, side);",
"        return AECapabilities.CRAFTING_MACHINE.getCapability(level, pos, null, null, side);")

patch("appeng/api/implementations/blockentities/ICrankable.java",
"        return level.getCapability(AECapabilities.CRANKABLE, pos, side);",
"        return AECapabilities.CRANKABLE.getCapability(level, pos, null, null, side);")

patch("appeng/api/implementations/blockentities/PatternContainerGroup.java",
"""        var itemHandler = level.getCapability(Capabilities.Item.BLOCK, pos, target.getBlockState(), target,
                side);""",
"""        var itemHandler = Capabilities.Item.BLOCK.getCapability(level, pos, target.getBlockState(), target, side);""")

patch("appeng/api/implementations/blockentities/PatternContainerGroup.java",
"""            var fluidHandler = level.getCapability(Capabilities.Fluid.BLOCK, pos, target.getBlockState(), target,
                    side);""",
"""            var fluidHandler = Capabilities.Fluid.BLOCK.getCapability(level, pos, target.getBlockState(), target, side);""")

patch("appeng/api/inventories/InternalInventory.java",
"        var handler = level.getCapability(Capabilities.Item.BLOCK, pos, side);",
"        var handler = Capabilities.Item.BLOCK.getCapability(level, pos, null, null, side);")

patch("appeng/api/networking/GridHelper.java",
"        return level.getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST, pos, null);",
"        return AECapabilities.IN_WORLD_GRID_NODE_HOST.getCapability(level, pos, null, null, null);")

patch("appeng/helpers/patternprovider/PatternProviderTarget.java",
"            storage = l.getCapability(AECapabilities.ME_STORAGE, be.getBlockPos(), be.getBlockState(), be, side);",
"            storage = AECapabilities.ME_STORAGE.getCapability(l, be.getBlockPos(), be.getBlockState(), be, side);")

patch("appeng/helpers/patternprovider/PatternProviderTarget.java",
"            storage = l.getCapability(AECapabilities.ME_STORAGE, pos, side);",
"            storage = AECapabilities.ME_STORAGE.getCapability(l, pos, null, null, side);")

patch("appeng/debug/EnergyGeneratorBlockEntity.java",
"""            var consumer = getLevel().getCapability(Capabilities.Energy.BLOCK, getBlockPos().relative(facing),
                    facing.getOpposite());""",
"""            var consumer = Capabilities.Energy.BLOCK.getCapability(getLevel(), getBlockPos().relative(facing), null,
                    null, facing.getOpposite());""")

patch("appeng/init/InitCapabilityProviders.java",
"""                    var genericInv = level.getCapability(AECapabilities.GENERIC_INTERNAL_INV, pos, state,
                            blockEntity, context);""",
"""                    var genericInv = AECapabilities.GENERIC_INTERNAL_INV.getCapability(level, pos, state, blockEntity,
                            context);""")

patch("appeng/server/testworld/PlotTestHelper.java",
"        return getLevel().getCapability(cap, absolutePos(ref), context);",
"        return cap.getCapability(getLevel(), absolutePos(ref), null, null, context);")

patch("appeng/hotkeys/CuriosHotkeyAction.java",
"        var cap = player.getCapability(CuriosIntegration.ITEM_HANDLER);",
"        var cap = CuriosIntegration.ITEM_HANDLER.getCapability(player, null);")

patch("appeng/menu/locator/CuriosItemLocator.java",
"        var cap = player.getCapability(CuriosIntegration.ITEM_HANDLER);",
"        var cap = CuriosIntegration.ITEM_HANDLER.getCapability(player, null);")

patch("appeng/util/SearchInventoryEvent.java",
"        var cap = event.getEntity().getCapability(CuriosIntegration.ITEM_HANDLER);",
"        var cap = CuriosIntegration.ITEM_HANDLER.getCapability(event.getEntity(), null);")

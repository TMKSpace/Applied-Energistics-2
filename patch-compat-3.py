import io
import os

ROOT = r"D:\Code\java\ae2-fabric-probe\compat\java\net\neoforged\neoforge"

def patch(rel, pairs, required=True):
    path = os.path.join(ROOT, rel)
    with io.open(path, encoding="utf-8") as fh:
        text = fh.read()
    orig = text
    for old, new in pairs:
        if old not in text:
            if required:
                print("!! NOT FOUND in %s: %r" % (rel, old[:70]))
            continue
        text = text.replace(old, new)
    if text != orig:
        with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
            fh.write(text)
        print("patched", rel)

# CommonHooks - патч NeoForge, у нас его нет
patch("event/entity/player/PlayerDestroyItemEvent.java",
      [("import net.neoforged.neoforge.common.CommonHooks;\n", "")])
patch("transfer/item/PlayerInventoryWrapper.java",
      [("import net.neoforged.neoforge.common.CommonHooks;\n", "")])

# Level#getCapability - патч NeoForge; используем сам BlockCapability
patch("transfer/fluid/FluidUtil.java", [
    ("var fluidHandler = level.getCapability(Capabilities.Fluid.BLOCK, pos, side);",
     "var fluidHandler = Capabilities.Fluid.BLOCK.getCapability(level, pos, null, null, side);"),
    ("var fluidHandler = level.getCapability(Capabilities.Fluid.BLOCK, pos, state, null, side);",
     "var fluidHandler = Capabilities.Fluid.BLOCK.getCapability(level, pos, state, null, side);"),
])
print("done")

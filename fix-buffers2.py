# -*- coding: utf-8 -*-
"""MEInventoryUpdatePacket: вернуть потерянные аргументы буфера."""
import io

PATH = r"D:\Code\java\ae2-fabric-probe\src\main\java\appeng\core\network\clientbound\MEInventoryUpdatePacket.java"

BROKEN = """                encodedEntries =
                //#if FABRIC
                        nfcompat.network.Buffers.create(
                //#else
                        new RegistryFriendlyByteBuf(
                //#endif
                        ConnectionType.NEOFORGE);"""

GOOD = """                encodedEntries =
//#if FABRIC
                        nfcompat.network.Buffers.create(Unpooled.buffer(INITIAL_BUFFER_CAPACITY), registryAccess,
                                ConnectionType.NEOFORGE)
//#else
                        new RegistryFriendlyByteBuf(Unpooled.buffer(INITIAL_BUFFER_CAPACITY), registryAccess,
                                ConnectionType.NEOFORGE)
//#endif
                        ;"""

with io.open(PATH, encoding="utf-8") as fh:
    text = fh.read()
if BROKEN not in text:
    raise SystemExit("не найдено")
with io.open(PATH, "w", encoding="utf-8", newline="\n") as fh:
    fh.write(text.replace(BROKEN, GOOD, 1))
print("MEInventoryUpdatePacket починен")

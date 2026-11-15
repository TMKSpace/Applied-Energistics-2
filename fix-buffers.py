# -*- coding: utf-8 -*-
"""Починка трёх мест, где при замене буфера потерялись аргументы."""
import io

def fix(path, broken, good):
    with io.open(path, encoding="utf-8") as fh:
        text = fh.read()
    if broken not in text:
        print("!! не найдено:", path.split("\\")[-1])
        return
    with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text.replace(broken, good, 1))
    print("починен", path.split("\\")[-1])

BASE = r"D:\Code\java\ae2-fabric-probe\src"

fix(BASE + r"\main\java\appeng\blockentity\AEBaseBlockEntity.java",
    """            } else if (readUpdateData(

                    //#if FABRIC
                            nfcompat.network.Buffers.create(
                    //#else
                            new RegistryFriendlyByteBuf(
                    //#endif
                            ConnectionType.NEOFORGE))) {""",
    """            } else if (readUpdateData(
//#if FABRIC
                    nfcompat.network.Buffers.create(Unpooled.wrappedBuffer(decodedUpdateData), registryAccess,
                            ConnectionType.NEOFORGE)
//#else
                    new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(decodedUpdateData), registryAccess,
                            ConnectionType.NEOFORGE)
//#endif
            )) {""")

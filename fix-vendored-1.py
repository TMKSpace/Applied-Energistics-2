# -*- coding: utf-8 -*-
"""SimpleModelWrapper.bake в ванили берёт Identifier, а не ResolvedModel; + ctor буфера."""
import io

def patch(path, old, new, count=0):
    with io.open(path, encoding="utf-8") as fh:
        text = fh.read()
    if old not in text:
        print("!! НЕ НАЙДЕНО:", path.split("\\")[-1], "::", old[:50])
        return
    text = text.replace(old, new) if count == 0 else text.replace(old, new, count)
    with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)
    print("поправлен", path.split("\\")[-1])

# 1) фабрики: ванильный bake() хочет Identifier
patch(r"D:\Code\java\ae2-fabric-probe\compat\java\net\neoforged\neoforge\client\model\standalone\SimpleUnbakedStandaloneModel.java",
      "SimpleModelWrapper.bake(baker, model, ", "SimpleModelWrapper.bake(baker, modelId, ")

# 2) RegistryFriendlyByteBuf: у ванили нет третьего аргумента ConnectionType
patch(r"D:\Code\java\ae2-fabric-probe\compat\java\net\neoforged\neoforge\common\util\FriendlyByteBufUtil.java",
      "new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess, ConnectionType.NEOFORGE)",
      "new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess)")
patch(r"D:\Code\java\ae2-fabric-probe\compat\java\net\neoforged\neoforge\common\util\FriendlyByteBufUtil.java",
      "import net.neoforged.neoforge.network.connection.ConnectionType;\n", "")

# -*- coding: utf-8 -*-
"""Пакет простых маркеров: буфер с ConnectionType, isComponentsPatchEmpty, FluidStack.is(TagKey)."""
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


def write(rel, text):
    p = os.path.join(ROOT, "compat", "java", rel.replace("/", os.sep))
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with io.open(p, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)
    print("написан", rel)


# 1) Хелпер: NeoForge'вский конструктор буфера с ConnectionType
write("nfcompat/network/Buffers.java", '''/*
 * Часть nfcompat. Создание RegistryFriendlyByteBuf.
 *
 * NeoForge патчит конструктор: `RegistryFriendlyByteBuf(ByteBuf, RegistryAccess, ConnectionType)`.
 * Третий аргумент говорит, какие каналы знает собеседник — на Fabric такой модели нет,
 * поэтому аргумент принимается и игнорируется.
 */
package nfcompat.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;

public final class Buffers {
    private Buffers() {
    }

    public static RegistryFriendlyByteBuf create(ByteBuf buf, RegistryAccess registryAccess, ConnectionType type) {
        return new RegistryFriendlyByteBuf(buf, registryAccess);
    }
}
''')

# 2) Точечные замены
SIMPLE = [
    ("appeng/api/stacks/AEItemKey.java",
     "return !stack.isComponentsPatchEmpty();",
     "return !stack.getComponentsPatch().isEmpty();"),
    ("appeng/api/stacks/AEItemKey.java",
     'return stack.isComponentsPatchEmpty() ? idString : idString + " (with patches)";',
     'return stack.getComponentsPatch().isEmpty() ? idString : idString + " (with patches)";'),
    ("appeng/api/stacks/AEFluidKey.java",
     "return stack.is((TagKey<Fluid>) tag);",
     "return stack.getFluid().builtInRegistryHolder().is(tag);"),
]

for rel, old, new in SIMPLE:
    path = resolve(rel)
    if not path:
        print("!! нет файла", rel)
        continue
    with io.open(path, encoding="utf-8") as fh:
        text = fh.read()
    if old not in text:
        print("!! не найдено в", rel)
        continue
    line_start = text.rfind("\n", 0, text.index(old)) + 1
    indent = re.match(r'[ \t]*', text[line_start:]).group(0)
    block = "%s//#if FABRIC\n%s%s\n%s//#else\n%s%s\n%s//#endif" % ("", indent, new, indent, indent, old, indent)
    text = text.replace(old, block, 1)
    with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)
    print("замена:", rel)

# 3) new RegistryFriendlyByteBuf(...) -> Buffers.create(...), маркерами по строке
pattern = re.compile(r'new RegistryFriendlyByteBuf\(')
for dirpath, _, names in os.walk(os.path.join(ROOT, "src")):
    for name in names:
        if not name.endswith(".java"):
            continue
        p = os.path.join(dirpath, name)
        with io.open(p, encoding="utf-8") as fh:
            text = fh.read()
        if "new RegistryFriendlyByteBuf(" not in text or "Buffers.create(" in text:
            continue
        out, touched = [], False
        for line in text.split("\n"):
            m = pattern.search(line)
            if m and "//#if" not in line.strip():
                head = line[:m.start()]
                indent = re.match(r'[ \t]*', line).group(0)
                deeper = indent + "        "
                out.append(head.rstrip())
                out.append(indent + "//#if FABRIC")
                out.append(deeper + "nfcompat.network.Buffers.create(")
                out.append(indent + "//#else")
                out.append(deeper + "new RegistryFriendlyByteBuf(")
                out.append(indent + "//#endif")
                touched = True
                continue
            out.append(line)
        if touched:
            with io.open(p, "w", encoding="utf-8", newline="\n") as fh:
                fh.write("\n".join(out))
            print("буфер:", os.path.relpath(p, os.path.join(ROOT, "src")))

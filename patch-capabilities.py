# -*- coding: utf-8 -*-
"""Батч маркеров: getCapability, isComponentsPatchEmpty, RegistryFriendlyByteBuf, connection.send, FluidStack.is(TagKey)."""
import io
import os
import re

SRC = r"D:\Code\java\ae2-fabric-probe\src"


def split_args(text):
    """Делит аргументы по запятым верхнего уровня."""
    args, depth, cur = [], 0, ""
    for ch in text:
        if ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth -= 1
        if ch == "," and depth == 0:
            args.append(cur.strip())
            cur = ""
        else:
            cur += ch
    if cur.strip():
        args.append(cur.strip())
    return args


def find_call(text, marker, start=0):
    """Возвращает (начало получателя, индекс marker, индекс закрывающей скобки, аргументы)."""
    idx = text.find(marker, start)
    if idx < 0:
        return None
    open_paren = idx + len(marker) - 1  # позиция '('
    depth = 0
    for i in range(open_paren, len(text)):
        if text[i] == '(':
            depth += 1
        elif text[i] == ')':
            depth -= 1
            if depth == 0:
                return idx, open_paren, i, text[open_paren + 1:i]
    return None


def receiver_start(text, dot_index):
    """Идём назад от точки, собирая выражение-получатель."""
    i = dot_index - 1
    depth = 0
    while i >= 0:
        ch = text[i]
        if ch in ")]":
            depth += 1
        elif ch in "([":
            if depth == 0:
                break
            depth -= 1
        elif depth == 0 and not (ch.isalnum() or ch in "_$."):
            break
        i -= 1
    return i + 1


def indent_of(text, pos):
    line_start = text.rfind("\n", 0, pos) + 1
    m = re.match(r'[ \t]*', text[line_start:])
    return m.group(0)


report = []


def patch_file(rel, transform):
    path = os.path.join(SRC, rel.replace("/", os.sep))
    with io.open(path, encoding="utf-8") as fh:
        text = fh.read()
    if "nfcompat" in text and transform.__name__ == "cap":
        pass
    new = transform(text)
    if new is None or new == text:
        return False
    with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(new)
    report.append(rel)
    return True


# ---------- 1. getCapability: переносим на объект capability ----------
def cap(text):
    out = text
    changed = False
    search = 0
    while True:
        # ищем ".getCapability(" у получателя, который НЕ является самой capability
        m = re.search(r'\.getCapability\(', out[search:])
        if not m:
            break
        dot = search + m.start()
        start = receiver_start(out, dot)
        recv = out[start:dot]
        if recv.startswith("nfcompat"):
            search = dot + 1
            continue
        call = find_call(out, ".getCapability(", dot)
        if not call:
            break
        _, open_paren, close_paren, args_text = call
        args = split_args(args_text)
        indent = indent_of(out, start)
        if len(args) == 2:          # (cap, side) — предмет/сущность
            fabric = "%s.getCapability(%s, %s)" % (args[0], recv, args[1])
        elif len(args) == 3:        # (cap, pos, side)
            fabric = "%s.getCapability(%s, %s, null, null, %s)" % (args[0], recv, args[1], args[2])
        elif len(args) == 5:        # (cap, pos, state, be, side)
            fabric = "%s.getCapability(%s, %s, %s, %s, %s)" % (args[0], recv, args[1], args[2], args[3], args[4])
        else:
            search = dot + 1
            continue
        original = out[start:close_paren + 1]
        out = (out[:start]
               + "%s//#if FABRIC\n%s%s\n%s//#else\n%s%s\n%s//#endif" % (
                   "", indent, fabric, indent, indent, original, indent)
               + out[close_paren + 1:])
        changed = True
        search = start + len(fabric) + 60
    return out if changed else None


# ---------- 2..5: простые однострочные замены ----------
SIMPLE = [
    # isComponentsPatchEmpty
    (r"appeng/api/stacks/AEItemKey.java",
     "return !stack.isComponentsPatchEmpty();",
     "return !stack.getComponentsPatch().isEmpty();"),
    (r"appeng/api/stacks/AEItemKey.java",
     "return stack.isComponentsPatchEmpty() ? idString : idString + \" (with patches)\";",
     "return stack.getComponentsPatch().isEmpty() ? idString : idString + \" (with patches)\";"),
    # FluidStack.is(TagKey)
    (r"appeng/api/stacks/AEFluidKey.java",
     "return stack.is((TagKey<Fluid>) tag);",
     "return stack.getFluid().builtInRegistryHolder().is(tag);"),
]

files_cap = [
    "appeng/api/implementations/blockentities/ICraftingMachine.java",
    "appeng/api/implementations/blockentities/ICrankable.java",
    "appeng/api/implementations/blockentities/PatternContainerGroup.java",
    "appeng/api/features/P2PTunnelAttunement.java",
    "appeng/api/inventories/InternalInventory.java",
    "appeng/api/networking/GridHelper.java",
    "appeng/helpers/patternprovider/PatternProviderTarget.java",
]

for rel in []:
    if patch_file(rel, cap):
        print("getCapability:", rel)

for rel, old, new in SIMPLE:
    path = os.path.join(SRC, rel.replace("/", os.sep))
    with io.open(path, encoding="utf-8") as fh:
        text = fh.read()
    if old not in text:
        print("!! НЕ НАЙДЕНО:", rel, "::", old[:40])
        continue
    indent = indent_of(text, text.index(old))
    text = text.replace(old, "%s//#if FABRIC\n%s%s\n%s//#else\n%s%s\n%s//#endif" % (
        "", indent, new, indent, indent, old.strip(), indent), 1)
    with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)
    print("замена:", rel)

print("итого файлов:", len(report))


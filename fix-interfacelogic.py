# -*- coding: utf-8 -*-
"""Точечная чистка вложенных маркеров в InterfaceLogic."""
import io

PATH = r"D:\Code\java\ae2-fabric-probe\src\main\java\appeng\helpers\InterfaceLogic.java"

with io.open(PATH, encoding="utf-8") as fh:
    lines = fh.read().split("\n")

# 225..233 (1-based) -> индексы 224..232
start = None
for i in range(len(lines) - 8):
    if (lines[i].strip() == "//#if FABRIC"
            and lines[i + 2].strip() == "//#else"
            and lines[i + 3].strip() == "//#if FABRIC"
            and lines[i + 5].strip() == "//#else"
            and lines[i + 7].strip() == "//#endif"
            and lines[i + 8].strip() == "//#endif"):
        start = i
        break

if start is None:
    raise SystemExit("шаблон не найден")

indent = lines[start][:len(lines[start]) - len(lines[start].lstrip())]
replacement = [
    lines[start],            # //#if FABRIC
    lines[start + 4],        # fabric-вариант (внутренний)
    indent + "//#else",
    lines[start + 6],        # оригинал
    indent + "//#endif",
]
lines = lines[:start] + replacement + lines[start + 9:]

with io.open(PATH, "w", encoding="utf-8", newline="\n") as fh:
    fh.write("\n".join(lines))
print("InterfaceLogic: маркеры схлопнуты, строка", start + 1)

for i in range(start, start + 6):
    print("%d: %s" % (i + 1, lines[i].strip()))

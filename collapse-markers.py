# -*- coding: utf-8 -*-
"""Схлопываем вложенные одинаковые маркеры (#if FABRIC / #else / #if FABRIC ...)."""
import io
import os

SRC = r"D:\Code\java\ae2-fabric-probe\src"
fixed = 0

for dirpath, _, names in os.walk(SRC):
    for name in names:
        if not name.endswith(".java"):
            continue
        path = os.path.join(dirpath, name)
        with io.open(path, encoding="utf-8") as fh:
            lines = fh.read().split("\n")

        out = []
        i = 0
        changed = False
        while i < len(lines):
            # искомый шаблон длиной 8 строк
            if (i + 7 < len(lines)
                    and lines[i].strip() == "//#if FABRIC"
                    and lines[i + 1].strip() == "//#else"
                    and lines[i + 2].strip() == "//#if FABRIC"
                    and lines[i + 5].strip() == "//#else"
                    and lines[i + 6].strip() == "//#endif"
                    and lines[i + 7].strip() == "//#endif"):
                indent = lines[i][:len(lines[i]) - len(lines[i].lstrip())]
                out.append(lines[i])            # //#if FABRIC
                out.append(lines[i + 3])        # fabric-вариант (внутренний)
                out.append(indent + "//#else")
                out.append(lines[i + 4])        # оригинал
                out.append(indent + "//#endif")
                changed = True
                i += 8
                continue
            out.append(lines[i])
            i += 1

        if changed:
            with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
                fh.write("\n".join(out))
            fixed += 1
            print("схлопнут:", os.path.relpath(path, SRC))
print("файлов:", fixed)

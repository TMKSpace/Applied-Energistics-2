# -*- coding: utf-8 -*-
"""Маркеры для recipeMap(): правильный разбор получателя (с вызовами методов)."""
import io
import os
import re

SRC = r"D:\Code\java\ae2-fabric-probe\src"
CALL = re.compile(r'([A-Za-z_][\w\.]*(?:\(\))?(?:\.\w+\(\))*)\.recipeMap\(\)')

changed = 0
for dirpath, _, names in os.walk(SRC):
    for name in names:
        if not name.endswith(".java"):
            continue
        path = os.path.join(dirpath, name)
        with io.open(path, encoding="utf-8") as fh:
            text = fh.read()
        if "RecipeMaps.of(" in text:
            continue  # файл уже обработан
        if ".recipeMap()" not in text:
            continue

        out = []
        touched = False
        for line in text.split("\n"):
            if ".recipeMap()" in line and "recipeMap." not in line:
                fabric, n = CALL.subn(lambda m: "nfcompat.recipes.RecipeMaps.of(" + m.group(1) + ")", line, count=1)
                if n:
                    indent = re.match(r'\s*', line).group(0)
                    out.append(indent + "//#if FABRIC")
                    out.append(fabric)
                    out.append(indent + "//#else")
                    out.append(line)
                    out.append(indent + "//#endif")
                    touched = True
                    continue
            out.append(line)

        if touched:
            with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
                fh.write("\n".join(out))
            changed += 1
            print("маркеры:", os.path.relpath(path, SRC))
print("файлов затронуто:", changed)

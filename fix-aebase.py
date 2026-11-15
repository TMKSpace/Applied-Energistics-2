# -*- coding: utf-8 -*-
"""Починка вложенных маркеров в AEBaseBlockEntity + остаток invalidateCapabilities."""
import io
import os
import re

ROOT = r"D:\Code\java\ae2-fabric-probe"

PATH = os.path.join(ROOT, "src", "main", "java", "appeng", "blockentity", "AEBaseBlockEntity.java")
with io.open(PATH, encoding="utf-8") as fh:
    lines = fh.read().split("\n")

# ищем начало сломанного блока: строка "//#if FABRIC" перед getModelData (первая после 445)
start = None
for i in range(440, len(lines)):
    if lines[i].strip() == "//#if FABRIC":
        start = i
        break
# конец блока — строка с "//#endif" перед "return AEModelData.create();"
end = None
for i in range(start, len(lines)):
    if lines[i].strip() == "return AEModelData.create();":
        end = i - 1  # строка "//#endif" или "public ModelData getModelData() {"
        break

if start is None or end is None:
    raise SystemExit("не нашёл границы блока: start=%s end=%s" % (start, end))

BLOCK = '''//#if FABRIC
    /**
     * NeoForge патчит `BlockEntity`, добавляя `onChunkUnloaded()`; в ванили такого хука
     * нет (есть `setRemoved()`). Объявляем здесь, чтобы вся иерархия AE2 компилировалась
     * и `super.onChunkUnloaded()` в наследниках был валиден.
     *
     * TODO: вызывать из миксина на `BlockEntity#setRemoved`, иначе метод не работает.
     */
    public void onChunkUnloaded() {
    }

    /**
     * NeoForge добавляет `invalidateCapabilities()`. Нужен он для его кэша capability,
     * а у нас кэша нет — каждый запрос идёт в провайдеры напрямую, инвалидировать нечего.
     */
    public void invalidateCapabilities() {
    }

    // NeoForge не переопределяет BlockEntity#getModelData(): у нас этот метод объявлен
    // в интерфейсе ModelDataProvider, поэтому @Override здесь не к чему прикрепить.
    public ModelData getModelData() {
//#else
    @Override
    public ModelData getModelData() {
//#endif'''.split("\n")

new_lines = lines[:start] + BLOCK + lines[end + 1:]
with io.open(PATH, "w", encoding="utf-8", newline="\n") as fh:
    fh.write("\n".join(new_lines))
print("AEBaseBlockEntity: блок переписан, было строк %d, стало %d" % (len(lines), len(new_lines)))

# --- остаток invalidateCapabilities на ванильном BlockEntity ---
SRC = os.path.join(ROOT, "src")
PAT = re.compile(r'([A-Za-z_][\w\.]*(?:\(\))?(?:\.\w+\(\))*)\.invalidateCapabilities\(\)')
changed = 0
for dirpath, _, names in os.walk(SRC):
    for name in names:
        if not name.endswith(".java"):
            continue
        p = os.path.join(dirpath, name)
        with io.open(p, encoding="utf-8") as fh:
            text = fh.read()
        if ".invalidateCapabilities()" not in text:
            continue
        out, touched = [], False
        for line in text.split("\n"):
            if ".invalidateCapabilities()" in line and "CapabilityInvalidation" not in line:
                fabric, n = PAT.subn(lambda m: "nfcompat.capabilities.CapabilityInvalidation.invalidate(" + m.group(1) + ")", line, count=1)
                if n:
                    indent = re.match(r'\s*', line).group(0)
                    out += [indent + "//#if FABRIC", fabric, indent + "//#else", line, indent + "//#endif"]
                    touched = True
                    continue
            out.append(line)
        if touched:
            with io.open(p, "w", encoding="utf-8", newline="\n") as fh:
                fh.write("\n".join(out))
            changed += 1
            print("маркеры:", os.path.relpath(p, SRC))
print("доправлено файлов:", changed)

# -*- coding: utf-8 -*-
"""onChunkUnloaded в базу AE2 + invalidateCapabilities через хелпер."""
import io
import os
import re

ROOT = r"D:\Code\java\ae2-fabric-probe"

# 1) Хелпер инвалидации: у нас нечего инвалидировать (кэша нет)
HELPER_DIR = os.path.join(ROOT, "compat", "java", "nfcompat", "capabilities")
os.makedirs(HELPER_DIR, exist_ok=True)
with io.open(os.path.join(HELPER_DIR, "CapabilityInvalidation.java"), "w", encoding="utf-8", newline="\n") as fh:
    fh.write('''/*
 * Часть nfcompat. Инвалидация кэшей capability.
 *
 * NeoForge патчит `BlockEntity` и `Level`, добавляя `invalidateCapabilities()`: это сигнал
 * «провайдеры для этого объекта/позиции могли измениться». Нужен он потому, что у NeoForge
 * есть кэш (`BlockCapabilityCache`). У нас кэша нет — каждый запрос идёт в
 * `BlockCapability.getCapability(...)` напрямую, — поэтому инвалидировать нечего.
 *
 * Если позже появится кэш (см. TODO в `ModelDataHooks`), сюда придёт его сброс.
 */
package nfcompat.capabilities;

import org.jetbrains.annotations.Nullable;

public final class CapabilityInvalidation {
    private CapabilityInvalidation() {
    }

    public static void invalidate(@Nullable Object holder) {
        // no-op: кэша нет
    }
}
''')
print("написан nfcompat/capabilities/CapabilityInvalidation.java")

# 2) AEBaseBlockEntity: onChunkUnloaded как свой метод (иначе @Override у наследников невалиден)
path = os.path.join(ROOT, "src", "main", "java", "appeng", "blockentity", "AEBaseBlockEntity.java")
with io.open(path, encoding="utf-8") as fh:
    text = fh.read()

anchor = """    @Override
    public ModelData getModelData() {"""
add = """//#if FABRIC
    /**
     * NeoForge патчит `BlockEntity`, добавляя `onChunkUnloaded()`. В ванили такого хука нет
     * (есть `setRemoved()`). Объявляем его здесь, чтобы вся иерархия AE2 продолжала
     * компилироваться и `super.onChunkUnloaded()` в наследниках был валиден.
     *
     * TODO: вызывать из миксина на `BlockEntity#setRemoved`, иначе метод не работает.
     */
    public void onChunkUnloaded() {
    }
//#endif

    @Override
    public ModelData getModelData() {"""

if anchor in text and "public void onChunkUnloaded()" not in text:
    text = text.replace(anchor, add, 1)
    with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)
    print("AEBaseBlockEntity: onChunkUnloaded добавлен")
else:
    print("AEBaseBlockEntity: якорь не найден или уже есть")

# 3) invalidateCapabilities -> хелпер, маркерами
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
        if ".invalidateCapabilities()" not in text or "CapabilityInvalidation" in text:
            continue
        out, touched = [], False
        for line in text.split("\n"):
            if ".invalidateCapabilities()" in line:
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
print("файлов с invalidateCapabilities:", changed)

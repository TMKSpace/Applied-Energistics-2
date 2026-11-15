# -*- coding: utf-8 -*-
"""Добор мелких пакетов NeoForge, которые зовёт AE2 (вендорим из клона)."""
import io
import os
import shutil

NF = os.path.expandvars(r"%LOCALAPPDATA%\Temp\opencode\nf")
DEST = r"D:\Code\java\ae2-fabric-probe\compat\java"

COPY = []

def add_dir(rel_dir, names=None, only=None):
    src_dir = os.path.join(NF, rel_dir)
    if not os.path.isdir(src_dir):
        print("НЕТ каталога:", rel_dir)
        return
    if names is None:
        names = [f for f in os.listdir(src_dir) if f.endswith(".java")]
    if only:
        names = [n for n in names if n in only]
    for n in names:
        COPY.append((os.path.join(src_dir, n), rel_dir, n))

MAIN = "src/main/java/net/neoforged/neoforge"
CLIENT = "src/client/java/net/neoforged/neoforge"

add_dir(MAIN + "/registries/callback")
add_dir(MAIN + "/registries/holdersets")
add_dir(MAIN + "/common/loot", only={"NeoForgeLootContextParams.java"})
add_dir(MAIN + "/common/crafting", only={"ICustomIngredient.java", "DifferenceIngredient.java"})
add_dir(CLIENT + "/client/settings", only={"IKeyConflictContext.java", "KeyConflictContext.java"})
add_dir(CLIENT + "/client/extensions/common")

copied = 0
for src, rel_dir, name in COPY:
    rel = rel_dir.split("java/", 1)[1]          # net/neoforged/neoforge/...
    dst_dir = os.path.join(DEST, rel.replace("/", os.sep))
    os.makedirs(dst_dir, exist_ok=True)
    dst = os.path.join(dst_dir, name)
    with io.open(src, encoding="utf-8") as fh:
        text = fh.read()
    # BOM не нужен, переводы строк — LF
    with io.open(dst, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text.lstrip("\ufeff"))
    copied += 1
    print("copied", rel + "/" + name)

print("итого:", copied)

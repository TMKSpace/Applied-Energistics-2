# -*- coding: utf-8 -*-
"""RecipeMap: свой класс в ванильном пакете + хелпер + маркеры на 13 вызовах."""
import io
import os
import re

ROOT = r"D:\Code\java\ae2-fabric-probe\compat\java"

FILES = {}

# ВАЖНО: NeoForge добавляет `RecipeMap` в ванильный пакет. Класса нет в MC 26.3,
# но AE2 его импортирует, поэтому кладём свой — в тот же пакет (split package).
FILES["net/minecraft/world/item/crafting/RecipeMap.java"] = '''/*
 * Часть nfcompat. Карта рецептов.
 *
 * NeoForge добавляет этот класс в ванильный пакет: он группирует рецепты по типу и
 * умеет искать по ключу. В MC 26.3 такого класса нет, а AE2 его импортирует и активно
 * использует (`byType`, `getRecipesFor`, `byKey`), поэтому класс объявлен здесь.
 *
 * Это единственное место, где шим кладёт свой класс в пакет `net.minecraft.*`:
 * иначе импорт AE2 не разрешить никак. Пакет не запечатан, Fabric грузит класс из мода.
 */
package net.minecraft.world.item.crafting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public final class RecipeMap {
    public static final RecipeMap EMPTY = new RecipeMap(List.of());

    private final Map<RecipeType<?>, List<RecipeHolder<?>>> byType = new HashMap<>();
    private final Map<ResourceKey<Recipe<?>>, RecipeHolder<?>> byKey = new HashMap<>();

    private RecipeMap(Collection<RecipeHolder<?>> recipes) {
        for (RecipeHolder<?> holder : recipes) {
            byType.computeIfAbsent(holder.value().getType(), t -> new ArrayList<>()).add(holder);
            byKey.put(holder.id(), holder);
        }
    }

    public static RecipeMap of(Collection<RecipeHolder<?>> recipes) {
        return recipes.isEmpty() ? EMPTY : new RecipeMap(recipes);
    }

    @SuppressWarnings("unchecked")
    public <I extends RecipeInput, T extends Recipe<I>> Collection<RecipeHolder<T>> byType(RecipeType<T> type) {
        return (Collection<RecipeHolder<T>>) (Collection<?>) byType.getOrDefault(type, List.of());
    }

    @SuppressWarnings("unchecked")
    public <I extends RecipeInput, T extends Recipe<I>> Stream<RecipeHolder<T>> getRecipesFor(RecipeType<T> type,
            I input, Level level) {
        return byType(type).stream()
                .filter(holder -> ((Recipe<I>) holder.value()).matches(input, level));
    }

    @Nullable
    public RecipeHolder<?> byKey(ResourceKey<Recipe<?>> key) {
        return byKey.get(key);
    }

    public Collection<RecipeHolder<?>> values() {
        return byKey.values();
    }

    public boolean isEmpty() {
        return byKey.isEmpty();
    }
}
'''

FILES["nfcompat/recipes/RecipeMaps.java"] = '''/*
 * Часть nfcompat. Доступ к карте рецептов.
 *
 * NeoForge патчит `RecipeAccess`, добавляя `recipeMap()`. В ванили есть
 * `RecipeManager#getRecipes()`, из которого карта и строится. AE2 в FABRIC-ветке
 * зовёт этот хелпер вместо `recipeMap()`.
 *
 * Карта строится на каждый вызов: кэша нет, потому что момент перезагрузки рецептов
 * нам не виден. Для поиска рецепта при крафте это лишний проход по коллекции —
 * TODO: кэшировать по `RecipeManager` и сбрасывать на перезагрузке датапаков.
 */
package nfcompat.recipes;

import net.minecraft.world.item.crafting.RecipeAccess;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;

public final class RecipeMaps {
    private RecipeMaps() {
    }

    public static RecipeMap of(RecipeAccess access) {
        if (access instanceof RecipeManager manager) {
            return RecipeMap.of(manager.getRecipes());
        }
        return RecipeMap.EMPTY;
    }
}
'''

for rel, text in FILES.items():
    path = os.path.join(ROOT, rel.replace("/", os.sep))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)
    print("написан", rel)

# --- маркеры в исходниках AE2 ---

SRC = r"D:\Code\java\ae2-fabric-probe\src"
CALL = re.compile(r'((?:[\w\.]+\.)?[\w\.]+?)\.recipeMap\(\)')

changed = 0
for dirpath, _, names in os.walk(SRC):
    for name in names:
        if not name.endswith(".java"):
            continue
        path = os.path.join(dirpath, name)
        with io.open(path, encoding="utf-8") as fh:
            lines = fh.read().split("\n")
        out = []
        touched = False
        for line in lines:
            stripped = line.strip()
            # только настоящие вызовы метода, не поле recipeMap
            if ".recipeMap()" in line and "//#if" not in stripped and "private" not in stripped:
                indent = re.match(r'\s*', line).group(0)
                fabric = CALL.sub(lambda m: "nfcompat.recipes.RecipeMaps.of(" + m.group(1) + ")", line, count=1)
                if fabric != line:
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

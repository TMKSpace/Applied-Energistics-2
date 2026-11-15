/*
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

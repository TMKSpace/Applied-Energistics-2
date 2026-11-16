/*
 * Часть nfcompat. Доступ к карте рецептов.
 *
 * NeoForge патчит `RecipeAccess`, добавляя `recipeMap()`. В непатченной ванили такого
 * аксессора нет и рецептов на клиенте вообще нет (`ClientLevel.recipeAccess()` —
 * это `ClientRecipeContainer` без списка). Зато на сервере карта лежит в
 * `RecipeManager.recipes`, а `ServerLevel.recipeAccess()` возвращает сам `RecipeManager`.
 *
 * Поэтому вместо `X.recipeAccess().recipeMap()` в FABRIC-ветке вызывается этот хелпер.
 *
 * ЧЕГО НЕ ДЕЛАТЬ: `RecipeMap.create(access.lookupOrThrow(Registries.RECIPE))` —
 * реестр `minecraft:recipe` есть у датапаков, но не в `server.registryAccess()`,
 * вызов падает с "Missing registry: minecraft:recipe" прямо в тике сервера.
 *
 * На клиенте карта приходит пакетом и собирается через `createFrom`: публичной
 * фабрики без реестра у ванильного `RecipeMap` нет, а конструктор приватный.
 */
package nfcompat.recipes;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import nfcompat.mixin.RecipeManagerAccessor;

public final class RecipeMaps {
    private static final Constructor<RecipeMap> CONSTRUCTOR = resolveConstructor();

    private RecipeMaps() {
    }

    public static RecipeMap of(Level level) {
        if (level.recipeAccess() instanceof RecipeManager manager) {
            return of(manager);
        }
        // Клиент: если синхронизация ещё не пришла, отдаём пустую карту, чтобы вызывающий
        // код не падал (AE2 сам проверяет knownRecipeTypes и предупреждает в лог).
        return RecipeMap.EMPTY;
    }

    public static RecipeMap of(RecipeManager manager) {
        return ((RecipeManagerAccessor) manager).nfcompat$recipes();
    }

    /** Собрать карту из готового списка рецептов (клиент, после синхронизации). */
    public static RecipeMap createFrom(Collection<RecipeHolder<?>> recipes) {
        Multimap<RecipeType<?>, RecipeHolder<?>> byType = buildByType(recipes);

        Map<ResourceKey<Recipe<?>>, RecipeHolder<?>> byKey = new HashMap<>();
        for (var holder : recipes) {
            byKey.put(holder.id(), holder);
        }

        try {
            return CONSTRUCTOR.newInstance(byType, Map.copyOf(byKey));
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Не удалось собрать RecipeMap из " + recipes.size() + " рецептов", e);
        }
    }

    private static Multimap<RecipeType<?>, RecipeHolder<?>> buildByType(Collection<RecipeHolder<?>> recipes) {
        ImmutableMultimap.Builder<RecipeType<?>, RecipeHolder<?>> builder = ImmutableMultimap.builder();
        for (var holder : recipes) {
            builder.put(holder.value().getType(), holder);
        }
        return builder.build();
    }

    private static Constructor<RecipeMap> resolveConstructor() {
        try {
            Constructor<RecipeMap> constructor = RecipeMap.class
                    .getDeclaredConstructor(Multimap.class, Map.class);
            constructor.setAccessible(true);
            return constructor;
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}

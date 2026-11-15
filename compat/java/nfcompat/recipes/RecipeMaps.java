/*
 * Часть nfcompat. Доступ к карте рецептов.
 *
 * NeoForge патчит `RecipeAccess`, добавляя `recipeMap()`. В ванили такого аксессора нет,
 * зато есть `RecipeMap.create(HolderLookup<Recipe<?>>)`, а lookup берётся из
 * `RegistryAccess`. Поэтому вместо `X.recipeAccess().recipeMap()` в FABRIC-ветке
 * вызывается этот хелпер с уровнем (или с RegistryAccess).
 *
 * `RecipeMap.create` строит карту по реестру рецептов, то есть её не надо собирать
 * вручную. TODO: если это окажется горячим путём, кэшировать по RegistryAccess.
 */
package nfcompat.recipes;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.level.Level;

public final class RecipeMaps {
    private RecipeMaps() {
    }

    public static RecipeMap of(Level level) {
        return of(level.registryAccess());
    }

    public static RecipeMap of(RegistryAccess access) {
        return RecipeMap.create(access.lookupOrThrow(Registries.RECIPE));
    }
}

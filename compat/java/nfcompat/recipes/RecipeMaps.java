/*
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

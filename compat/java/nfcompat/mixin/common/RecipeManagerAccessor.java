/*
 * Часть nfcompat. Доступ к карте рецептов из `RecipeManager`.
 *
 * ПОЧЕМУ ЧЕРЕЗ ПОЛЕ, А НЕ ЧЕРЕЗ РЕЕСТР
 * В 26.3 рецепты живут не в `RegistryAccess`, а в самом `RecipeManager`:
 * конструктор кладёт их в приватное поле `recipes` (`RecipeMap`). Реестр
 * `minecraft:recipe` в `server.registryAccess()` при этом отсутствует — попытка
 * `lookupOrThrow(Registries.RECIPE)` падает с "Missing registry".
 *
 * NeoForge решает это патчем `RecipeAccess#recipeMap()`. У нас патча нет, поэтому
 * берём поле аксессором: `RecipeManager` — единственный держатель полной карты,
 * и он же пересоздаётся при перезагрузке датапаков, так что читать его безопасно.
 */
package nfcompat.mixin.common;

import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RecipeManager.class)
public interface RecipeManagerAccessor {

    @Accessor("recipes")
    RecipeMap nfcompat$recipes();
}

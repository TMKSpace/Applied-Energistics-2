/*
 * Часть nfcompat. Ингредиент «база минус вычитаемое».
 *
 * NeoForge'вская версия — кастомный ингредиент со своим типом и кодеком (в ванильном
 * `Ingredient` такого механизма нет). Здесь считается на месте: берём элементы базы,
 * выбрасываем те, что принимает вычитаемое, и собираем прямой HolderSet.
 *
 * Плата: результат — обычный ванильный ингредиент, то есть в датагене он запишется
 * развёрнутым списком, а не ссылкой на тег. Для генерации данных AE2 это допустимо.
 */
package net.neoforged.neoforge.common.crafting;

import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;

public final class DifferenceIngredient {
    private DifferenceIngredient() {
    }

    public static Ingredient of(Ingredient base, Ingredient subtracted) {
        Set<Holder<Item>> excluded = subtracted.items().collect(Collectors.toSet());
        var kept = base.items().filter(holder -> !excluded.contains(holder)).toList();
        return Ingredient.of(HolderSet.direct(kept));
    }
}

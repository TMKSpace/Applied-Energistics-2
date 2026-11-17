/*
 * Часть nfcompat. Null-safe сравнение стека с шаблоном (патч NeoForge к `ItemStack`).
 *
 * ЗАЧЕМ. NeoForge добавляет в `ItemStack` перегрузки, принимающие `ItemStackTemplate`,
 * и в них `null` — легальное значение, означающее "пустой слот":
 *
 *     public static boolean isSameItemSameComponents(ItemStack a, @Nullable ItemStackTemplate b) {
 *         if (a.isEmpty() || b == null) {
 *             return a.isEmpty() == (b == null);
 *         }
 *         return a.is(b.item()) && a.components.patchEquals(b.components());
 *     }
 *
 * Миксином такое не добавить (non-private static в чужой класс), поэтому у нас это
 * отдельный хелпер, а FABRIC-ветки в `src` зовут его напрямую.
 *
 * ГДЕ ЖДАЛО ГРАБЛИ. `InscriberTestPlots` передаёт `null` в ожидаемых слотах (это нормальный
 * "слот пуст"), а FABRIC-ветка звала `template.create()` — NPE и падение всего GameTestServer
 * (в 26.3 внутри тика геймтеста ловится только `GameTestAssertException`).
 * `ItemResource.matches(@Nullable ItemStackTemplate)` в шиме страдал тем же.
 */
package nfcompat.compat;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

import org.jetbrains.annotations.Nullable;

public final class ItemStackTemplates {

    private ItemStackTemplates() {
    }

    /**
     * {@return true, если стек соответствует шаблону; {@code null}-шаблон означает пустой слот}
     */
    public static boolean isSameItemSameComponents(ItemStack stack, @Nullable ItemStackTemplate template) {
        if (template == null) {
            return stack.isEmpty();
        }
        if (stack.isEmpty()) {
            return false;
        }
        return ItemStack.isSameItemSameComponents(stack, template.create());
    }
}

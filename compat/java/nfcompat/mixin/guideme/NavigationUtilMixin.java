/*
 * Часть nfcompat (GuideME). Конструкторы `ItemStackTemplate`, которых нет в ванили.
 *
 * NeoForge добавляет к `ItemStackTemplate` перегрузки с `Holder<Item>`:
 *     ItemStackTemplate(Holder<Item>)
 *     ItemStackTemplate(Holder<Item>, DataComponentPatch)
 * Ванильный конструктор с Holder'ом один и с количеством:
 *     ItemStackTemplate(Holder<Item>, int, DataComponentPatch)
 *
 * GuideME строит иконки навигации по страницам гайда (`NavigationUtil.createNavigationIcon`),
 * то есть это первое, что вызывается при открытии гайда:
 *
 *     NoSuchMethodError: ItemStackTemplate.<init>(Lnet/minecraft/core/Holder;)V
 *
 * Добавить конструктор миксином нельзя, поэтому подменяем создание объекта (`NEW`) на
 * ванильный трёхаргументный конструктор с количеством 1. В методе три `NEW ItemStackTemplate`,
 * из них проблемные — первый (Holder + Patch) и третий (только Holder); средний уже ванильный,
 * его не трогаем, поэтому адресация по `ordinal`.
 */
package nfcompat.mixin.guideme;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "guideme.internal.util.NavigationUtil")
public class NavigationUtilMixin {

    /** `new ItemStackTemplate(holder, patch)` -> `new ItemStackTemplate(holder, 1, patch)`. */
    @Redirect(method = "createNavigationIcon", require = 0,
            at = @At(value = "NEW", target = "net/minecraft/world/item/ItemStackTemplate", ordinal = 0))
    private static ItemStackTemplate nfcompat$withCount(Holder<Item> item, DataComponentPatch patch) {
        return new ItemStackTemplate(item, 1, patch);
    }

    /** `new ItemStackTemplate(holder)` -> `new ItemStackTemplate(holder, 1, EMPTY)`. */
    @Redirect(method = "createNavigationIcon", require = 0,
            at = @At(value = "NEW", target = "net/minecraft/world/item/ItemStackTemplate", ordinal = 2))
    private static ItemStackTemplate nfcompat$withCountAndEmptyPatch(Holder<Item> item) {
        return new ItemStackTemplate(item, 1, DataComponentPatch.EMPTY);
    }
}

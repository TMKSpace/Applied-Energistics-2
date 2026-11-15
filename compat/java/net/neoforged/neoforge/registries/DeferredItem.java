/*
 * Часть nfcompat. Шим net.neoforged.neoforge.registries.DeferredItem для Fabric.
 * Сигнатуры повторяют NeoForge 26.3 (DeferredItem/DeferredBlock — уже не DeferredHolder<Item,...>,
 * а отдельный класс, реализующий ItemLike).
 */
package net.neoforged.neoforge.registries;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public class DeferredItem<T extends Item> extends DeferredHolder<Item, T> implements ItemLike {

    /** Стек из одного предмета. */
    public ItemStack toStack() {
        return toStack(1);
    }

    /** Стек заданного размера. */
    public ItemStack toStack(int count) {
        ItemStack stack = asItem().getDefaultInstance();
        if (stack.isEmpty()) {
            throw new IllegalStateException("Obtained empty item stack; incorrect getDefaultInstance() call?");
        }
        stack.setCount(count);
        return stack;
    }

    public static <T extends Item> DeferredItem<T> createItem(Identifier key) {
        return createItem(ResourceKey.create(Registries.ITEM, key));
    }

    public static <T extends Item> DeferredItem<T> createItem(ResourceKey<Item> key) {
        return new DeferredItem<>(key);
    }

    protected DeferredItem(ResourceKey<Item> key) {
        super(key);
    }

    @Override
    public Item asItem() {
        return get();
    }
}

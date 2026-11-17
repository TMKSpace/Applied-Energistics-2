/*
 * Часть nfcompat. Шим net.neoforged.neoforge.registries.DeferredBlock для Fabric.
 * Сигнатуры повторяют NeoForge 26.3 (DeferredBlock реализует ItemLike через block.asItem()).
 */
package net.neoforged.neoforge.registries;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;

public class DeferredBlock<T extends Block> extends DeferredHolder<Block, T> implements ItemLike {

    /** Стек предмета-блока заданного размера. */
    public ItemStack toStack(int count) {
        ItemStack stack = asItem().getDefaultInstance();
        if (stack.isEmpty()) {
            throw new IllegalStateException("Block does not have a corresponding item: " + this.key());
        }
        stack.setCount(count);
        return stack;
    }

    public static <T extends Block> DeferredBlock<T> createBlock(Identifier key) {
        return createBlock(ResourceKey.create(Registries.BLOCK, key));
    }

    public static <T extends Block> DeferredBlock<T> createBlock(ResourceKey<Block> key) {
        return new DeferredBlock<>(key);
    }

    protected DeferredBlock(ResourceKey<Block> key) {
        super(key);
    }

    @Override
    public Item asItem() {
        return get().asItem();
    }
}

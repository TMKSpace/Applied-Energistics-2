/*
 * Часть nfcompat. Дополнительные параметры лут-контекста.
 *
 * В NeoForge набор шире; AE2 использует ровно один — стек, по которому сработал триггер.
 * На Fabric сам параметр в контекст никто не кладёт (это делает патч NeoForge),
 * поэтому значение в лут-таблице будет отсутствовать — см. комментарий в коде вызова.
 */
package net.neoforged.neoforge.common.loot;

import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.item.ItemInstance;

public final class NeoForgeLootContextParams {
    public static final ContextKey<ItemInstance> QUERIED_STACK = new ContextKey<>(
            Identifier.fromNamespaceAndPath("neoforge", "queried_stack"));

    private NeoForgeLootContextParams() {
    }
}

/*
 * Часть nfcompat. Утилиты списков.
 *
 * `NonNullList.copyOf` есть в NeoForge, но не в ванили — а AE2 им пользуется,
 * чтобы получить список без null-элементов.
 */
package nfcompat.util;

import java.util.Collection;

import net.minecraft.core.NonNullList;

public final class Lists {
    private Lists() {
    }

    public static <T> NonNullList<T> nonNullCopy(Collection<T> source) {
        NonNullList<T> list = NonNullList.create();
        list.addAll(source);
        return list;
    }
}

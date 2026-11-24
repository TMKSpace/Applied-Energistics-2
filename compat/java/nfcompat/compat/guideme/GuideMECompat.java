/*
 * Часть nfcompat. Замена неофорджевского `ArgumentTypeInfos.registerByClass`.
 *
 * NeoForge патчит ванильный `ArgumentTypeInfos`, добавляя статический
 * `registerByClass(Class, ArgumentTypeInfo)`, который кладёт пару в приватную карту
 * `BY_CLASS`. Добавить такой метод миксином нельзя (Mixin запрещает non-private static
 * в чужих классах), поэтому метод живёт здесь, а вызовы из GuideME перенаправляются
 * сюда же (см. `nfcompat.mixin.GuideMERegisterByClassMixin`).
 *
 * Логика ровно как в NeoForge: положить в карту и вернуть тот же info (GuideME дальше
 * делает checkcast в `SingletonArgumentInfo`).
 */
package nfcompat.compat.guideme;

import com.mojang.brigadier.arguments.ArgumentType;

import net.minecraft.commands.synchronization.ArgumentTypeInfo;

import nfcompat.mixin.guideme.ArgumentTypeInfosAccessor;

public final class GuideMECompat {

    private GuideMECompat() {
    }

    public static <A extends ArgumentType<?>, T extends ArgumentTypeInfo.Template<A>> ArgumentTypeInfo<A, T> registerByClass(
            Class<? extends A> clazz, ArgumentTypeInfo<A, T> info) {
        ArgumentTypeInfosAccessor.nfcompat$byClass().put(clazz, info);
        return info;
    }

    /** Сырой вариант для миксина-редиректа (стирание дженериков). */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static ArgumentTypeInfo registerByClassRaw(Class clazz, ArgumentTypeInfo info) {
        return registerByClass(clazz, info);
    }
}
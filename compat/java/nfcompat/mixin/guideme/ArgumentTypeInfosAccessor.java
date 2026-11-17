/*
 * Часть nfcompat. Доступ к приватной карте `ArgumentTypeInfos.BY_CLASS`.
 *
 * Зачем: NeoForge патчит `ArgumentTypeInfos`, добавляя public static
 * `registerByClass(Class, ArgumentTypeInfo)`. Миксин **не может** добавить в чужой
 * класс non-private static метод (Mixin падает с "contains non-private static method"),
 * поэтому сам метод живёт в `nfcompat.compat.GuideMECompat`, а карту мы достаём
 * аксессором.
 */
package nfcompat.mixin.guideme;

import java.util.Map;

import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ArgumentTypeInfos.class)
public interface ArgumentTypeInfosAccessor {

    @Accessor("BY_CLASS")
    static Map<Class<?>, ArgumentTypeInfo<?, ?>> nfcompat$byClass() {
        throw new AssertionError("mixin not applied");
    }
}

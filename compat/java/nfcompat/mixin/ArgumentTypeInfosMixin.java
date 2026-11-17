/*
 * Часть nfcompat. Возврат неофорджевского `ArgumentTypeInfos.registerByClass`.
 *
 * NeoForge патчит ванильный `ArgumentTypeInfos`, добавляя ПУБЛИЧНЫЙ статический
 * `registerByClass(Class, ArgumentTypeInfo)` — «положи тип аргумента команды в карту
 * `BY_CLASS`, не трогая реестр». Ваниль (26.3) умеет только приватный
 * `register(Registry, String, Class, ArgumentTypeInfo)`, которому нужен реестр.
 *
 * Кто зовёт: GuideME в своей статической инициализации —
 * `DR_ARGUMENT_TYPE_INFOS.register("guide_id", () -> registerByClass(GuideIdArgument.class,
 * SingletonArgumentInfo.contextFree(...)))`. Наш DeferredRegister регистрирует нетерпеливо,
 * поэтому вызов идёт прямо из `<clinit>` GuideME. На Fabric этого метода нет, и мы
 * получали NoSuchMethodError.
 *
 * Миксин ДОБАВЛЯЕТ метод в целевой класс (это разрешено: Mixin запрещает только
 * non-private статические ПОЛЯ). Метод не `@Shadow` и не инжектор, поэтому просто
 * вливается в `ArgumentTypeInfos` и виден любому коду, который его зовёт — включая
 * скомпилированный под NeoForge байткод GuideME.
 *
 * Так закрывается любая точка вызова (их две — лямбды `GuideME.lambda$new$0/1`), а не
 * только известные.
 */
package nfcompat.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.mojang.brigadier.arguments.ArgumentType;

import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;

@Mixin(ArgumentTypeInfos.class)
public abstract class ArgumentTypeInfosMixin {

    /** Ванильная карта `Class -> ArgumentTypeInfo`, приватная и статическая. */
    @Shadow
    private static Map<Class<?>, ArgumentTypeInfo<?, ?>> BY_CLASS;

    /**
     * Аналог неофорджевского патча: только сопоставление типа и его сериализатора.
     * Возвращает переданный info — GuideME делает на результате `checkcast`
     * в `SingletonArgumentInfo`, так что вернуть надо ровно его.
     */
    public static <A extends ArgumentType<?>, T extends ArgumentTypeInfo.Template<A>> ArgumentTypeInfo<A, T> registerByClass(
            Class<? extends A> clazz, ArgumentTypeInfo<A, T> info) {
        BY_CLASS.put(clazz, info);
        return info;
    }
}

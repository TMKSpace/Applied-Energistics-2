/*
 * ╨з╨░╤Б╤В╤М nfcompat. ╨Ч╨░╨╝╨╡╨╜╨░ ╨╜╨╡╨╛╤Д╨╛╤А╨┤╨╢╨╡╨▓╤Б╨║╨╛╨│╨╛ `ArgumentTypeInfos.registerByClass`.
 *
 * NeoForge ╨┐╨░╤В╤З╨╕╤В ╨▓╨░╨╜╨╕╨╗╤М╨╜╤Л╨╣ `ArgumentTypeInfos`, ╨┤╨╛╨▒╨░╨▓╨╗╤П╤П ╤Б╤В╨░╤В╨╕╤З╨╡╤Б╨║╨╕╨╣
 * `registerByClass(Class, ArgumentTypeInfo)`, ╨║╨╛╤В╨╛╤А╤Л╨╣ ╨║╨╗╨░╨┤╤С╤В ╨┐╨░╤А╤Г ╨▓ ╨┐╤А╨╕╨▓╨░╤В╨╜╤Г╤О ╨║╨░╤А╤В╤Г
 * `BY_CLASS`. ╨Ф╨╛╨▒╨░╨▓╨╕╤В╤М ╤В╨░╨║╨╛╨╣ ╨╝╨╡╤В╨╛╨┤ ╨╝╨╕╨║╤Б╨╕╨╜╨╛╨╝ ╨╜╨╡╨╗╤М╨╖╤П (Mixin ╨╖╨░╨┐╤А╨╡╤Й╨░╨╡╤В non-private static
 * ╨▓ ╤З╤Г╨╢╨╕╤Е ╨║╨╗╨░╤Б╤Б╨░╤Е), ╨┐╨╛╤Н╤В╨╛╨╝╤Г ╨╝╨╡╤В╨╛╨┤ ╨╢╨╕╨▓╤С╤В ╨╖╨┤╨╡╤Б╤М, ╨░ ╨▓╤Л╨╖╨╛╨▓╤Л ╨╕╨╖ GuideME ╨┐╨╡╤А╨╡╨╜╨░╨┐╤А╨░╨▓╨╗╤П╤О╤В╤Б╤П
 * ╤Б╤О╨┤╨░ ╨╢╨╡ (╤Б╨╝. `nfcompat.mixin.GuideMERegisterByClassMixin`).
 *
 * ╨Ы╨╛╨│╨╕╨║╨░ ╤А╨╛╨▓╨╜╨╛ ╨║╨░╨║ ╨▓ NeoForge: ╨┐╨╛╨╗╨╛╨╢╨╕╤В╤М ╨▓ ╨║╨░╤А╤В╤Г ╨╕ ╨▓╨╡╤А╨╜╤Г╤В╤М ╤В╨╛╤В ╨╢╨╡ info (GuideME ╨┤╨░╨╗╤М╤И╨╡
 * ╨┤╨╡╨╗╨░╨╡╤В checkcast ╨▓ `SingletonArgumentInfo`).
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

    /** ╨б╤Л╤А╨╛╨╣ ╨▓╨░╤А╨╕╨░╨╜╤В ╨┤╨╗╤П ╨╝╨╕╨║╤Б╨╕╨╜╨░-╤А╨╡╨┤╨╕╤А╨╡╨║╤В╨░ (╤Б╤В╨╕╤А╨░╨╜╨╕╨╡ ╨┤╨╢╨╡╨╜╨╡╤А╨╕╨║╨╛╨▓). */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static ArgumentTypeInfo registerByClassRaw(Class clazz, ArgumentTypeInfo info) {
        return registerByClass(clazz, info);
    }
}
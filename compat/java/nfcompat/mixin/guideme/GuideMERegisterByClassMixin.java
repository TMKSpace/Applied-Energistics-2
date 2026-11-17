/*
 * ╨з╨░╤Б╤В╤М nfcompat. ╨Ь╨╛╤Б╤В ╨┤╨╗╤П GuideME: `ArgumentTypeInfos.registerByClass` -> ╨╜╨░╤И ╨║╨╛╨╝╨┐╨░╤В.
 *
 * GuideME тАФ ╤Б╨▒╨╛╤А╨║╨░ ╨┐╨╛╨┤ NeoForge, ╨╡╤С `GuideME.<init>` ╨▓ ╨┤╨▓╤Г╤Е ╨╗╤П╨╝╨▒╨┤╨░╤Е ╤А╨╡╨│╨╕╤Б╤В╤А╨╕╤А╤Г╨╡╤В
 * ╨░╤А╨│╤Г╨╝╨╡╨╜╤В╤Л ╨║╨╛╨╝╨░╨╜╨┤ ╤З╨╡╤А╨╡╨╖ ╨╜╨╡╨╛╤Д╨╛╤А╨┤╨╢╨╡╨▓╤Б╨║╨╕╨╣ `ArgumentTypeInfos.registerByClass`. ╨в╨░╨║╨╛╨│╨╛
 * ╨╝╨╡╤В╨╛╨┤╨░ ╨▓ ╨▓╨░╨╜╨╕╨╗╨╕ ╨╜╨╡╤В, ╨░ ╨┤╨╛╨▒╨░╨▓╨╕╤В╤М ╨╡╨│╨╛ ╨╝╨╕╨║╤Б╨╕╨╜╨╛╨╝ ╨╜╨╡╨╗╤М╨╖╤П (non-private static).
 * ╨Я╨╛╤Н╤В╨╛╨╝╤Г ╨┐╨╡╤А╨╡╨╜╨░╨┐╤А╨░╨▓╨╗╤П╨╡╨╝ ╨╛╨▒╨░ ╨▓╤Л╨╖╨╛╨▓╨░ ╨╜╨░ `nfcompat.compat.GuideMECompat`, ╨║╨╛╤В╨╛╤А╤Л╨╣ ╨┤╨╡╨╗╨░╨╡╤В
 * ╤В╨╛ ╨╢╨╡ ╤Б╨░╨╝╨╛╨╡ (╨║╨╗╨░╨┤╤С╤В ╨▓ ╨┐╤А╨╕╨▓╨░╤В╨╜╤Г╤О ╨║╨░╤А╤В╤Г ╨╕ ╨▓╨╛╨╖╨▓╤А╨░╤Й╨░╨╡╤В info).
 */
package nfcompat.mixin.guideme;

import net.minecraft.commands.synchronization.ArgumentTypeInfo;

import nfcompat.compat.guideme.GuideMECompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "guideme.internal.GuideME")
@SuppressWarnings({"rawtypes", "unchecked"})
public class GuideMERegisterByClassMixin {

    @Redirect(method = "lambda$new$0", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/commands/synchronization/ArgumentTypeInfos;registerByClass(Ljava/lang/Class;Lnet/minecraft/commands/synchronization/ArgumentTypeInfo;)Lnet/minecraft/commands/synchronization/ArgumentTypeInfo;"))
    private static ArgumentTypeInfo<?, ?> nfcompat$registerByClass0(Class<?> clazz, ArgumentTypeInfo<?, ?> info) {
        return GuideMECompat.registerByClassRaw(clazz, info);
    }

    @Redirect(method = "lambda$new$1", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/commands/synchronization/ArgumentTypeInfos;registerByClass(Ljava/lang/Class;Lnet/minecraft/commands/synchronization/ArgumentTypeInfo;)Lnet/minecraft/commands/synchronization/ArgumentTypeInfo;"))
    private static ArgumentTypeInfo<?, ?> nfcompat$registerByClass1(Class<?> clazz, ArgumentTypeInfo<?, ?> info) {
        return GuideMECompat.registerByClassRaw(clazz, info);
    }
}
/*
 * ╨з╨░╤Б╤В╤М nfcompat. ╨е╨╛╤В╨║╨╡╨╣ GuideME ╨╕ ╨╜╨╡╨╛╤Д╨╛╤А╨┤╨╢╨╡╨▓╤Б╨║╨╕╨╣ ╨║╨╛╨╜╤Б╤В╤А╤Г╨║╤В╨╛╤А `KeyMapping`.
 *
 * `GuideMEClient.<init>` ╨▓ ╨║╨╛╨╜╤Ж╨╡ ╨▓╤Л╨╖╤Л╨▓╨░╨╡╤В `OpenGuideHotkey.init()`, ╨░ `<clinit>`
 * `OpenGuideHotkey` ╤Б╨╛╨╖╨┤╨░╤С╤В ╨║╨╗╨░╨▓╨╕╤И╤Г ╤В╨░╨║:
 *
 *   new KeyMapping("key.guideme.guide", KeyConflictContext.GUI, InputConstants.Type.KEYBOARD,
 *                  GLFW_KEY_G, GuideMEClient.KEYBIND_CATEGORY)
 *
 * ╨Я╤П╤В╨╕╨░╤А╨│╤Г╨╝╨╡╨╜╤В╨╜╤Л╨╣ ╨║╨╛╨╜╤Б╤В╤А╤Г╨║╤В╨╛╤А ╤Б `IKeyConflictContext` тАФ ╤Н╤В╨╛ ╨┐╨░╤В╤З NeoForge ╨║ ╨▓╨░╨╜╨╕╨╗╨╕
 * (╨▓ ╨▓╨░╨╜╨╕╨╗╨╕ ╨╡╤Б╤В╤М ╤В╨╛╨╗╤М╨║╨╛ `(String, Type, int, Category)`). ╨Ъ╨╛╨╜╤Б╤В╤А╤Г╨║╤В╨╛╤А╤Л Mixin ╨┤╨╛╨▒╨░╨▓╨╗╤П╤В╤М
 * ╨╜╨╡ ╤Г╨╝╨╡╨╡╤В, ╨░ ╨┐╨╡╤А╨╡╨┐╨╕╤Б╤Л╨▓╨░╤В╤М `KeyMapping` ╤Ж╨╡╨╗╨╕╨║╨╛╨╝ ╤А╨░╨┤╨╕ ╨║╨╛╨╜╤В╨╡╨║╤Б╤В╨░ ╨║╨╛╨╜╤Д╨╗╨╕╨║╤В╨╛╨▓ ╨╜╨╡ ╤Б╤В╨╛╨╕╤В:
 * ╨╜╨░ Fabric ╨║╨╛╨╜╤В╨╡╨║╤Б╤В ╨▓╨╗╨╕╤П╨╡╤В ╤В╨╛╨╗╤М╨║╨╛ ╨╜╨░ ╨┐╨╛╨┤╤Б╨▓╨╡╤В╨║╤Г ╨║╨╛╨╜╤Д╨╗╨╕╨║╤В╨╛╨▓ ╨▓ ╨╜╨░╤Б╤В╤А╨╛╨╣╨║╨░╤Е ╤Г╨┐╤А╨░╨▓╨╗╨╡╨╜╨╕╤П.
 *
 * ╨Я╨╛╤Н╤В╨╛╨╝╤Г ╨┐╨╡╤А╨╡╤Е╨▓╨░╤В╤Л╨▓╨░╨╡╨╝ ╤Б╨░╨╝ ╨▓╤Л╨╖╨╛╨▓ `OpenGuideHotkey.init()` ╨╕ ╨┤╨╡╨╗╨░╨╡╨╝ ╨╡╨│╨╛ ╤З╨╡╤А╨╡╨╖
 * `GuideMEBootstrap.initHotkey()`: ╤В╨░╨╝ try/catch, ╤В╨░╨║ ╤З╤В╨╛ NoSuchMethodError ╨╜╨╡ ╤Г╨╜╨╛╤Б╨╕╤В
 * ╤Б ╤Б╨╛╨▒╨╛╨╣ ╨╛╤Б╤В╨░╤В╨╛╨║ ╨║╨╛╨╜╤Б╤В╤А╤Г╨║╤В╨╛╤А╨░ `GuideMEClient` (╨┐╤А╨╛╨║╤Б╨╕, ╨║╨╛╨╜╤Д╨╕╨│, reload-╤Б╨╗╤Г╤И╨░╤В╨╡╨╗╤М).
 * ╨Ъ╨╛╨│╨┤╨░/╨╡╤Б╨╗╨╕ ╨┐╨╛╤П╨▓╨╕╤В╤Б╤П ╤И╨╕╨╝ ╨║╨╛╨╜╤Б╤В╤А╤Г╨║╤В╨╛╤А╨░ `KeyMapping`, ╤Е╨╛╤В╨║╨╡╨╣ ╨╖╨░╤А╨░╨▒╨╛╤В╨░╨╡╤В ╤Б╨░╨╝ тАФ
 * ╨╝╨╡╨╜╤П╤В╤М ╨╖╨┤╨╡╤Б╤М ╨╜╨╕╤З╨╡╨│╨╛ ╨╜╨╡ ╨┐╤А╨╕╨┤╤С╤В╤Б╤П.
 *
 * `require = 0`: ╨╡╤Б╨╗╨╕ ╨┐╨╛╤А╤П╨┤╨╛╨║ ╨▓╤Л╨╖╨╛╨▓╨╛╨▓ ╨▓ GuideME ╨┐╨╛╨╝╨╡╨╜╤П╨╡╤В╤Б╤П, ╨╝╨╕╨║╤Б╨╕╨╜ ╨┐╤А╨╛╨╝╨╛╨╗╤З╨╕╤В, ╨░ ╨╜╨╡
 * ╤Г╤А╨╛╨╜╨╕╤В ╨╖╨░╨│╤А╤Г╨╖╨║╤Г ╨║╨╗╨░╤Б╤Б╨░ (╨╕╨╜╨░╤З╨╡ `SimpleRenderContext` ╤Б╨╜╨╛╨▓╨░ ╨┐╨╛╨╗╤Г╤З╨╕╨╗ ╨▒╤Л
 * NoClassDefFoundError ╨╜╨░ ╨╖╨░╨╝╨╛╤А╨╛╨╢╨╡╨╜╨╜╨╛╨╝ ╤А╨╡╨╡╤Б╤В╤А╨╡ тАФ ╤А╨╛╨▓╨╜╨╛ ╤В╨░ ╨┐╤А╨╛╨▒╨╗╨╡╨╝╨░, ╨║╨╛╤В╨╛╤А╤Г╤О ╤З╨╕╨╜╨╕╨╝).
 */
package nfcompat.mixin.guideme;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import nfcompat.compat.guideme.GuideMEBootstrap;

@Mixin(targets = "guideme.internal.GuideMEClient")
public class GuideMEClientMixin {

    @Redirect(method = "<init>", require = 0,
            at = @At(value = "INVOKE", target = "Lguideme/internal/hotkey/OpenGuideHotkey;init()V"))
    private static void nfcompat$initHotkey() {
        GuideMEBootstrap.initHotkey();
    }
}
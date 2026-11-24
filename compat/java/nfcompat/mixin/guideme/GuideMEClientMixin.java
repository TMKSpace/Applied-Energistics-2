/*
 * Часть nfcompat. Хоткей GuideME и неофорджевский конструктор `KeyMapping`.
 *
 * `GuideMEClient.<init>` в конце вызывает `OpenGuideHotkey.init()`, а `<clinit>`
 * `OpenGuideHotkey` создаёт клавишу так:
 *
 *   new KeyMapping("key.guideme.guide", KeyConflictContext.GUI, InputConstants.Type.KEYBOARD,
 *                  GLFW_KEY_G, GuideMEClient.KEYBIND_CATEGORY)
 *
 * Пятиаргументный конструктор с `IKeyConflictContext` — это патч NeoForge к ванили
 * (в ванили есть только `(String, Type, int, Category)`). Конструкторы Mixin добавлять
 * не умеет, а переписывать `KeyMapping` целиком ради контекста конфликтов не стоит:
 * на Fabric контекст влияет только на подсветку конфликтов в настройках управления.
 *
 * Поэтому перехватываем сам вызов `OpenGuideHotkey.init()` и делаем его через
 * `GuideMEBootstrap.initHotkey()`: там try/catch, так что NoSuchMethodError не уносит
 * с собой остаток конструктора `GuideMEClient` (прокси, конфиг, reload-слушатель).
 * Когда/если появится шим конструктора `KeyMapping`, хоткей заработает сам —
 * менять здесь ничего не придётся.
 *
 * `require = 0`: если порядок вызовов в GuideME поменяется, миксин промолчит, а не
 * уронит загрузку класса (иначе `SimpleRenderContext` снова получил бы
 * NoClassDefFoundError на замороженном реестре — ровно та проблема, которую чиним).
 */
package nfcompat.mixin.guideme;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.KeyMapping;

import nfcompat.compat.guideme.GuideMEBootstrap;
import nfcompat.compat.guideme.GuideMEHotkeyCompat;

@Mixin(targets = "guideme.internal.GuideMEClient")
public class GuideMEClientMixin {

    @Redirect(method = "<init>", require = 0,
            at = @At(value = "INVOKE", target = "Lguideme/internal/hotkey/OpenGuideHotkey;init()V"))
    private static void nfcompat$initHotkey() {
        GuideMEBootstrap.initHotkey();
    }

    /**
     * `registerHotkeys` зовёт `OpenGuideHotkey.getHotkey()` — то есть грузит класс, который
     * на NeoForge инициализируется 5-аргументным `KeyMapping`. Если `<clinit>` всё же упал
     * (например, GuideME сменит конструктор), это давало `NoClassDefFoundError` уже на
     * инициализации `Options` и роняло клиент. Отдаём клавишу из своего холдера: она либо та,
     * что создал `GuideMEHotkeyMixin`, либо наша ванильная замена.
     */
    @Redirect(method = "registerHotkeys", require = 0,
            at = @At(value = "INVOKE",
                    target = "Lguideme/internal/hotkey/OpenGuideHotkey;getHotkey()Lnet/minecraft/client/KeyMapping;"))
    private static KeyMapping nfcompat$hotkey() {
        return GuideMEHotkeyCompat.mapping();
    }
}

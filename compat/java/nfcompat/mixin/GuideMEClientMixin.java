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
package nfcompat.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import nfcompat.client.GuideMEBootstrap;

@Mixin(targets = "guideme.internal.GuideMEClient")
public class GuideMEClientMixin {

    @Redirect(method = "<init>", require = 0,
            at = @At(value = "INVOKE", target = "Lguideme/internal/hotkey/OpenGuideHotkey;init()V"))
    private static void nfcompat$initHotkey() {
        GuideMEBootstrap.initHotkey();
    }
}

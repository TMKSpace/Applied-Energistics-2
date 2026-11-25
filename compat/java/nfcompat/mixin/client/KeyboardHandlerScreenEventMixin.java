/*
 * Часть nfcompat. Постинг `ScreenEvent.KeyPressed` (`Pre`/`Post`).
 *
 * ЗАЧЕМ. `extendedae_plus` вешает на `KeyPressed.Pre` подстановку поиска по JEI
 * (`InputEvents#onKeyPressedPre`, `CtrlQPatternKeyHandler#onScreenKeyPressed`): ловит клавишу,
 * пишет текст в поле поиска AE2 и делает `setCanceled(true)`, чтобы сама клавиша до экрана не
 * дошла. Без постинга событие не приходит и фича молчит.
 *
 * ГДЕ. NeoForge 26.3 патчит ровно это место (`patches/net/minecraft/client/KeyboardHandler.java.patch`):
 * внутри `KeyboardHandler#keyPress` вызов `Screen#keyPressed(KeyEvent)` оборачивается в
 * `onScreenKeyPressedPre(screen, event) || screen.keyPressed(event) || onScreenKeyPressedPost(...)`.
 * Мы воспроизводим ту же семантику через `@WrapOperation` вокруг этого единственного вызова:
 *  - `Pre` постится ДО экрана, отмена -> экран клавишу не видит, считаем «обработано» (как
 *    замыкание `||` у NeoForge);
 *  - экран не обработал -> постим `Post`, его отмена тоже означает «обработано».
 *
 * ПОЧЕМУ `@WrapOperation`, А НЕ `@Inject` В `Screen#keyPressed`. Экран — не одно место: клавишу
 * может обработать любой `GuiEventListener`, а NeoForge постит событие один раз на уровне
 * обработчика ввода. Обёртка вызова сохраняет ровно один постинг и правильный порядок.
 * Требование MixinExtras: `Operation` — первый параметр, дальше аргументы вызова.
 *
 * ХРУПКОСТЬ. Держится на имени `keyPress` и дескрипторе
 * `Screen#keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z`. При обновлении MC падаем на
 * старте (`defaultRequire = 1`), а не теряем событие молча. Сам `KeyEvent` в 26.3 — запись
 * `(key, keycode, modifiers)`, в 1.21.x был `(key, scancode, modifiers)`: если MC снова
 * переставит поля, `getKeyCode()/getScanCode()` в `ScreenEvent` станут врать — это место
 * перепроверить первым.
 */
package nfcompat.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerScreenEventMixin {

    @WrapOperation(method = "keyPress",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/Screen;keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z"))
    private boolean nfcompat$postScreenKeyPressed(Screen screen, KeyEvent event, Operation<Boolean> original) {
        var pre = new ScreenEvent.KeyPressed.Pre(screen, event);
        NeoForge.EVENT_BUS.post(pre);
        if (pre.isCanceled()) {
            // NeoForge: отменённый Pre замыкает `||` -> клавиша считается обработанной,
            // экран её не получает.
            return true;
        }

        boolean handled = original.call(screen, event);
        if (!handled) {
            // Post постится только если экран клавишу не обработал (как в ClientHooks).
            var post = new ScreenEvent.KeyPressed.Post(screen, event);
            NeoForge.EVENT_BUS.post(post);
            handled = post.isCanceled();
        }
        return handled;
    }
}

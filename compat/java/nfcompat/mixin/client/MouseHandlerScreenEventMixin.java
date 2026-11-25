/*
 * Часть nfcompat. Постинг `ScreenEvent.MouseButtonPressed` (`Pre`/`Post`).
 *
 * ЗАЧЕМ. `extendedae_plus` на `MouseButtonPressed.Pre` смотрит среднюю кнопку по JEI-ингредиенту
 * под курсором и открывает «крафт из JEI» (`InputEvents#onMouseButtonPre`), после чего делает
 * `setCanceled(true)`. Ему нужны `getButton()`, `getMouseX()`, `getMouseY()`.
 *
 * ГДЕ. NeoForge 26.3 патчит `MouseHandler#onButton`
 * (`patches/net/minecraft/client/MouseHandler.java.patch`): вызов `Screen#mouseClicked(MouseButtonEvent, boolean)`
 * оборачивается в `onScreenMouseClickedPre(...)` / `screen.mouseClicked(...)` /
 * `onScreenMouseClickedPost(...)`. Повторяем через `@WrapOperation` на этом единственном вызове:
 *  - `Pre` постится ДО экрана; отмена -> экран клик не видит, клик считается обработанным;
 *  - `Pre` не отменён -> зовём экран, затем постим `Post` с фактическим `handled`;
 *    `Post.setResult(...)` может переопределить итог (см. `getClickResult()`).
 *
 * ПОЧЕМУ НЕ `@Inject` В `Screen#mouseClicked`. В 26.3 `mouseClicked` — default-метод
 * `ContainerEventHandler`, а не метод `Screen`; миксин в интерфейсный default-метод тянет за собой
 * ограничения Mixin на интерфейсы (нельзя добавлять не-default методы/поля). Обёртка вызова в
 * `MouseHandler` — та же точка, что у NeoForge, и без этих ограничений.
 *
 * ХРУПКОСТЬ. Держится на имени `onButton` и дескрипторе
 * `Screen#mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z`. При обновлении MC
 * падаем на старте (`defaultRequire = 1`).
 */
package nfcompat.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MouseHandler.class)
public class MouseHandlerScreenEventMixin {

    @WrapOperation(method = "onButton",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/Screen;mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z"))
    private boolean nfcompat$postScreenMouseClicked(Operation<Boolean> original, Screen screen,
            MouseButtonEvent event, boolean doubleClick) {
        var pre = new ScreenEvent.MouseButtonPressed.Pre(screen, event, doubleClick);
        NeoForge.EVENT_BUS.post(pre);
        if (pre.isCanceled()) {
            // NeoForge: отменённый Pre = экран клик не обрабатывает, но клик «съеден».
            return true;
        }

        boolean handled = original.call(screen, event, doubleClick);
        var post = new ScreenEvent.MouseButtonPressed.Post(screen, event, doubleClick, handled);
        NeoForge.EVENT_BUS.post(post);
        return post.getClickResult();
    }
}

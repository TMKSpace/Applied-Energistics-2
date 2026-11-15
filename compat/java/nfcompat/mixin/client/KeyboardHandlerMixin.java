/*
 * Часть nfcompat. Хук ввода с клавиатуры.
 *
 * NeoForge после обработки нажатия в `KeyboardHandler#keyPress` постит
 * `InputEvent.Key`. AE2 слушает его, чтобы отслеживать зажатую клавишу
 * «противоположной установки части» (Ctrl): состояние уходит на сервер
 * отдельным пакетом, а на сервере от него зависит выбор стороны/facade.
 */
package nfcompat.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {

    @Inject(method = "keyPress", at = @At("RETURN"))
    private void nfcompat$postKeyInput(long window, int action, KeyEvent event, CallbackInfo ci) {
        NeoForge.EVENT_BUS.post(new InputEvent.Key(event, action));
    }
}

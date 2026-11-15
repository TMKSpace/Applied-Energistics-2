/*
 * Часть nfcompat. Хук прокрутки колеса мыши.
 *
 * NeoForge постит `InputEvent.MouseScrollingEvent` в ветке `MouseHandler#onScroll`,
 * которая работает только когда не открыт ни экран, ни оверлей и есть игрок.
 * AE2 ловит его, чтобы отдать прокрутку портативной ячейке в руке
 * (шифт + колесо), и отменяет ванильную обработку.
 *
 * Отмену эмулируем через `ci.cancel()`: NeoForge в этом случае просто выходит
 * из метода, то есть до ванильной прокрутки дело не доходит.
 */
package nfcompat.mixin.client;

import org.joml.Vector2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Shadow
    public boolean isLeftPressed() {
        throw new AssertionError();
    }

    @Shadow
    public boolean isMiddlePressed() {
        throw new AssertionError();
    }

    @Shadow
    public boolean isRightPressed() {
        throw new AssertionError();
    }

    @Shadow
    public double xpos() {
        throw new AssertionError();
    }

    @Shadow
    public double ypos() {
        throw new AssertionError();
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void nfcompat$postMouseScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        var mc = Minecraft.getInstance();
        if (mc.gui.screen() != null || mc.gui.overlay() != null || mc.player == null) {
            return;
        }

        // Накопленную прокрутку не считаем: AE2 смотрит только на знак дельты.
        var event = new InputEvent.MouseScrollingEvent(xOffset, yOffset, new Vector2i(0, 0),
                isLeftPressed(), isMiddlePressed(), isRightPressed(), xpos(), ypos());
        NeoForge.EVENT_BUS.post(event);

        if (event.isCanceled()) {
            ci.cancel();
        }
    }
}

/*
 * Часть nfcompat (GuideME). `Blitter#copy()` не переносит `blending`/`transform`.
 *
 * GuideME копирует блиттер и потом меняет только цвет/рамку, ожидая, что флаги (особенно
 * `blending=false`, которым включается непрозрачный пайплайн) сохранятся. На деле `copy()`
 * создаёт новый экземпляр и теряет эти два поля, поэтому копия уезжает в `GUI_TEXTURED`
 * с блендингом - визуально «просвечивающие» панели/картинки.
 *
 * Чиним снаружи, не трогая джар: после `copy()` копируем флаги публичными сеттерами самого
 * GuideME (`blending(boolean)`, `transform(TextureTransform)`), поэтому лезем только в
 * приватные поля через `@Shadow`, а менять чужой код не приходится.
 *
 * Хрупкость: держится на именах полей `blending`/`transform` и методе `copy()`.
 * При обновлении GuideME падает на старте (миксин обязательный), а не тихо.
 */
package nfcompat.mixin.guideme;

import guideme.internal.util.Blitter;
import guideme.internal.util.TextureTransform;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "guideme.internal.util.Blitter")
public abstract class BlitterCopyMixin {

    @Shadow
    private boolean blending;

    @Shadow
    private TextureTransform transform;

    @Inject(method = "copy", at = @At("RETURN"))
    private void nfcompat$copyFlags(CallbackInfoReturnable<Blitter> cir) {
        var copy = cir.getReturnValue();
        if (copy == null || copy == (Object) this) {
            return;
        }

        copy.blending(this.blending);
        if (this.transform != null) {
            copy.transform(this.transform);
        }
    }
}

/*
 * Часть nfcompat (GuideME). Иконки жидкостей рисовались плоским синим квадратом.
 *
 * GuideME рисует спрайт жидкости через `Blitter` с `.blending(false)`, из-за чего берётся
 * НЕ штатный `RenderPipelines.GUI_TEXTURED`, а свой пайплайн `Blitter.GUI_TEXTURED_OPAQUE`,
 * собранный через неофорджевский `RenderPipeline.toBuilder()` (метода в ванили нет — его
 * добавляем мы, `nfcompat.mixin.guideme.RenderPipelineToBuilderMixin`). Копия пайплайна
 * получается неполной (в `RenderPipeline.Builder` нельзя перенести string-valued shader
 * defines), и в результате спрайт рисуется без текстуры — остаётся только тинт:
 *
 *     иконка воды = ровный синий квадрат вместо текстуры воды
 *
 * Поэтому в `Blitter.blit` принудительно читаем `blending` как true: уходит стандартный
 * `GUI_TEXTURED`, который в порте работает (им же рисуются все остальные спрайты гайда).
 * Прозрачность у текстур жидкостей и так есть, а «непрозрачный слот» в гайде не принципиален.
 *
 * Это костыль вокруг нашего же `toBuilder`; если однажды сделаем точную копию пайплайна
 * (или NeoForge-патч `toBuilder` перестанет быть нужен) — миксин можно снять.
 */
package nfcompat.mixin.guideme;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "guideme.internal.util.Blitter")
public class BlitterBlendingMixin {

    @Redirect(method = "blit", require = 0,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lguideme/internal/util/Blitter;blending:Z"))
    private boolean nfcompat$forceTexturedPipeline(guideme.internal.util.Blitter self) {
        return true;
    }
}

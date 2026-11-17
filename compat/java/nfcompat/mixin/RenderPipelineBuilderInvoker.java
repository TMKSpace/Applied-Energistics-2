/*
 * Часть nfcompat. Доступ к приватному конструктору `RenderPipeline.Builder`.
 *
 * Зачем: NeoForge патчит `RenderPipeline`, добавляя `toBuilder()`. У GuideME (сборка под
 * NeoForge) этот метод используется в пяти местах, поэтому мы добавляем его сами
 * (см. `RenderPipelineToBuilderMixin`), а для этого нужен экземпляр Builder'а, чей
 * конструктор приватный.
 */
package nfcompat.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "com.mojang.renderpearl.api.pipeline.RenderPipeline$Builder")
public interface RenderPipelineBuilderInvoker {

    @Invoker("<init>")
    static RenderPipeline.Builder nfcompat$newBuilder() {
        throw new AssertionError("mixin not applied");
    }
}

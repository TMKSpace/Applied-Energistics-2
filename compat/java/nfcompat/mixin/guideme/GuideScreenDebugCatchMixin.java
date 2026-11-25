/*
 * Часть nfcompat (GuideME). Страховка для обхода гайдов в dev-ране.
 *
 * Пока порт не закрыт, почти каждый неофорджевский член, которого не хватает GuideME, роняет
 * КЛИЕНТ целиком: исключение летит из `GuideScreen.scaledExtractRenderState` через
 * `DocumentScreen.renderDocument`, а выше его никто не ловит. Из-за этого нельзя просто
 * пройтись по всем страницам гайда и собрать список проблем — на каждой нужен перезапуск.
 *
 * Поэтому в dev-окружении оборачиваем вызов `renderDocument` в try/catch: страница рисуется
 * пустой, в лог уходит одна строка на уникальную ошибку (с типом, сообщением и стеком).
 *
 * ВАЖНО. Это отладочный костыль, а не поведение порта:
 *  - включён только в dev (FabricLoader.isDevelopmentEnvironment()) и выключается
 *    системным свойством `-Dnfcompat.guide.debugCatch=false`;
 *  - в релизной сборке выключен, то есть ошибки снова валят клиент;
 *  - перед релизом проверить, что флаг действительно off (см. TODO).
 */
package nfcompat.mixin.guideme;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.fabricmc.loader.api.FabricLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import guideme.render.RenderContext;


@Mixin(targets = "guideme.internal.screen.GuideScreen")
public class GuideScreenDebugCatchMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("nfcompat/guideme");

    private static final boolean CATCH = Boolean.parseBoolean(System.getProperty(
            "nfcompat.guide.debugCatch",
            String.valueOf(FabricLoader.getInstance().isDevelopmentEnvironment())));

    private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();

    @WrapOperation(method = "scaledExtractRenderState", require = 0,
            at = @At(value = "INVOKE",
                    target = "Lguideme/internal/screen/DocumentScreen;renderDocument(Lguideme/render/RenderContext;)V"))
    private void nfcompat$renderDocumentSafely(RenderContext context, Operation<Void> original) {
        if (!CATCH) {
            original.call(context);
            return;
        }

        try {
            original.call(context);
        } catch (Throwable t) {
            var key = t.getClass().getName() + ": " + t.getMessage();
            if (SEEN.add(key)) {
                LOGGER.error("[nfcompat] страница гайда нарисована пустой (dev-страховка): {}", key, t);
            }
        }
    }
}

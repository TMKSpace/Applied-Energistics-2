/*
 * Часть nfcompat. Мост `RegisterPictureInPictureRenderersEvent`.
 *
 * NeoForge патчит конструктор `GameRenderer`: собирает список PiP-рендереров
 * (`List.of(...)` с ванильными) и передаёт третьим аргументом в `new GuiRenderer(...)`.
 * `GuiRenderer` строит по нему карту "класс состояния -> рендерер", поэтому докинуть
 * свой рендерер позже нельзя — только тут.
 *
 * AE2 регистрирует `FluidBlockPictureInPictureRenderer` (превью жидкости в списках
 * предметов JEI/REI). См. `nfcompat.client.PictureInPicture`.
 */
package nfcompat.mixin.client;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.GameRenderer;

import nfcompat.client.PictureInPicture;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;<init>(Lnet/minecraft/client/renderer/state/gui/GuiRenderState;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;Ljava/util/List;)V"), index = 2)
    private static List<PictureInPictureRenderer<?>> nfcompat$addPictureInPictureRenderers(
            List<PictureInPictureRenderer<?>> vanilla) {
        var extra = PictureInPicture.createAll();
        if (extra.isEmpty()) {
            return vanilla;
        }

        var all = new ArrayList<PictureInPictureRenderer<?>>(vanilla.size() + extra.size());
        all.addAll(vanilla);
        all.addAll(extra);
        return all;
    }
}

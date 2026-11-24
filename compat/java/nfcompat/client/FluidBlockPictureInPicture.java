/*
 * Часть nfcompat. Отправка PiP-состояния для превью жидкости (`FluidBlockRendering`).
 *
 * На NeoForge это тело живёт прямо в `FluidBlockRendering.render`: оно зовёт
 * `GuiGraphicsExtractor#peekScissorStack` и `#submitPictureInPictureRenderState` — методы,
 * которых в ванили нет. На Fabric их добавляет миксин
 * (`nfcompat.mixin.client.GuiGraphicsExtractorMixin`), а компилятор AE2 методы от миксина
 * не видит, поэтому тело продублировано здесь, а в `src` под `//#if FABRIC` остался один
 * вызов. Логика один в один как в NeoForge-ветке: тот же прямоугольник с запасом 16,
 * то же пересечение с текущей областью отсечения, то же `State`.
 *
 * `scissorArea` кладём в состояние как есть (`null`, если отсечения нет): `GuiRenderer`
 * через `PictureInPictureRenderer#prepare` сам решает, чем клипать.
 */
package nfcompat.client;

import org.joml.Matrix3x2f;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.world.level.material.Fluid;

import appeng.client.integrations.itemlists.FluidBlockPictureInPictureRenderer;

public final class FluidBlockPictureInPicture {

    private FluidBlockPictureInPicture() {
    }

    /**
     * Ставит в очередь отрисовки 3D-превью жидкости. Координаты — как у
     * `FluidBlockRendering.render`: слот `x, y` размером `width x height`.
     */
    public static void submit(GuiGraphicsExtractor guiGraphics, Fluid fluid, int x, int y, int width, int height) {
        var pip = (GuiGraphicsExtractorAccess) (Object) guiGraphics;

        var rect = new ScreenRectangle(x - 16, y - 16, width + 32, height + 32);
        var screenBounds = rect.transformMaxBounds(guiGraphics.pose());
        var scissorArea = pip.peekScissorStack();
        // Pre-apply scissor area
        screenBounds = scissorArea != null ? scissorArea.intersection(screenBounds) : screenBounds;
        pip.submitPictureInPictureRenderState(new FluidBlockPictureInPictureRenderer.State(
                new Matrix3x2f(guiGraphics.pose()),
                rect.left(),
                rect.top(),
                rect.right(),
                rect.bottom(),
                screenBounds,
                scissorArea,
                fluid));
    }
}

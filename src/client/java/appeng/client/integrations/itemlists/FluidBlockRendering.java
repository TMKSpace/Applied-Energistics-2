package appeng.client.integrations.itemlists;

import org.joml.Matrix3x2f;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.world.level.material.Fluid;

public final class FluidBlockRendering {
    private FluidBlockRendering() {
    }

    public static void render(GuiGraphicsExtractor guiGraphics, Fluid fluid, int x, int y, int width, int height) {

//#if FABRIC
        // nfcompat: `submitPictureInPictureRenderState`/`peekScissorStack` — методы NeoForge,
        // в ванили их нет. На Fabric их кладёт в класс миксин
        // (nfcompat.mixin.client.GuiGraphicsExtractorMixin), а компилятор AE2 их не видит,
        // поэтому тело ушло в шим: nfcompat.client.FluidBlockPictureInPicture.
        nfcompat.client.FluidBlockPictureInPicture.submit(guiGraphics, fluid, x, y, width, height);
        //#else
        var rect = new ScreenRectangle(x - 16, y - 16, width + 32, height + 32);
        var screenBounds = rect.transformMaxBounds(guiGraphics.pose());
        var scissorArea = guiGraphics.peekScissorStack();
        // Pre-apply scissor area
        screenBounds = scissorArea != null ? scissorArea.intersection(screenBounds) : screenBounds;
        guiGraphics.submitPictureInPictureRenderState(new FluidBlockPictureInPictureRenderer.State(
                new Matrix3x2f(guiGraphics.pose()),
                rect.left(),
                rect.top(),
                rect.right(),
                rect.bottom(),
                screenBounds,
                scissorArea,
                fluid));
        //#endif
    }

}

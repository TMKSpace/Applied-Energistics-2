/*
 * Часть nfcompat. Неофорджевские члены `GuiGraphicsExtractor`, которых ждёт GuideME.
 *
 * NeoForge патчит `GuiGraphicsExtractor`, добавляя два метода:
 *   - `ScreenRectangle peekScissorStack()` — текущая область отсечения;
 *   - `void submitGuiElementRenderState(GuiElementRenderState state)` — отдать готовый
 *     GUI-элемент в `GuiRenderState`.
 * GuideME (сборка под NeoForge) зовёт оба из `SimpleRenderContext.fillRect/blit`, то есть
 * на каждом кадре при отрисовке гайда и полосы статуса связи в терминале ME:
 *
 *     NoSuchMethodError: GuiGraphicsExtractor.peekScissorStack()
 *       at guideme.render.SimpleRenderContext.fillRect
 *       at appeng.client.gui.me.common.MEStorageScreen.renderLinkStatus
 *
 * Это instance-методы, поэтому их можно добавить миксином (в отличие от static, которые Mixin
 * в чужие классы добавлять не умеет). Внутри — прямое обращение к ванильным полям:
 * `scissorStack.peek()` и `guiRenderState.addGuiElement(...)`.
 *
 * Тип `ScissorStack` в ванили package-private, поэтому он открыт в `ae2.accesswidener`
 * (`accessible class` + `accessible method ... peek`). Без этого пришлось бы лазить рефлексией
 * на каждый вызов, а `fillRect` зовётся очень часто.
 */
package nfcompat.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin {

    @Shadow
    private GuiGraphicsExtractor.ScissorStack scissorStack;

    @Shadow
    private GuiRenderState guiRenderState;

    public ScreenRectangle peekScissorStack() {
        return scissorStack.peek();
    }

    public void submitGuiElementRenderState(GuiElementRenderState state) {
        guiRenderState.addGuiElement(state);
    }

    public void submitPictureInPictureRenderState(PictureInPictureRenderState state) {
        guiRenderState.addPicturesInPictureState(state);
    }
}

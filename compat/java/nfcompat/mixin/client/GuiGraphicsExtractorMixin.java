/*
 * Часть nfcompat. Неофорджевские члены `GuiGraphicsExtractor`, которых ждут GuideME и AE2.
 *
 * NeoForge патчит `GuiGraphicsExtractor`, добавляя три метода:
 *   - `ScreenRectangle peekScissorStack()` — текущая область отсечения;
 *   - `void submitGuiElementRenderState(GuiElementRenderState state)` — отдать готовый
 *     GUI-элемент в `GuiRenderState`.
 *   - `void submitPictureInPictureRenderState(PictureInPictureRenderState state)` — то же,
 *     но для 3D-сцен PiP (превью жидкости в JEI/REI).
 * GuideME (сборка под NeoForge) зовёт первые два из `SimpleRenderContext.fillRect/blit`, то есть
 * на каждом кадре при отрисовке гайда и полосы статуса связи в терминале ME:
 *
 *     NoSuchMethodError: GuiGraphicsExtractor.peekScissorStack()
 *       at guideme.render.SimpleRenderContext.fillRect
 *       at appeng.client.gui.me.common.MEStorageScreen.renderLinkStatus
 *
 * Третий нужен уже исходникам AE2: компилятор методов от миксина не видит, поэтому
 * `FluidBlockRendering` кастует экстрактор к `nfcompat.client.GuiGraphicsExtractorAccess`,
 * а реализацию берёт отсюда.
 *
 * Это instance-методы, поэтому их можно добавить миксином (в отличие от static, которые Mixin
 * в чужие классы добавлять не умеет). Внутри — прямое обращение к ванильным полям:
 * `scissorStack.peek()`, `guiRenderState.addGuiElement(...)` и
 * `guiRenderState.addPicturesInPictureState(...)`.
 *
 * Тип `ScissorStack` в ванили package-private, поэтому он открыт в `ae2.accesswidener`
 * (`accessible class` + `accessible method ... peek`). Без этого пришлось бы лазить рефлексией
 * на каждый вызов, а `fillRect` зовётся очень часто. Поле `guiRenderState` тоже открыто AW.
 */
package nfcompat.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import nfcompat.client.GuiGraphicsExtractorAccess;

@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin implements GuiGraphicsExtractorAccess {

    @Shadow
    private GuiGraphicsExtractor.ScissorStack scissorStack;

    @Shadow
    private GuiRenderState guiRenderState;

    @Override
    @Nullable
    public ScreenRectangle peekScissorStack() {
        return scissorStack.peek();
    }

    /** Не входит в `GuiGraphicsExtractorAccess`: у AE2 этот путь закрыт ванильным `blit`. */
    public void submitGuiElementRenderState(GuiElementRenderState state) {
        guiRenderState.addGuiElement(state);
    }

    @Override
    public void submitPictureInPictureRenderState(PictureInPictureRenderState state) {
        guiRenderState.addPicturesInPictureState(state);
    }
}

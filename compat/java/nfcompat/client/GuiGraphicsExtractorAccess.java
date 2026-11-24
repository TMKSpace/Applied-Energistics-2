/*
 * Часть nfcompat. Неофорджевские члены `GuiGraphicsExtractor`, которые нужны коду AE2.
 *
 * NeoForge патчит экстрактор, добавляя два метода, которых в ванили нет:
 *   - `ScreenRectangle peekScissorStack()` — текущая область отсечения;
 *   - `void submitPictureInPictureRenderState(PictureInPictureRenderState state)` — отдать
 *     готовое PiP-состояние в `GuiRenderState` (оттуда его забирает `GuiRenderer`).
 *
 * GuideME — готовая сборка под NeoForge, она зовёт их напрямую (`invokevirtual`), и ей
 * достаточно миксина. Исходники AE2 (`FluidBlockRendering`) компилируются здесь, а методы
 * добавляет миксин — Java-компилятор их не видит, поэтому нужен явный тип для каста:
 *
 *     ((GuiGraphicsExtractorAccess) (Object) guiGraphics).submitPictureInPictureRenderState(...)
 *
 * Реализация — `nfcompat.mixin.client.GuiGraphicsExtractorMixin` (там же `@Shadow`-поля
 * `scissorStack` и `guiRenderState`; в `ae2.accesswidener` открыт только тип
 * `GuiGraphicsExtractor$ScissorStack`, поле `guiRenderState` и `ScissorStack#peek`).
 */
package nfcompat.client;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;

import org.jetbrains.annotations.Nullable;

public interface GuiGraphicsExtractorAccess {

    /** NeoForge: {@code null}, если сейчас нет ни одной активной области отсечения. */
    @Nullable
    ScreenRectangle peekScissorStack();

    /** NeoForge: поставить PiP-состояние в очередь отрисовки GUI. */
    void submitPictureInPictureRenderState(PictureInPictureRenderState state);
}

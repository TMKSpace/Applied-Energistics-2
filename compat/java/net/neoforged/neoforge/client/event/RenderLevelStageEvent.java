/*
 * Часть nfcompat. Неофорджевский `RenderLevelStageEvent` (постинг стадий рендера мира).
 *
 * ЗАЧЕМ. `extendedae_plus` подписывается на `RenderLevelStageEvent.AfterWeather`
 * (`UltimateSuperAssemblerMatrixPreviewRenderer#onRenderLevelStage`) и рисует превью
 * структуры через `getLevelRenderer().collectPerFrameGizmos()`. Без класса слушатель
 * падал на резолве типа (`NoClassDefFoundError`), без постинга — превью не появлялось.
 *
 * СИГНАТУРЫ СВЕРЕНЫ С NeoForge 26.3.x
 * (`src/client/java/net/neoforged/neoforge/client/event/RenderLevelStageEvent.java`):
 *  - база: `getLevelRenderer()`, `getLevelRenderState()`, `getPoseStack()`,
 *    `getModelViewMatrix()`, `getRenderableSections()`, `protected getRenderPass()`;
 *  - конструктор: `(LevelRenderer, LevelRenderState, @Nullable PoseStack, Matrix4fc,
 *    Iterable<? extends IRenderableSection>, @Nullable RenderPass)`, `null`-стек
 *    подменяется свежим `PoseStack`;
 *  - подклассы: `AfterSky`, `AfterOpaqueBlocks`, `AfterOpaqueFeatures`,
 *    `AfterTranslucentFeatures`, `AfterTranslucentBlocks`, `AfterTranslucentParticles`,
 *    `AfterWeather`, `OitTranslucent`, `AfterLevel`.
 *
 * ЧЕГО ЗДЕСЬ НЕТ (и почему). В 26.3 у события НЕТ `getStage()`, `getProjectionMatrix()`,
 * `getCamera()`, `getFrustum()` и `getRenderTick()` — они были в 1.21.x и удалены вместе с
 * переездом на новый рендер-пайплайн (`LevelRenderState`/`RenderPass`). Аддоны под 26.3 их и
 * не зовут: из jar `extendedae_plus` нужен только `getLevelRenderer()`. Поэтому порт
 * повторяет 26.3 один-в-один, а не тащит старый API, которого в целевой версии нет.
 *
 * ЧТО РЕАЛЬНО ПОСТИТСЯ. Сейчас только `AfterWeather` (см.
 * `nfcompat/mixin/client/LevelRendererRenderStageMixin`) — этого достаточно измеренным аддонам.
 * `AfterSky` (лямбда в `addSkyPass`), `AfterOpaqueBlocks`/`AfterOpaqueFeatures` (`executeSolid`),
 * `AfterTranslucentFeatures`/`AfterTranslucentBlocks`/`AfterTranslucentParticles`
 * (`executeClassicTransparency`), `OitTranslucent` (`executeOit`) и `AfterLevel`
 * (`GameRenderer#renderLevel`) не постим: лишние точки в горячем пути рендера — лишний риск,
 * а спрос на них пока не измерен. Добавить — по одной `@Inject` на стадию.
 *
 * КЛАССЫ ВСЕ РАВНО ОБЪЯВЛЕНЫ ВСЕ: аддон может ссылаться на любой из них в
 * `Class`-константе/`instanceof`, и JVM обязана его найти. Непостенное событие —
 * молчаливая тишина у слушателя, а отсутствующий класс — падение.
 */
package net.neoforged.neoforge.client.event;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.commands.RenderPass;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.oit.OitStage;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.client.IRenderableSection;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4fc;

/**
 * Стадия рендера мира. Не отменяется (как и в NeoForge).
 */
public abstract class RenderLevelStageEvent extends Event {
    private final LevelRenderer levelRenderer;
    private final LevelRenderState levelRenderState;
    private final PoseStack poseStack;
    private final Matrix4fc modelViewMatrix;
    private final Iterable<? extends IRenderableSection> renderableSections;
    @Nullable
    private final RenderPass renderPass;

    public RenderLevelStageEvent(LevelRenderer levelRenderer, LevelRenderState levelRenderState,
            @Nullable PoseStack poseStack, Matrix4fc modelViewMatrix,
            Iterable<? extends IRenderableSection> renderableSections, @Nullable RenderPass renderPass) {
        this.levelRenderer = levelRenderer;
        this.levelRenderState = levelRenderState;
        // как в NeoForge: null-стек (стадии, не владеющие позой) подменяется пустым
        this.poseStack = poseStack != null ? poseStack : new PoseStack();
        this.modelViewMatrix = modelViewMatrix;
        this.renderableSections = renderableSections;
        this.renderPass = renderPass;
    }

    public LevelRenderer getLevelRenderer() {
        return levelRenderer;
    }

    public LevelRenderState getLevelRenderState() {
        return levelRenderState;
    }

    public PoseStack getPoseStack() {
        return poseStack;
    }

    public Matrix4fc getModelViewMatrix() {
        return modelViewMatrix;
    }

    public Iterable<? extends IRenderableSection> getRenderableSections() {
        return renderableSections;
    }

    /// {@return активный `RenderPass` стадии; закрывать его нельзя}
    ///
    /// @throws NullPointerException у стадий без активного пасса (`AfterSky`, `AfterLevel`)
    protected RenderPass getRenderPass() {
        if (renderPass == null) {
            throw new NullPointerException("This render stage does not provide an active render pass");
        }
        return renderPass;
    }

    /** После отрисовки неба. У нас не постится. */
    public static class AfterSky extends RenderLevelStageEvent {
        public AfterSky(LevelRenderer levelRenderer, LevelRenderState levelRenderState, @Nullable PoseStack poseStack,
                Matrix4fc modelViewMatrix, Iterable<? extends IRenderableSection> renderableSections) {
            super(levelRenderer, levelRenderState, poseStack, modelViewMatrix, renderableSections, null);
        }
    }

    /** После непрозрачной геометрии чанков (`LevelRenderer#executeSolid`). */
    public static class AfterOpaqueBlocks extends RenderLevelStageEvent {
        public AfterOpaqueBlocks(LevelRenderer levelRenderer, LevelRenderState levelRenderState,
                @Nullable PoseStack poseStack, Matrix4fc modelViewMatrix,
                Iterable<? extends IRenderableSection> renderableSections, RenderPass renderPass) {
            super(levelRenderer, levelRenderState, poseStack, modelViewMatrix, renderableSections, renderPass);
        }

        @Override
        public RenderPass getRenderPass() {
            return super.getRenderPass();
        }
    }

    /** После непрозрачных фич (сущности, BE, частицы) — конец `LevelRenderer#executeSolid`. */
    public static class AfterOpaqueFeatures extends RenderLevelStageEvent {
        public AfterOpaqueFeatures(LevelRenderer levelRenderer, LevelRenderState levelRenderState,
                @Nullable PoseStack poseStack, Matrix4fc modelViewMatrix,
                Iterable<? extends IRenderableSection> renderableSections, RenderPass renderPass) {
            super(levelRenderer, levelRenderState, poseStack, modelViewMatrix, renderableSections, renderPass);
        }

        @Override
        public RenderPass getRenderPass() {
            return super.getRenderPass();
        }
    }

    /** После полупрозрачных фич (`LevelRenderer#executeClassicTransparency`). */
    public static class AfterTranslucentFeatures extends RenderLevelStageEvent {
        public AfterTranslucentFeatures(LevelRenderer levelRenderer, LevelRenderState levelRenderState,
                @Nullable PoseStack poseStack, Matrix4fc modelViewMatrix,
                Iterable<? extends IRenderableSection> renderableSections, RenderPass renderPass) {
            super(levelRenderer, levelRenderState, poseStack, modelViewMatrix, renderableSections, renderPass);
        }

        @Override
        public RenderPass getRenderPass() {
            return super.getRenderPass();
        }
    }

    /** После полупрозрачной геометрии чанков. */
    public static class AfterTranslucentBlocks extends RenderLevelStageEvent {
        public AfterTranslucentBlocks(LevelRenderer levelRenderer, LevelRenderState levelRenderState,
                @Nullable PoseStack poseStack, Matrix4fc modelViewMatrix,
                Iterable<? extends IRenderableSection> renderableSections, RenderPass renderPass) {
            super(levelRenderer, levelRenderState, poseStack, modelViewMatrix, renderableSections, renderPass);
        }

        @Override
        public RenderPass getRenderPass() {
            return super.getRenderPass();
        }
    }

    /** После полупрозрачных частиц. */
    public static class AfterTranslucentParticles extends RenderLevelStageEvent {
        public AfterTranslucentParticles(LevelRenderer levelRenderer, LevelRenderState levelRenderState,
                @Nullable PoseStack poseStack, Matrix4fc modelViewMatrix,
                Iterable<? extends IRenderableSection> renderableSections, RenderPass renderPass) {
            super(levelRenderer, levelRenderState, poseStack, modelViewMatrix, renderableSections, renderPass);
        }

        @Override
        public RenderPass getRenderPass() {
            return super.getRenderPass();
        }
    }

    /** После погоды, до world border. Это событие и нужно `extendedae_plus`. */
    public static class AfterWeather extends RenderLevelStageEvent {
        public AfterWeather(LevelRenderer levelRenderer, LevelRenderState levelRenderState, @Nullable PoseStack poseStack,
                Matrix4fc modelViewMatrix, Iterable<? extends IRenderableSection> renderableSections,
                RenderPass renderPass) {
            super(levelRenderer, levelRenderState, poseStack, modelViewMatrix, renderableSections, renderPass);
        }

        @Override
        public RenderPass getRenderPass() {
            return super.getRenderPass();
        }
    }

    /** OIT-стадия. У нас не постится (OIT-путь рендера не трогаем). */
    public static class OitTranslucent extends RenderLevelStageEvent {
        private final OitStage oitStage;

        public OitTranslucent(LevelRenderer levelRenderer, LevelRenderState levelRenderState, OitStage oitStage,
                @Nullable PoseStack poseStack, Matrix4fc modelViewMatrix,
                Iterable<? extends IRenderableSection> renderableSections, RenderPass renderPass) {
            super(levelRenderer, levelRenderState, poseStack, modelViewMatrix, renderableSections, renderPass);
            this.oitStage = oitStage;
        }

        public OitStage getOitStage() {
            return oitStage;
        }

        @Override
        public RenderPass getRenderPass() {
            return super.getRenderPass();
        }
    }

    /** После всего мира (`GameRenderer#renderLevel`). У нас не постится. */
    public static class AfterLevel extends RenderLevelStageEvent {
        public AfterLevel(LevelRenderer levelRenderer, LevelRenderState levelRenderState, @Nullable PoseStack poseStack,
                Matrix4fc modelViewMatrix, Iterable<? extends IRenderableSection> renderableSections) {
            super(levelRenderer, levelRenderState, poseStack, modelViewMatrix, renderableSections, null);
        }
    }
}

/*
 * Часть nfcompat (JEI). 3D-превью жидкости для fabric-ингредиента `FabricTypes.FLUID_STACK`.
 *
 * На NeoForge `TransformCategory` вешает на слот жидкости свой `FluidBlockRenderer`:
 * тот зовёт `FluidBlockRendering.render` и в слоте появляется наклонный 3D-блок. На Fabric
 * так нельзя: JEI-ингредиент жидкости здесь — `IJeiFluidIngredient` (внутри fabric
 * `FluidVariant`), а не `net.neoforged.neoforge.fluids.FluidStack`, под который типизирован
 * `FluidBlockRenderer`. Поэтому в FABRIC-ветке раньше стоял только `slot.setFluidRenderer(...)`
 * — плоский тайловый спрайт (`mezz.jei.library.render.FluidTankRenderer`).
 *
 * Здесь тот же путь, что у NeoForge-сборки, но для fabric-типа: координаты 0,0 в системе
 * слота (JEI сам сдвигает позу), размер 16x16, дальше `FluidBlockRendering` ->
 * `nfcompat.client.FluidBlockPictureInPicture`, а само состояние рисует
 * `FluidBlockPictureInPictureRenderer` из `RegisterPictureInPictureRenderersEvent`
 * (регистрация — `nfcompat.client.PictureInPicture`).
 *
 * Тултип не переписываем: отдаём его штатному JEI-рендереру этого типа (там локализованное
 * имя жидкости из fabric-хелпера). Это публичный API
 * `IIngredientManager#getIngredientRenderer(IIngredientType)`. Строку с количеством/ёмкостью,
 * которую рисовал слотовый `FluidTankRenderer` из `setFluidRenderer(..., true, ...)`, мы при
 * этом теряем — ровно как в NeoForge-сборке AE2, где на слоте тоже стоит свой рендерер.
 */
package nfcompat.compat.jei;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.material.Fluids;

import mezz.jei.api.fabric.constants.FabricTypes;
import mezz.jei.api.fabric.ingredients.fluids.IJeiFluidIngredient;
import mezz.jei.api.ingredients.IIngredientRenderer;

import org.jetbrains.annotations.Nullable;

import appeng.client.integrations.itemlists.FluidBlockRendering;
import appeng.client.integrations.jei.JEIPlugin;

public final class FluidBlockIngredientRenderer implements IIngredientRenderer<IJeiFluidIngredient> {

    public static final FluidBlockIngredientRenderer INSTANCE = new FluidBlockIngredientRenderer();

    private FluidBlockIngredientRenderer() {
    }

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, IJeiFluidIngredient ingredient) {
        var fluid = ingredient.getFluidVariant().getFluid();
        // Как и штатный FluidTankRenderer: для пустой жидкости (Fluids.EMPTY) ничего не рисуем,
        // иначе рендерер PiP полезет за моделью пустого состояния.
        if (fluid.isSame(Fluids.EMPTY)) {
            return;
        }
        FluidBlockRendering.render(guiGraphics, fluid, 0, 0, 16, 16);
    }

    @Override
    public List<Component> getTooltip(IJeiFluidIngredient ingredient, TooltipFlag tooltipFlag) {
        var defaultRenderer = defaultRenderer();
        if (defaultRenderer != null) {
            return defaultRenderer.getTooltip(ingredient, tooltipFlag);
        }
        // Рантайма JEI ещё нет (теоретически): отдаём хотя бы имя блока жидкости.
        return List.of(ingredient.getFluidVariant().getFluid().defaultFluidState()
                .createLegacyBlock().getBlock().getName());
    }

    /** {@return штатный рендерер JEI для fabric-жидкости, либо null, если JEI ещё не поднялся} */
    @Nullable
    private static IIngredientRenderer<IJeiFluidIngredient> defaultRenderer() {
        var runtime = JEIPlugin.instance();
        if (runtime == null) {
            return null;
        }
        return runtime.getIngredientManager().getIngredientRenderer(FabricTypes.FLUID_STACK);
    }
}

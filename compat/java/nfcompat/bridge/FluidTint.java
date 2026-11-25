/*
 * Часть nfcompat: настоящий ванильный тинт жидкости (как в мире), а не «цвет текста».
 *
 * Ваниль тинтит жидкость через tint source её fluid-модели:
 *   FluidStateModelSet.get(FluidState) -> FluidModel.tintSource() -> BlockTintSource
 * В 26.3 у BlockTintSource есть:
 *   int color(BlockState)                                        // вне мира
 *   int colorInWorld(BlockState, BlockAndTintGetter, BlockPos)    // настоящий цвет
 *
 * Факты из байткода 26.3 (minecraft-merged-deobf-26.3.jar):
 *  - FluidStateModelSet.<clinit> собирает модель воды с BlockTintSources.water():
 *    у него color(BlockState) == -1, а реальный цвет даёт colorInWorld ->
 *    BiomeColors.getAverageWaterColor -> Biome#getWaterColor (у равнин это "#3f76e4").
 *  - модель лавы собирается с tintSource == null: block/lava_still уже оранжевая,
 *    тинтовать её не нужно (то же самое для модовых жидкостей без модели).
 *
 * Никаких цветов здесь не захардкожено: единственный источник цвета — tint source
 * из fluid-модели, поэтому ресурспак/датапак/мод, подменивший тинт или биомный цвет,
 * подхватится автоматически.
 *
 * Возвращаем packed ARGB с непрозрачной альфой (0xFFRRGGBB); -1 = не тинтить
 * (рисовать текстуру как есть).
 *
 * ПОЧЕМУ АЛЬФА. Цвет жидкости ваниль кладёт в вершины как ARGB целиком:
 * FluidRenderer зовёт VertexConsumer.addVertex(x,y,z,color,...), BlockModelRenderer -
 * QuadInstance.multiplyColor (ARGB.multiply умножает и альфу). У воды tint приходит из
 * Biome#getWaterColor, а он парсится из `water_color: "#3f76e4"` через
 * ExtraCodecs.STRING_RGB_COLOR, то есть это непрозрачный 0xFF3F76E4.
 * Старое `color & 0xFFFFFF` срезало старший байт: в GUI это не замечалось
 * (Blitter.colorRgb / ARGB.opaque сами берут три канала), а в Sky Stone Tank цвет уходит
 * прямо в QuadInstance.setColor (BufferBuilder.setColor -> putRgba -> ARGB.toABGR), и
 * alpha=0 означал «полностью прозрачно»: спрайт и геометрия были, а не рисовалось ни
 * жидкости, ни столбика.
 */
package nfcompat.bridge;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;

public final class FluidTint {
    /** Тинт «нет тинта» == ванильный -1: рисуем текстуру как есть. */
    public static final int NO_TINT = -1;

    /**
     * Где сэмплить биом: в GUI своей позиции нет, поэтому берём позицию игрока в клиентском
     * мире (ванильный рендер жидкости тоже красит по биому под ней). Без игрока — нули.
     */
    private static final BlockPos FALLBACK_POS = BlockPos.ZERO;

    private FluidTint() {
    }

    /**
     * @return packed ARGB (0xFFRRGGBB) или {@link #NO_TINT}.
     */
    public static int colorOf(FluidStack stack) {
        if (stack == null || stack.isEmpty()) {
            return NO_TINT;
        }
        FluidModel model;
        try {
            model = Minecraft.getInstance().getModelManager().getFluidStateModelSet()
                    .get(stack.getFluid().defaultFluidState());
        } catch (RuntimeException ignored) {
            // fluid-модели ещё не забейканы (титульник/ранний GUI)
            return NO_TINT;
        }
        return colorOf(model, stack);
    }

    /**
     * @return packed ARGB (0xFFRRGGBB) или {@link #NO_TINT}.
     */
    public static int colorOf(FluidModel model, FluidStack stack) {
        if (stack == null || stack.isEmpty() || model == null) {
            return NO_TINT;
        }

        BlockTintSource tint = model.tintSource();
        int color = tint == null ? NO_TINT : applyTint(tint, legacyState(stack));
        if (color != NO_TINT && color != 0) {
            return color;
        }

        // Фолбэк (и ТОЛЬКО фолбэк): «цвет жидкости» из Transfer API. Это цвет текста
        // (для воды — чистый 0x0000FF), поэтому как основной источник он не годится.
        return associatedColor(stack);
    }

    private static int applyTint(BlockTintSource tint, BlockState state) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            BlockPos pos = samplePos();
            if (level.hasChunkAt(pos)) {
                try {
                    return normalize(tint.colorInWorld(state, level, pos));
                } catch (RuntimeException ignored) {
                    // биом/чанк ещё не готов — падаем на color(BlockState), как вне мира
                }
            }
        }

        // Мира нет вообще: ведём себя как ваниль без мира (BlockAndTintGetter.EMPTY),
        // у которого getBlockTint() = -1, т.е. «тинта нет».
        return normalize(tint.color(state));
    }

    private static BlockPos samplePos() {
        var player = Minecraft.getInstance().player;
        return player != null ? player.blockPosition() : FALLBACK_POS;
    }

    private static int normalize(int color) {
        if (color == 0 || color == NO_TINT) {
            return NO_TINT;
        }
        // Старший байт обязателен: значение уходит в QuadInstance.setColor (это ARGB, а не
        // «три канала» GUI), и с alpha=0 слой не рисуется вообще.
        return 0xFF000000 | (color & 0xFFFFFF);
    }

    private static BlockState legacyState(FluidStack stack) {
        try {
            return stack.getFluid().defaultFluidState().createLegacyBlock();
        } catch (RuntimeException ignored) {
            return Blocks.AIR.defaultBlockState();
        }
    }

    private static int associatedColor(FluidStack stack) {
        var variant = FluidStacks.toVariant(stack);
        if (variant.isBlank()) {
            return NO_TINT;
        }
        return normalize(FluidVariantAttributes.getAssociatedColor(variant));
    }
}

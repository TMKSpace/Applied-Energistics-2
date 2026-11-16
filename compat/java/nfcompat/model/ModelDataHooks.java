/*
 * Часть nfcompat. Статические хелперы вместо неофорджевских патчей BlockGetter/BlockEntity.
 *
 * ПОЧЕМУ ТАК, А НЕ КАК В NEOFORGE:
 * В NeoForge уровень отвечает на getModelData(pos) через ModelDataManager — кэш
 * «позиция -> данные модели» с хуками на загрузку/выгрузку блок-сущностей и сбросом
 * по requestModelDataUpdate(). У нас ни хуков, ни кэша нет — и кэш тут не нужен.
 *
 * КЭШ НЕ НУЖЕН, ПОТОМУ ЧТО РЕНДЕР-РЕГИОН — УЖЕ СНАПШОТ СЕКЦИИ.
 * При сборке меша чанка Indigo передаёт в emitQuads не живой Level, а
 * net.minecraft.client.renderer.chunk.RenderSectionRegion: неизменяемый снимок секции
 * 16x16x16 вместе с её блок-сущностями. Он implements BlockAndTintGetter и его
 * getBlockEntity(BlockPos) отдаёт настоящую блок-сущность из снапшота (проверено javap).
 * То есть чтение идёт из потока сборки меша по этому снимку и никогда не обращается к
 * живому Minecraft.level. Внутри одного прохода рендера снапшоту устареть не с чего,
 * а следующий проход получит свой, свежий.
 *
 * Отдельного клиентского фолбэка («найти блок-сущность в Minecraft.level по позиции»)
 * поэтому нет: он бы читал живой мир из фонового потока — ровно то, чего вся эта схема
 * избегает. До удаления фолбэка он был проверен на живых прогонах: диагностика
 * «ModelData: пусто» не сработала ни разу, то есть первый путь покрывал все вызовы.
 *
 * Итог: путь простой и честный — спросить BlockGetter.getBlockEntity(pos), взять данные
 * у неё напрямую; ModelData.EMPTY, если блок-сущности нет или она не ModelDataProvider.
 */
package nfcompat.model;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.model.data.ModelData;
import org.jspecify.annotations.Nullable;

public final class ModelDataHooks {

    private ModelDataHooks() {
    }

    /**
     * Замена {@code BlockGetter#getModelData(BlockPos)}.
     * <p>
     * Принимает {@code Object}, потому что вызывающий код держит разные типы:
     * {@code Level}, {@code BlockAndLightGetter}, клиентский {@code BlockAndTintGetter}.
     * Все они — наследники {@link BlockGetter} (проверено по minecraft-merged.jar 26.3),
     * так что instanceof срабатывает всегда; проверка оставлена только чтобы не ловить
     * ClassCastException, если кто-то подсунет что-то совсем постороннее.
     * <p>
     * В рендере сюда приходит {@code RenderSectionRegion} — снапшот секции, и его
     * {@code getBlockEntity} отвечает настоящими данными (см. шапку файла).
     */
    public static ModelData get(Object getter, BlockPos pos) {
        if (getter instanceof BlockGetter blockGetter) {
            return get(blockGetter.getBlockEntity(pos));
        }
        return ModelData.EMPTY;
    }

    /**
     * Замена {@code BlockEntity#getModelData()}, безопасная к null.
     */
    public static ModelData get(@Nullable BlockEntity blockEntity) {
        if (blockEntity instanceof ModelDataProvider provider) {
            return provider.getModelData();
        }
        return ModelData.EMPTY;
    }

    /**
     * Замена {@code BlockEntity#requestModelDataUpdate()}.
     * <p>
     * В NeoForge это инвалидация записи в кэше ModelDataManager: после неё следующий
     * запрос данных модели пересобирает их у блок-сущности. У нас кэша нет вообще
     * (см. {@link #get(Object, BlockPos)}), поэтому инвалидировать нечего — no-op.
     * Метод оставлен, чтобы не менять логику вызывающего кода и чтобы точки
     * «здесь данные модели устарели» остались видны в исходниках.
     */
    public static void requestUpdate(@Nullable Object holder) {
        // no-op: кэша данных модели на Fabric нет, см. javadoc выше
    }
}

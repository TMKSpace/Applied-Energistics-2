/*
 * Часть nfcompat. Статические хелперы вместо неофорджевских патчей BlockGetter/BlockEntity.
 *
 * ПОЧЕМУ ТАК, А НЕ КАК В NEOFORGE:
 * В NeoForge уровень отвечает на getModelData(pos) через ModelDataManager — он держит
 * кэш «позиция -> данные модели», наполняет его при загрузке чанка/блок-сущности и
 * сбрасывает по requestModelDataUpdate(). Кэш нужен, потому что рендер может читать
 * данные модели чаще, чем они меняются.
 *
 * У нас такого кэша нет и завести его неоткуда: нет хуков на загрузку/выгрузку
 * блок-сущностей, которыми ModelDataManager набивает свои карты. Поэтому путь
 * простой и честный: каждый вызов достаёт блок-сущность по позиции и спрашивает
 * её напрямую. Дороже, чем кэш (лишний getBlockEntity на каждый запрос), но
 * результат тот же самый — ModelData.EMPTY, если данных нет.
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
     */
    public static ModelData get(Object getter, BlockPos pos) {
        if (getter instanceof BlockGetter blockGetter) {
            var data = get(blockGetter.getBlockEntity(pos));
            if (data != ModelData.EMPTY) {
                return data;
            }
        }

        // Рендер-регион чанка BlockGetter'ом отдаёт null: block entity лежит за его
        // пределами. Берём его из клиентского мира по той же позиции.
        if (net.fabricmc.loader.api.FabricLoader.getInstance()
                .getEnvironmentType() == net.fabricmc.api.EnvType.CLIENT) {
            var fromClient = nfcompat.model.client.ClientModelDataLookup.lookup(pos);
            if (fromClient == ModelData.EMPTY) {
                nfcompat.Diagnostics.modelDataMissing(pos);
            }
            return fromClient;
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

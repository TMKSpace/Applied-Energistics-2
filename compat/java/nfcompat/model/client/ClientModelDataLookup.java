/*
 * Часть nfcompat. ModelData из клиентского мира для рендер-регионов.
 *
 * Модели AE2 (кабель-шина, привод, крафт-куб, пилон, квантовый мост) читают
 * ModelData через nfcompat.model.ModelDataHooks. При сборке меша чанка ваниль
 * передаёт не Level, а RenderChunkRegion — обёртку над соседними секциями, у которой
 * getBlockEntity() всегда null. NeoForge обходит это своим ModelDataManager
 * (снимок model data на секцию). У нас снимка нет, поэтому берём block entity
 * напрямую из клиентского мира: позиция та же, чанк тот же.
 *
 * Класс клиентский: на выделенном сервере он не должен загружаться вообще
 * (вызов идёт только после проверки стороны).
 */
package nfcompat.model.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.model.data.ModelData;

import nfcompat.model.ModelDataHooks;

public final class ClientModelDataLookup {

    private ClientModelDataLookup() {
    }

    public static ModelData lookup(BlockPos pos) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            nfcompat.Diagnostics.modelDataOfEntity("нет клиентского мира");
            return ModelData.EMPTY;
        }
        var blockState = level.getBlockState(pos);
        if (blockState.isAir()) {
            // Это пробный вызов загрузчика моделей с позицией 0,0,0 — не наш блок.
            return ModelData.EMPTY;
        }
        var entity = level.getBlockEntity(pos);
        nfcompat.Diagnostics.modelDataOfEntity(entity);
        return ModelDataHooks.get(entity);
    }
}

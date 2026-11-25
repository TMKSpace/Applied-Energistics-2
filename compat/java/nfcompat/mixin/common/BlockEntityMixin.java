/*
 * Часть nfcompat. Миксин на BlockEntity — возвращает рантайм-паритет с патчами NeoForge.
 *
 * 1. `ModelDataProvider`: в NeoForge `BlockEntity` патчится до `ModelDataProvider` — у него
 *    есть `getModelData()` и `requestModelDataUpdate()`. AE2 на это опирается.
 *    Компилятору миксин не виден, поэтому в исходниках AE2 стоят маркеры препроцессора
 *    (FABRIC-ветка уходит на nfcompat.model.ModelDataHooks), а здесь мы возвращаем
 *    рантайм-паритет: любой BlockEntity теперь является ModelDataProvider, и наш
 *    хелпер отличает «свои» данные от пустых штатным instanceof.
 *
 * 2. `onChunkUnloaded()`: NeoForge добавляет этот метод в `BlockEntity` (пустой дефолт),
 *    и `LevelChunk#clearAllBlockEntities` зовёт его у всех BE чанка перед `setRemoved()`.
 *    На ванильном `BlockEntity` метода нет, поэтому объявляем его здесь. Подклассы AE2
 *    (`AEBaseBlockEntity` и наследники) объявляют метод сами — он естественным образом
 *    переопределяет добавленный, и вызов через `ChunkUnloadListener` попадает в них.
 *    Кто и когда дергает — см. nfcompat.mixin.common.LevelChunkUnloadMixin.
 *
 * 3. `IAttachmentHolder`: в NeoForge BlockEntity — держатель attachment'ов. Тела методов
 *    default в самом интерфейсе (делегируют в nfcompat.attachments.Attachments),
 *    здесь только навешиваем интерфейс. См. nfcompat.mixin.common.EntityAttachmentMixin.
 */
package nfcompat.mixin.common;

import nfcompat.blockentity.ChunkUnloadListener;
import nfcompat.model.ModelDataProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockEntity.class)
public abstract class BlockEntityMixin implements ModelDataProvider, ChunkUnloadListener, IAttachmentHolder {

    @Override
    public ModelData getModelData() {
        // Базовая реализация — как дефолт NeoForge: данных нет.
        // Свои данные отдают блок-сущности AE2, переопределяя метод.
        return ModelData.EMPTY;
    }

    @Override
    public void onChunkUnloaded() {
        // Пустой дефолт, как в NeoForge. Работу делают переопределения в AE2.
    }
}

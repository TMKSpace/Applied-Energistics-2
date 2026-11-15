/*
 * Часть nfcompat. Миксин на BlockEntity.
 *
 * Зачем: в NeoForge `BlockEntity` патчится до `ModelDataProvider` — у него есть
 * `getModelData()` и `requestModelDataUpdate()`. AE2 на это опирается.
 * Компилятору миксин не виден, поэтому в исходниках AE2 стоят маркеры препроцессора
 * (FABRIC-ветка уходит на nfcompat.model.ModelDataHooks), а здесь мы возвращаем
 * рантайм-паритет: любой BlockEntity теперь является ModelDataProvider, и наш
 * хелпер отличает «свои» данные от пустых штатным instanceof.
 */
package nfcompat.mixin;

import nfcompat.model.ModelDataProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockEntity.class)
public abstract class BlockEntityMixin implements ModelDataProvider {

    @Override
    public ModelData getModelData() {
        // Базовая реализация — как дефолт NeoForge: данных нет.
        // Свои данные отдают блок-сущности AE2, переопределяя метод.
        return ModelData.EMPTY;
    }
}

/*
 * Часть nfcompat. Мост к ModelData для Fabric.
 *
 * Контракт NeoForge:
 *  - BlockEntity#getModelData()      — виртуальный метод, который переопределяют блок-сущности;
 *  - BlockEntity#requestModelDataUpdate() — просьба сбросить кэш данных модели;
 *  - BlockGetter#getModelData(BlockPos)   — чтение данных модели из мира.
 * Всё это патчи NeoForge в ванильные классы. В ванили MC 26.3 (проверено javap по
 * minecraft-merged.jar) таких методов нет, а из compat/java метод в существующий
 * ванильный класс не добавить. Поэтому на Fabric те же роли играют:
 *  - этот интерфейс (его реализует AEBaseBlockEntity, т.е. все блок-сущности AE2);
 *  - {@link ModelDataHooks} — статические хелперы вместо методов у BlockEntity/BlockGetter.
 *
 * В NeoForge-сборке AE2 этот интерфейс не нужен и не подключается: там метод уже есть
 * в BlockEntity (см. маркеры //#if FABRIC в AE2-исходниках).
 */
package nfcompat.model;

import net.neoforged.neoforge.model.data.ModelData;

/**
 * Замена неофорджевских методов {@code BlockEntity#getModelData()} и
 * {@code BlockEntity#requestModelDataUpdate()} для Fabric.
 * <p>
 * Реализуется {@code AEBaseBlockEntity}, поэтому все блок-сущности AE2 автоматически
 * умеют отдавать свои данные модели.
 */
public interface ModelDataProvider {

    /**
     * Данные модели для этой блок-сущности. Должны быть неизменяемыми: рендер может
     * читать их из другого потока.
     */
    ModelData getModelData();

    /**
     * Просьба сбросить закэшированные данные модели. В NeoForge это будит
     * {@code ModelDataManager}; у нас кэша нет, поэтому no-op — и это не потеря:
     * {@link ModelDataHooks#get} каждый раз честно создаёт данные заново.
     */
    default void requestModelDataUpdate() {
    }
}

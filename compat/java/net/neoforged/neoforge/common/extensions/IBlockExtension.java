/*
 * Часть nfcompat. Шим `net.neoforged.neoforge.common.extensions.IBlockExtension` — только та
 * часть, которая нужна нам.
 *
 * ЗАЧЕМ. NeoForge добавляет блокам метод
 * `onNeighborChange(BlockState, LevelReader, BlockPos, BlockPos)`, который зовётся, когда
 * у соседнего блока что-то изменилось (`Level#updateNeighbourForOutputSignal`). В ванили
 * такого хука нет: у блока есть только `neighborChanged(...)`, который получает *тип* соседа,
 * но не его позицию, а для частей AE2 позиция соседа критична (по ней шина хранения понимает,
 * что рядом поставили/убрали контейнер).
 *
 * ЧТО СДЕЛАНО. Интерфейс + миксин `Block implements IBlockExtension` (`BlockNeighborChangeMixin`)
 * + вызов из `Level#updateNeighbourForOutputSignal` (`LevelNeighborChangeMixin`). AE2 переопределяет
 * этот метод у `CableBusBlock` — в FABRIC-ветке он теперь тоже компилируется (без `@Override`,
 * потому что компилятор миксинов не видит).
 */
package net.neoforged.neoforge.common.extensions;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

public interface IBlockExtension {

    /**
     * Вызывается, когда блок-сосед по {@code neighbor} изменился. NeoForge-семантика: сообщаем
     * блоку в {@code pos}, что изменился блок в {@code neighbor}.
     */
    default void onNeighborChange(BlockState state, LevelReader level, BlockPos pos, BlockPos neighbor) {
    }
}

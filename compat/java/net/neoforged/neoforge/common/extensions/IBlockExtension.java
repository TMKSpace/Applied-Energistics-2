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
 *
 * ВТОРОЕ. NeoForge добавляет `onBlockExploded(BlockState, ServerLevel, BlockPos, Explosion)`:
 * блок сам решает, что делать со взрывом. Дефолт повторяет ванильное поведение (поставить воздух
 * и позвать `wasExploded`), а переопределение может блок сохранить — так делает `MatrixFrameBlock`
 * в AE2 ("immune to explosions"). Ванильный `BlockBehaviour#onExplosionHit` в 26.3 ставит воздух
 * сам и никакого хука не даёт, поэтому вызов разведён миксином `BlockExplosionMixin`: он
 * вклинивается перед `setBlockAndUpdate` и передаёт управление сюда.
 */
package net.neoforged.neoforge.common.extensions;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public interface IBlockExtension {

    /**
     * Вызывается, когда блок-сосед по {@code neighbor} изменился. NeoForge-семантика: сообщаем
     * блоку в {@code pos}, что изменился блок в {@code neighbor}.
     */
    default void onNeighborChange(BlockState state, LevelReader level, BlockPos pos, BlockPos neighbor) {
    }

    /**
     * Вызывается, когда блок разрушен взрывом. NeoForge-дефолт: убрать блок (флаг 3, как у
     * ванильного `setBlockAndUpdate`) и позвать {@link Block#wasExploded}. Переопределение может
     * блок оставить на месте — ровно это делает `MatrixFrameBlock`.
     */
    default void onBlockExploded(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion) {
        var block = (Block) this;
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        block.wasExploded(level, pos, explosion);
    }
}

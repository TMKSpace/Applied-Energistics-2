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
 *
 * ТРЕТЬЕ. NeoForge добавляет `canEntityDestroy(BlockState, BlockGetter, BlockPos, Entity)`:
 * «можно ли этому мобу снести этот блок». В ванили такого хука нет — визер зовёт статический
 * `WitherBoss.canDestroy(BlockState)` (только теги), а дракон прямо в `EnderDragon#checkWalls`
 * смотрит `!state.is(BlockTags.DRAGON_IMMUNE)`. NeoForge в патчах `WitherBoss` и `EnderDragon`
 * ЗАМЕНЯЕТ эти ванильные проверки на `state.canEntityDestroy(...)`, поэтому дефолт ниже обязан
 * воспроизводить ваниль (см. javadoc метода) — иначе обычные блоки начали бы/перестали ломаться.
 * Вызовы разведены миксинами `WitherBossDestroyBlocksMixin` и `EnderDragonDestroyBlocksMixin`.
 */
package net.neoforged.neoforge.common.extensions;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.level.BlockGetter;
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

    /**
     * Можно ли сущности {@code entity} снести блок в {@code pos}. NeoForge-семантика: {@code false} —
     * блок не трогаем, {@code true} — ломаем штатно ({@code destroyBlock}/{@code removeBlock}).
     * <p>
     * ВАЖНО: в NeoForge этот метод не «дополняет» ванильные проверки, а заменяет их, поэтому дефолт
     * повторяет ванильное поведение один-в-один:
     * <ul>
     * <li>дракон — ванильно {@code !state.is(BlockTags.DRAGON_IMMUNE)}; NeoForge берёт тег от
     * {@code defaultBlockState()} (тег всё равно на блоке, не на состоянии);</li>
     * <li>визер и его череп — ванильный {@code WitherBoss.canDestroy(state)} =
     * {@code !isAir() && !is(WITHER_IMMUNE)}; в NeoForge добавлено «воздух тоже можно»
     * (ломать нечего, но проверка не должна мешать);</li>
     * <li>любая другая сущность — {@code true}: ванильный код этих хуков других мобов не спрашивает.</li>
     * </ul>
     * Переопределение (у AE2 это {@code MatrixFrameBlock}) работает для любого моба: в FABRIC-ветке
     * метод объявлен без {@code @Override} и без прямого вызова, так что визер/дракон сносили
     * стенку spatial-плота.
     * <p>
     * ХРУПКОСТЬ. Дефолт специально зовёт ванильный {@code WitherBoss.canDestroy} вместо копии
     * выражения {@code !isAir() && !is(WITHER_IMMUNE)}: если при обновлении MC набор тегов
     * изменится, мы это унаследуем. Если же сам метод переименуют/уберут — сборка упадёт громко,
     * ровно там, где нужно.
     */
    default boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        if (entity instanceof EnderDragon) {
            return !((Block) this).defaultBlockState().is(BlockTags.DRAGON_IMMUNE);
        } else if (entity instanceof WitherBoss || entity instanceof WitherSkull) {
            return state.isAir() || WitherBoss.canDestroy(state);
        }
        return true;
    }
}

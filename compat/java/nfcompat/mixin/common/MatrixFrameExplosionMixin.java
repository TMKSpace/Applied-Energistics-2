/*
 * Часть nfcompat. Неуязвимость матричной рамки spatial-ячейки к взрывам.
 *
 * В NeoForge `BlockState#onExplosionHit` сначала дёргает `Block#onBlockExploded`
 * (патч `IBlockExtension`), а в AE2 этот метод переопределён пустым телом:
 * "immune to explosions". В ванили такого хука нет, поэтому FABRIC-ветка сняла
 * `@Override` — тела `onBlockExploded`/`canEntityDestroy` в `MatrixFrameBlock`
 * остались, но их никто не зовёт.
 *
 * Ванильная 26.3 ломает блоки взрывом в `BlockBehaviour#onExplosionHit`: он
 * считается лут по лут-таблице и убирает блок (`ServerExplosion#interactWithBlocks`
 * вызывает его для каждой позиции из `calculateExplodedPositions`). Это ровно та
 * точка, которую в NeoForge патчили под `onBlockExploded`, поэтому гасим вызов
 * здесь для `MatrixFrameBlock` — по классу блока, а не по id.
 *
 * Сопротивление взрыву (6000000) рамку и так прикрывает, но полагаться на одно
 * число не хотим: поведение должно совпадать с NeoForge-версией AE2. Остальные
 * блоки, урон сущностям, дроп и resistance не затрагиваются — для всего, что не
 * матричная рамка, метод работает как раньше.
 */
package nfcompat.mixin.common;

import java.util.function.BiConsumer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import appeng.block.spatial.MatrixFrameBlock;

@Mixin(BlockBehaviour.class)
public abstract class MatrixFrameExplosionMixin {

    @Inject(method = "onExplosionHit", at = @At("HEAD"), cancellable = true)
    private void nfcompat$matrixFrameImmuneToExplosions(BlockState state, ServerLevel level, BlockPos pos,
            Explosion explosion, BiConsumer<ItemStack, BlockPos> drops, CallbackInfo ci) {
        if ((Object) this instanceof MatrixFrameBlock) {
            ci.cancel();
        }
    }
}

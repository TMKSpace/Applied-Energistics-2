/*
 * Часть nfcompat. `Block#canEntityDestroy` для визера.
 *
 * NeoForge патчит `WitherBoss#customServerAiStep`: проверка `canDestroy(state)` заменяется на
 * `state.canEntityDestroy(level, blockPos, this)`, чтобы блок мог запретить разрушение
 * сущностью (у AE2 так делает `MatrixFrameBlock` - spatial-плот визер не должен грызть).
 *
 * ПОЧЕМУ `@ModifyExpressionValue`, А НЕ `@WrapOperation`.
 * MixinExtras требует, чтобы сахарные параметры (`@Local`) были хвостовыми, а для
 * `@WrapOperation` в хвосте обязан стоять `Operation`. Первая попытка (`Operation` первым либо
 * перед `@Local`) упала на валидации подписи, вторая - на "non-trailing sugared parameters".
 * `@ModifyExpressionValue` берёт ровно значение выражения (тот же байткод) и оставляет сахар
 * хвостовым: `(значение, @Local ...)`. Так же сделан драконий миксин, там эта схема рабочая.
 *
 * Семантика: `false` остаётся `false` (ваниль уже запретила), `true` уточняется хуком блока.
 * Наш дефолт в `IBlockExtension` воспроизводит ванильное условие (`isAir() || canDestroy(state)`),
 * поэтому для всех блоков, кроме переопределяющих, поведение не меняется.
 *
 * ХРУПКОСТЬ. Держится на имени `customServerAiStep` и на единственном вызове
 * `WitherBoss.canDestroy` внутри него; `@Local` - на LVT (`blockPos` и `state` в области
 * видимости вызова). Переименование метода ломает старт громко (`require = 1`), а не тихо.
 */
package nfcompat.mixin.common;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.extensions.IBlockExtension;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WitherBoss.class)
public abstract class WitherBossDestroyBlocksMixin {

    private static final Logger NFCOMPAT_LOGGER = LoggerFactory.getLogger("nfcompat/wither");

    @ModifyExpressionValue(
            method = "customServerAiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/boss/wither/WitherBoss;canDestroy(Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean nfcompat$canEntityDestroy(boolean original, @Local BlockPos pos, @Local BlockState state) {
        if (!original) {
            return false;
        }

        // `Block` реализует `IBlockExtension` через `BlockNeighborChangeMixin`, поэтому проверка
        // дешёвая, а переопределение AE2 (`MatrixFrameBlock`) подхватывается естественно.
        if (state.getBlock() instanceof IBlockExtension extension) {
            var wither = (WitherBoss) (Object) this;
            boolean allowed = extension.canEntityDestroy(state, wither.level(), pos, wither);
            if (nfcompat$isAe2Block(state.getBlock())) {
                NFCOMPAT_LOGGER.info("wither {} {} at {}", allowed ? "destroyed" : "blocked by", state.getBlock(), pos);
            }
            return allowed;
        }
        return true;
    }

    private static boolean nfcompat$isAe2Block(Block block) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        return id != null && "ae2".equals(id.getNamespace());
    }
}

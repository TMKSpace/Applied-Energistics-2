/*
 * Часть nfcompat. Неофорджевский хук `Block#canEntityDestroy` для дракона Края.
 *
 * ГДЕ. В ванили 26.3 ломание блоков драконом живёт в `EnderDragon#checkWalls(ServerLevel, AABB)`
 * (раньше метод назывался `destroyBlocks`, поэтому в патче NeoForge хук остался со старым
 * именем). На каждый непустой блок, который не в `DRAGON_TRANSPARENT`, стоит условие
 * `MOB_GRIEFING && !state.is(BlockTags.DRAGON_IMMUNE)`. NeoForge ЗАМЕНЯЕТ проверку
 * `MOB_GRIEFING` на `CommonHooks.canEntityDestroy(level, blockPos, this)`, то есть
 * `MOB_GRIEFING && state.canEntityDestroy(...) && onEntityDestroyBlock(...)` (геймрул внутри
 * `canEntityGrief`), а `!state.is(DRAGON_IMMUNE)` оставляет вторым множителем как есть.
 * Ванильная проверка ничего не знает про конкретный блок, поэтому дракон сносил стенку
 * spatial-плота: `MatrixFrameBlock#canEntityDestroy` возвращает false, но его никто не звал.
 *
 * ТОЧКА ВКЛИНА. `EnderDragon#checkWalls`, вызов
 * `GameRules.get(GameRules.MOB_GRIEFING)` (в байткоде — `invokevirtual GameRules.get(GameRule)Object`
 * сразу после `ServerLevel.getGameRules()`); в 26.3 это единственное обращение к геймрулам в
 * методе, так что ошибки «попали не туда» не будет. Значение выражения подменяем через
 * `@ModifyExpressionValue` (MixinExtras): `false` остаётся `false`, `true` уточняется хуком блока.
 * `blockPos` и `state` — локальные переменные тела цикла, захватываем через `@Local`.
 *
 * ПОДПИСЬ ХЕНДЛЕРА. `ModifyExpressionValueInjector` валидирует подпись как
 * `validateParams(handler, valueType, valueType)`, где `valueType` — тип выражения, то есть
 * «оригинальное значение, затем `@Local`-захваты» и никакого `Operation` (он есть только у
 * `@WrapOperation`, и там обязан быть последним). Тип выражения здесь `Object` (такой у
 * `GameRules.get(GameRule)` после стирания дженерика), поэтому хендлер принимает и возвращает
 * `Object`; за вызовом в байткоде стоит `checkcast java/lang/Boolean`, так что возвращать нужно
 * именно `Boolean` (`TRUE`/`FALSE` или исходное значение), иначе в рантайме `ClassCastException`.
 *
 * ПОЧЕМУ НЕ ПРОСТО «И» С ГЕЙМРУЛОМ. NeoForge именно заменяет геймрул-проверку (там ещё висит
 * событие `LivingDestroyBlockEvent` и `EntityMobGriefingEvent`), но для нас важно только то, что
 * `MOB_GRIEFING=false` по-прежнему полностью запрещает ломание: мы возвращаем `false` до
 * обращения к хуку. Для блоков без переопределения дефолт `IBlockExtension` повторяет ванильные
 * `!DRAGON_IMMUNE`, поэтому поведение дракона на обычных блоках не меняется.
 *
 * ХРУПКОСТЬ. Таргет держится на имени `checkWalls` (в NeoForge-патче он всё ещё зовётся
 * `destroyBlocks` — расхождение имён это нормально, мы цепляемся за ванильный байткод) и на том,
 * что `GameRules.get` в методе один. Если при обновлении MC геймрул начнут читать иначе
 * (например через `getBoolean` или вынесут в локальную переменную), инжектор не найдёт точку и
 * миксин упадёт на старте (required) — лечится правкой `at.target`. Захваты `@Local` держатся на
 * том, что в области видимости ровно по одной локали `BlockPos` и `BlockState`.
 */
package nfcompat.mixin.common;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.extensions.IBlockExtension;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EnderDragon.class)
public abstract class EnderDragonDestroyBlocksMixin {

    @ModifyExpressionValue(
            method = "checkWalls",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;"))
    private Object nfcompat$canEntityDestroy(Object mobGriefing, @Local BlockPos pos, @Local BlockState state) {
        // MOB_GRIEFING выключен — ваниль не ломает ничего, хук спрашивать не о чем.
        if (!(Boolean) mobGriefing) {
            return Boolean.FALSE;
        }

        // `Block` реализует `IBlockExtension` через `BlockNeighborChangeMixin`, так что ветка
        // срабатывает для всех блоков, а переопределения AE2 (`MatrixFrameBlock`) подхватываются
        // виртуально. Если интерфейс почему-то не навесился — оставляем ванильный `true`.
        if (state.getBlock() instanceof IBlockExtension extension) {
            var dragon = (EnderDragon) (Object) this;
            return extension.canEntityDestroy(state, dragon.level(), pos, dragon);
        }
        return mobGriefing;
    }
}

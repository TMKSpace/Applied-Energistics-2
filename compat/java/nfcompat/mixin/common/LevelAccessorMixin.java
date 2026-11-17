/*
 * Часть nfcompat. Неофорджевские методы `LevelAccessor`, которых ждёт GuideME.
 *
 * NeoForge расширяет `LevelAccessor` тремя методами: model data по позиции, менеджер
 * доп. света и проверка загруженности области с радиусом. GuideME зовёт их из своего
 * `FakeForwardingServerLevel` (уровень-декорация для 3D-сцены гайда) через `super`/интерфейс:
 *
 *     invokeinterface LevelAccessor.getModelData(BlockPos) -> NoSuchMethodError
 *
 * Так как это методы интерфейса, добавляем их `default`-реализациями: у AE2 в игре такие
 * вызовы не используются (кэша model data у нас нет, доп. света нет), поэтому возвращаем
 * пустые значения. Для сцены гайда этого достаточно: она декоративная.
 */
package nfcompat.mixin.common;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.neoforge.common.world.AuxiliaryLightManager;
import net.neoforged.neoforge.model.data.ModelData;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LevelAccessor.class)
public interface LevelAccessorMixin {

    /** NeoForge: model data блока; у нас кэша нет, поэтому пусто. */
    default ModelData getModelData(BlockPos pos) {
        return ModelData.EMPTY;
    }

    /** NeoForge: менеджер доп. света; в ванили его нет. */
    @Nullable
    default AuxiliaryLightManager getAuxLightManager(BlockPos pos) {
        return null;
    }

    @Nullable
    default AuxiliaryLightManager getAuxLightManager(ChunkPos pos) {
        return null;
    }

    /** NeoForge: загружена ли область вокруг позиции (в ванили такого метода нет). */
    default boolean isAreaLoaded(BlockPos pos, int radius) {
        return true;
    }
}

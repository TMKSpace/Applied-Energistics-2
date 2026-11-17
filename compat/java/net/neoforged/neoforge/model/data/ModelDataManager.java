/*
 * Часть nfcompat. Заглушка неофорджевского `ModelDataManager`.
 *
 * NeoForge держит кэш model data по block entity (`ModelDataManager`), и GuideME создаёт его
 * в `GuidebookLevel.<init>` — то есть при первом открытии страницы с 3D-сценой. Класса в шиме
 * не было: `new ModelDataManager(level)` -> NoClassDefFoundError.
 *
 * У нас кэша нет по принципу (см. `nfcompat.model.ModelDataHooks`): каждый запрос читает
 * block entity напрямую. Поэтому заглушка ничего не хранит, а `requestRefresh` — no-op.
 */
package net.neoforged.neoforge.model.data;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ModelDataManager {
    public ModelDataManager(Level level) {
    }

    /** В NeoForge сбрасывает кэш для блока; у нас кэша нет, так что нечего сбрасывать. */
    public void requestRefresh(BlockEntity blockEntity) {
    }
}

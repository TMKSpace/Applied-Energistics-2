/*
 * Часть nfcompat. Стаб неофорджевского `AuxiliaryLightManager`.
 *
 * NeoForge даёт уровням менеджер «дополнительного» света (динамические источники от модов),
 * и `LevelAccessor.getAuxLightManager(...)` возвращает его. GuideME дергает этот метод из
 * своего `FakeForwardingServerLevel` (сцена гайда), причём ему нужен только факт отсутствия
 * менеджера — сам свет он не расставляет.
 *
 * В ванили такого понятия нет вообще, поэтому это пустой маркер: возвращаем `null`
 * (см. `nfcompat.mixin.common.LevelAccessorMixin`).
 */
package net.neoforged.neoforge.common.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

import org.jetbrains.annotations.Nullable;

public interface AuxiliaryLightManager {

    /** NeoForge: свет на блоке, если он есть. */
    int getLightAt(BlockPos pos);

    /** NeoForge: сбросить свет в секции. */
    void clearSection(SectionPos section);

    /** NeoForge: пометить секцию как изменённую. */
    void markSectionForUpdate(SectionPos section);

    @Nullable
    static AuxiliaryLightManager getIfAvailable(Object level) {
        return null;
    }
}

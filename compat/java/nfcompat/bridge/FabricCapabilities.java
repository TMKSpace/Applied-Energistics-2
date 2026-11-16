/*
 * Часть nfcompat. Мост NeoForge-capability -> Fabric Transfer API.
 *
 * ПОЧЕМУ ОН НУЖЕН
 * В NeoForge ванильные сундуки, печи, баки и моды выставляют `Capabilities.Item.BLOCK` /
 * `Capabilities.Fluid.BLOCK` через RegisterCapabilitiesEvent. На Fabric этих провайдеров
 * нет вообще: контейнеры живут в `ItemStorage.SIDED` / `FluidStorage.SIDED`.
 *
 * Из-за этого у AE2 молчали всё, что ходит через capability:
 *   - импорт/экспорт-шины (StorageImportStrategy/StorageExportStrategy);
 *   - Pattern Provider (PatternProviderTarget через StackWorldBehaviors);
 *   - интерфейс, P2P-туннели предметов и жидкостей;
 *   - ForgeExternalStorageStrategy (он и есть основная стратегия внешнего хранилища).
 * Симптом: capability возвращает null, шина/провайдер считают, что рядом пусто.
 *
 * Поэтому `BlockCapability.getCapability` после NeoForge-провайдеров спрашивает этот мост:
 * он отдаёт Fabric-ное хранилище, обёрнутое в ResourceHandler (см. StorageItemHandler /
 * StorageFluidHandler).
 *
 * Контекст стороны: у Fabric lookup контекст — тоже Direction, поэтому передаём как есть
 * (null = "любая сторона/без стороны", Fabric это допускает).
 */
package nfcompat.bridge;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BaseCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jetbrains.annotations.Nullable;

public final class FabricCapabilities {

    private FabricCapabilities() {
    }

    /**
     * @return объект для запрошенной capability или {@code null}, если Fabric-ного
     *         хранилища на этой стороне нет
     */
    @Nullable
    public static Object lookup(BaseCapability<?, ?> capability, Level level, BlockPos pos,
            @Nullable BlockState state, @Nullable BlockEntity blockEntity, @Nullable Object context) {
        Direction side = context instanceof Direction direction ? direction : null;

        if (capability == Capabilities.Item.BLOCK) {
            var storage = ItemStorage.SIDED.find(level, pos, state, blockEntity, side);
            return storage == null ? null : new StorageItemHandler(storage);
        }

        if (capability == Capabilities.Fluid.BLOCK) {
            var storage = FluidStorage.SIDED.find(level, pos, state, blockEntity, side);
            return storage == null ? null : new StorageFluidHandler(storage);
        }

        if (capability == Capabilities.Energy.BLOCK) {
            // Энергия — мягкая зависимость: без мода team_reborn_energy вернётся null,
            // а сам класс EnergyStorage даже не загрузится (см. FabricEnergy).
            return FabricEnergy.lookup(level, pos, state, blockEntity, side);
        }

        return null;
    }
}

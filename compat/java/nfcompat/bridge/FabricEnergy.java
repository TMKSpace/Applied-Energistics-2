/*
 * Часть nfcompat. МОСТ ЭНЕРГИИ: NeoForge-ный `EnergyHandler` <-> Fabric-ный
 * `team.reborn.energy.api.EnergyStorage` (Team Reborn Energy, 1 E == 1 FE).
 * Работает и для блоков (`EnergyStorage.SIDED`), и для предметов (`EnergyStorage.ITEM`).
 *
 * Единицы НЕ пересчитываются: 1 E (Team Reborn) и 1 FE — одно и то же.
 * Коэффициент FE->AE (0.5) живёт в апстриме: `PowerUnit.FE.conversionRatio`
 * из `AEConfig.DEFAULT_FE_EXCHANGE`, применяется в `appeng.helpers.ForgeEnergyAdapter`
 * (`sink.injectExternalPower(PowerUnit.FE, ...)`). Мост обязан отдавать числа как есть.
 *
 * Этот класс — только ПРЕДОХРАНИТЕЛЬ и точка входа. Тип `EnergyStorage` здесь не
 * упоминается ни в полях, ни в сигнатурах, поэтому класс грузится и без мода:
 *   - нет мода `team_reborn_energy` -> `isAvailable()` == false, во все стороны молчим;
 *   - есть мод -> работа уходит в `EnergyBridge`, и только там JVM резолвит
 *     классы Team Reborn. Без такого разделения отсутствие API-мода в проде роняло бы
 *     `FabricCapabilities` с NoClassDefFoundError.
 */
package nfcompat.bridge;

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class FabricEnergy {

    /** id API-мода; он же внутри TechReborn (тот везёт его через `include`). */
    private static final String MOD_ID = "team_reborn_energy";

    /** null = ещё не спрашивали загрузчик. Тройное состояние, чтобы не дёргать его в горячем пути. */
    private static Boolean loaded;

    private FabricEnergy() {
    }

    public static boolean isAvailable() {
        Boolean value = loaded;
        if (value == null) {
            value = FabricLoader.getInstance().isModLoaded(MOD_ID);
            loaded = value;
        }
        return value;
    }

    /**
     * Inbound-направление: Fabric-ное хранилище энергии по позиции -> наш `EnergyHandler`
     * (см. EnergyStorageHandler). Возвращается `Object`, чтобы в сигнатуре не было
     * типа Team Reborn.
     *
     * @return обёртка или {@code null}, если мода нет либо хранилища на этой стороне нет
     */
    @Nullable
    public static Object lookup(Level level, BlockPos pos, @Nullable BlockState state,
            @Nullable BlockEntity blockEntity, @Nullable Direction side) {
        return isAvailable() ? EnergyBridge.lookup(level, pos, state, blockEntity, side) : null;
    }

    /**
     * Inbound-направление для предметов: Fabric-ное хранилище энергии ПРЕДМЕТА ->
     * наш `EnergyHandler` (см. EnergyStorageHandler).
     *
     * @return обёртка или {@code null}, если мода нет либо у предмета энергии нет
     */
    @Nullable
    public static Object lookupItem(ItemStack stack, ContainerItemContext context) {
        return isAvailable() ? EnergyBridge.lookupItem(stack, context) : null;
    }

    /**
     * Outbound-направление: отдаём NeoForge-ные провайдеры энергии ПРЕДМЕТОВ
     * (заряжаемые инструменты и портативные ячейки AE2) в `EnergyStorage.ITEM`.
     */
    public static void registerItemOutbound() {
        if (!isAvailable()) {
            return;
        }
        EnergyBridge.registerItemOutbound();
    }

    /**
     * Outbound-направление: отдаём NeoForge-ные провайдеры энергии (приёмники AE2,
     * FE P2P-туннели) в `EnergyStorage.SIDED`. Вызывать один раз и обязательно ПОСЛЕ
     * `RegisterCapabilitiesEvent`, иначе список блоков-провайдеров будет пуст.
     */
    public static void registerOutbound() {
        if (!isAvailable()) {
            return;
        }
        EnergyBridge.registerOutbound();
    }
}

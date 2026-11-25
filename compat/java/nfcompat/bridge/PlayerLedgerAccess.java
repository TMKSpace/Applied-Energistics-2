/*
 * Часть nfcompat. Маркер: у этого доступа к предмету есть владелец-игрок, значит к
 * операциям над предметом можно привязать журнал переноса остатка жидкости
 * ({@link FluidCarry}).
 *
 * Зачем отдельный интерфейс, а не метод в ItemAccess: ItemAccess — копия
 * NeoForge-ного API, и лишний метод там пришлось бы тащить при каждом обновлении.
 * Здесь достаточно одной строки `implements` в реализациях, у которых игрок реально
 * есть (PlayerItemAccess, InfiniteMaterialsItemAccess и обёртка OneByOneItemAccess).
 */
package nfcompat.bridge;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.player.Player;

public interface PlayerLedgerAccess {
    /** Игрок-владелец доступа или {@code null}, если доступа к игроку нет. */
    @Nullable
    Player nfcompat$player();
}

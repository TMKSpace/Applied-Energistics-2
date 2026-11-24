/*
 * Часть nfcompat. Мост Curios -> Trinkets (Fabric).
 *
 * На NeoForge AE2 получает аксессуары через Curios: `CuriosEntrypoint` (файл апстрима)
 * ставит реализацию `CuriosAccess` и вешает слушателя `SearchInventoryEvent`.
 * На Fabric Curios нет, но есть форк "Trinkets Updated" (Patbox), который живёт и под
 * 26.3 и держит ту же идею: слоты аксессуаров с живыми стеками.
 *
 * Поэтому здесь повторяется ровно то, что делает CuriosEntrypoint, только поверх
 * `eu.pb4.trinkets.api`:
 *   - `getStack(player, slotType, index)` — живой стек из слота Trinkets;
 *   - `forEachStack` — обход всех надетых аксессуаров (для хоткея портативных ячеек
 *     и для `SearchInventoryEvent`).
 *
 * ПОЧЕМУ КЛАСС ЛЕЖИТ В ПАКЕТЕ AE2. `CuriosAccess` и `CuriosIntegration.setAccess`
 * package-private — иначе апстрим не спрятал бы интеграцию. Чтобы не править исходники
 * AE2, наш класс кладётся в тот же пакет, но в наш source root (`compat/java`).
 * Для компилятора это один и тот же пакет, для апстрима — ноль изменений.
 *
 * Отличие от Curios: там `(slotType, index)`, где slotType — тип слота ("ring"), а
 * index — номер внутри этого типа. В Trinkets Updated ровно та же адресация:
 * `SlotType#getId()` + индекс, а `TrinketSlotReference` — её сериализуемый вид.
 * То есть `MenuLocators.forCurioSlot(slotType, index)` переиспользуется как есть.
 *
 * Класс грузится только когда Trinkets реально установлен (см. TrinketsHook),
 * поэтому `eu.pb4.trinkets.*` можно держать в compileOnly.
 */
package appeng.integration.modules.curios;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;

import appeng.util.SearchInventoryEvent;

import eu.pb4.trinkets.api.TrinketSlotAccess;
import eu.pb4.trinkets.api.TrinketsApi;

public final class TrinketsCuriosAccess implements CuriosAccess {

    private TrinketsCuriosAccess() {
    }

    /**
     * Ставит мост вместо no-op'а и повторяет регистрацию слушателя из CuriosEntrypoint,
     * чтобы аксессуары попадали в поиск предметов AE2 (вызов хоткея, открытие терминала
     * из слота и т.п.).
     */
    public static void install() {
        var access = new TrinketsCuriosAccess();
        CuriosIntegration.setAccess(access);

        NeoForge.EVENT_BUS.addListener((SearchInventoryEvent event) -> access.forEachStack(
                event.getEntity(),
                (slotType, index, stack) -> event.getStacks().add(stack)));
    }

    @Override
    public ItemStack getStack(Player player, String slotType, int index) {
        var attachment = TrinketsApi.getAttachment(player);
        if (attachment == null) {
            return ItemStack.EMPTY;
        }

        // getSlotAccess вернёт null для неизвестного типа слота; у невалидного
        // индекса get() сам отдаст ItemStack.EMPTY.
        TrinketSlotAccess slot = attachment.getSlotAccess(slotType, index);
        return slot == null ? ItemStack.EMPTY : slot.get();
    }

    @Override
    public void forEachStack(Player player, CuriosIntegration.StackVisitor visitor) {
        var attachment = TrinketsApi.getAttachment(player);
        if (attachment == null) {
            return;
        }

        // forEach отдаёт живые стеки (TrinketInventory.getItem -> NonNullList), а не копии:
        // меню AE2 правит содержимое ячейки/терминала прямо в слоте аксессуара.
        attachment.forEach((slot, stack) -> visitor.visit(slot.slotType().getId(), slot.index(), stack));
    }
}

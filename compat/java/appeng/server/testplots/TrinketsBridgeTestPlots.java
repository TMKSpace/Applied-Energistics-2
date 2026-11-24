/*
 * Часть nfcompat. Геймтест моста Curios -> Trinkets.
 *
 * Проверяет то, на чём держится интеграция AE2 с аксессуарами:
 *  1) адресация слота `(slotType, index)` из моста совпадает с той, что отдаёт Trinkets;
 *  2) `getStack` возвращает ЖИВОЙ стек (иначе меню портативной ячейки/терминала правит
 *     копию и изменения теряются - ровно этот баг апстрим чинил в #9107);
 *  3) `forEachStack` видит надетые аксессуары (хоткей портативных ячеек);
 *  4) `SearchInventoryEvent` получает аксессуары (поиск предметов AE2).
 *
 * Слот игроку даёт наш датапак (`compat/resources/data/trinkets/entities/player.json` +
 * `slots/hand/trinket.json`): сам Trinkets Updated 4.2.1 не привязывает ни одной группы к
 * типу сущности, без этого теста не на чем проверять мост.
 *
 * Если Trinkets не установлен, тест просто проходит: интеграция опциональна.
 */
package appeng.server.testplots;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import appeng.integration.modules.curios.CuriosIntegration;
import appeng.server.testworld.PlotBuilder;
import appeng.util.Platform;
import appeng.util.SearchInventoryEvent;

import eu.pb4.trinkets.api.TrinketInventory;
import eu.pb4.trinkets.api.TrinketsApi;

@TestPlotClass
public final class TrinketsBridgeTestPlots {
    private TrinketsBridgeTestPlots() {
    }

    @TestPlot(value = "trinkets_curios_bridge", padding = 2)
    public static void bridge(PlotBuilder plot) {
        plot.block(BlockPos.ZERO, Blocks.STONE);

        plot.test(helper -> {
            if (!FabricLoader.getInstance().isModLoaded("trinkets_updated")) {
                helper.succeed();
                return;
            }

            var player = Platform.getFakePlayer(helper.getLevel(), null);
            var attachment = TrinketsApi.getAttachment(player);
            helper.check(attachment != null, "Trinkets: у игрока нет attachment");

            // FakePlayer не добавляется в мир, поэтому Trinkets не собирает слоты сам
            // (у настоящего игрока это делает rebuild при входе/тике).
            if (attachment instanceof eu.pb4.trinkets.impl.LivingEntityTrinketAttachment impl) {
                impl.rebuild();
            }

            TrinketInventory inventory = null;
            for (var candidate : attachment.getInventories().values()) {
                if (candidate.getContainerSize() > 0) {
                    inventory = candidate;
                    break;
                }
            }
            helper.check(inventory != null,
                    "Trinkets: ни одного слота аксессуаров; groups=" + attachment.getGroups().keySet()
                            + " (датапак nfcompat не подхватился?)");

            var slotType = inventory.slotType().getId();
            helper.check(inventory.isValidSlot(0), "Trinkets: слот 0 невалиден");

            // Фильтр слота: в него можно положить только беспроводной терминал AE2
            // (валидатор `trinkets:default` + наш тег `trinkets:hand/trinket`).
            var slotAccess = inventory.getSlotAccess(0);
            helper.check(!inventory.slotType().validatorCheck(new ItemStack(Items.DIAMOND), slotAccess, player),
                    "Trinkets: в слот кладётся что угодно (фильтр по тегу не применился)");
            helper.check(
                    inventory.slotType().validatorCheck(new ItemStack(appeng.core.definitions.AEItems.WIRELESS_TERMINAL.get()),
                            slotAccess, player),
                    "Trinkets: слот не принимает беспроводной терминал");

            inventory.setItem(0, new ItemStack(Items.DIAMOND, 3));

            // 1) мост находит предмет по (slotType, index)
            var viaBridge = CuriosIntegration.getStack(player, slotType, 0);
            helper.check(viaBridge.is(Items.DIAMOND) && viaBridge.getCount() == 3,
                    "мост: getStack не нашёл аксессуар (" + slotType + ", 0)");

            // 2) стек живой: правка через мост видна в самом инвентаре Trinkets
            viaBridge.setCount(7);
            helper.check(inventory.getItem(0).getCount() == 7,
                    "мост: getStack вернул копию стека, изменения не дошли до Trinkets");

            // 3) обход всех аксессуаров
            var seen = new boolean[1];
            CuriosIntegration.forEachStack(player, (visitedType, index, stack) -> {
                if (slotType.equals(visitedType) && index == 0 && stack.getCount() == 7) {
                    seen[0] = true;
                }
            });
            helper.check(seen[0], "мост: forEachStack не увидел аксессуар");

            // 4) событие поиска предметов AE2
            var found = SearchInventoryEvent.getItems(player).stream()
                    .anyMatch(stack -> stack.is(Items.DIAMOND) && stack.getCount() == 7);
            helper.check(found, "мост: SearchInventoryEvent не получил аксессуары");

            helper.succeed();
        });
    }
}

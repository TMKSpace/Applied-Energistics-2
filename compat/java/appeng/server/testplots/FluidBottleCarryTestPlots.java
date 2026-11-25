/*
 * Часть nfcompat. Геймтест точного учёта жидкости в бутылках (ME-терминал).
 *
 * Симптом из игры: в сети 6000 mB, наполняем 3 стеклянные бутылки -> остаётся
 * 5001 mB. Причина: в Fabric бутылка — это 27000 droplets = 333.33 mB, целым mB не
 * представима. Пока отчёт об операции округлялся вниз на каждой операции, каждая
 * бутылка стоила сети 333 mB, и три бутылки списывали 999.
 *
 * Что проверяем:
 *  1) три бутылки, налитые по одной (как три клика по терминалу), списывают ровно
 *     1000 mB: 6000 -> 5000;
 *  2) каждая бутылка действительно превращается в бутылку воды, то есть предмет
 *     меняется там же, где списывается жидкость (это и был второй баг — «списали, а
 *     предмет не сменился»);
 *  3) слив тех же трёх бутылок возвращает ровно 1000 mB: 5000 -> 6000.
 *
 * Почему не через настоящее меню терминала: {@code AEBaseMenu.handleFillingHeldItem}
 * protected, а собрать полноценное меню терминала в тестплоте — это игрок, сеть и
 * синхронизация GUI. Поэтому здесь повторён ровно тот же серверный цикл
 * (SIMULATE -> извлечь из сети -> MODULATE -> вложить в предмет) поверх того же
 * публичного входа, которым пользуется меню: ContainerItemStrategies +
 * ContainerItemContext + capability предмета. Мост, контейнерные стратегии и учёт
 * остатка — настоящие.
 *
 * Энергия: в плоте стоит творческая энергоячейка, но в тесте сеть дёргается напрямую
 * через MEStorage (без StorageHelper.poweredExtraction) — на учёт это не влияет.
 */
package appeng.server.testplots;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import appeng.api.behaviors.ContainerItemContext;
import appeng.api.behaviors.ContainerItemStrategies;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageCells;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.me.helpers.BaseActionSource;
import appeng.server.testworld.PlotBuilder;
import appeng.server.testworld.PlotTestHelper;
import appeng.util.Platform;

@TestPlotClass
public final class FluidBottleCarryTestPlots {

    private static final IActionSource SOURCE = new BaseActionSource();

    private FluidBottleCarryTestPlots() {
    }

    @TestPlot(value = "fluid_bottles")
    public static void fluidBottles(PlotBuilder plot) {
        var origin = BlockPos.ZERO;

        // Сеть из одного ME-сундука с жидкостной ячейкой на 6 B воды.
        plot.creativeEnergyCell(origin.below());
        plot.blockEntity(origin, AEBlocks.ME_CHEST, chest -> {
            var cellItem = AEItems.FLUID_CELL_1K.stack();
            var cellInv = StorageCells.getCellInventory(cellItem, null);
            var inserted = cellInv.insert(AEFluidKey.of(Fluids.WATER), 6000, Actionable.MODULATE, SOURCE);
            if (inserted != 6000) {
                throw new IllegalStateException("ячейка не приняла 6 B воды: " + inserted);
            }
            chest.setCell(cellItem);
        });

        plot.test(helper -> helper.startSequence()
                // ждём, пока сеть поднимется и ячейка смонтируется: thenWaitUntil
                // повторяет проверку каждый тик (как succeedWhen), но ничего не меняет
                .thenWaitUntil(() -> helper.assertGridOnline(origin))
                .thenWaitUntil(() -> assertWater(helper, origin, 6000))
                .thenExecute(() -> fillAndDrain(helper, origin))
                .thenExecute(() -> assertWater(helper, origin, 6000))
                .thenSucceed());
    }

    /** Три клика «налить бутылку» и три «слить бутылку» — по одному контексту на клик. */
    private static void fillAndDrain(PlotTestHelper helper, BlockPos origin) {
        var water = AEFluidKey.of(Fluids.WATER);
        var storage = helper.getGrid(origin).getStorageService().getInventory();
        var player = Platform.getFakePlayer(helper.getLevel(), null);
        var menu = new CarriedMenu();
        var filledBottles = new ItemStack[3];

        for (int i = 1; i <= 3; i++) {
            // каждый клик — новый контекст: capability предмета запрашивается заново,
            // и учёт остатка переживает это только потому, что привязан к игроку
            menu.setCarried(new ItemStack(Items.GLASS_BOTTLE));
            var ctx = ContainerItemStrategies.findCarriedContextForKey(water, player, menu);
            helper.check(ctx != null, "нет контейнерного контекста для стеклянной бутылки");

            var filled = fill(helper, storage, ctx, water, i);
            helper.check(filled > 0, "бутылка " + i + " не наполнилась");
            helper.check(menu.getCarried().is(Items.POTION),
                    "бутылка " + i + " не превратилась в бутылку воды: " + menu.getCarried());
            filledBottles[i - 1] = menu.getCarried().copy();
        }

        helper.check(waterMb(storage, water) == 5000,
                "после трёх бутылок в сети " + waterMb(storage, water) + " mB вместо 5000");

        for (int i = 1; i <= 3; i++) {
            // сливаем ровно те бутылки, что налили: у Potion без PotionContents воды
            // нет, WaterPotionStorage такую бутылку не признает
            menu.setCarried(filledBottles[i - 1]);
            var ctx = ContainerItemStrategies.findCarriedContextForKey(water, player, menu);
            helper.check(ctx != null, "нет контейнерного контекста для бутылки воды");

            var drained = empty(helper, storage, ctx, i);
            helper.check(drained > 0, "бутылка воды " + i + " не слилась");
            helper.check(menu.getCarried().is(Items.GLASS_BOTTLE),
                    "бутылка воды " + i + " не стала пустой бутылкой: " + menu.getCarried());
        }
    }

    private static void assertWater(PlotTestHelper helper, BlockPos origin, long expected) {
        var water = AEFluidKey.of(Fluids.WATER);
        var storage = helper.getGrid(origin).getStorageService().getInventory();
        var actual = waterMb(storage, water);
        helper.check(actual == expected, "в сети " + actual + " mB воды вместо " + expected);
    }

    /**
     * Тело {@code AEBaseMenu.handleFillingHeldItem} для одного клика (fillAll = false):
     * симулируем извлечение из сети, симулируем вставку в предмет, потом повторяем оба
     * шага всерьёз ровно на столько, сколько прошла симуляция.
     *
     * @return сколько mB реально ушло из сети в предмет
     */
    private static long fill(PlotTestHelper helper, MEStorage storage, ContainerItemContext ctx, AEKey what, int n) {
        var amount = (long) what.getAmountPerUnit();

        var canPull = storage.extract(what, amount, Actionable.SIMULATE, SOURCE);
        helper.check(canPull > 0, "бутылка " + n + ": сеть не отдаёт жидкость даже в симуляции");

        var amountAllowed = ctx.insert(what, canPull, Actionable.SIMULATE);
        helper.check(amountAllowed > 0, "бутылка " + n + ": предмет не принимает жидкость (симуляция)");
        helper.check(amountAllowed <= canPull, "бутылка " + n + ": предмет обещал больше, чем есть в сети");

        var extracted = storage.extract(what, amountAllowed, Actionable.MODULATE, SOURCE);
        helper.check(extracted == amountAllowed, "бутылка " + n + ": сеть отдала " + extracted + " вместо "
                + amountAllowed + " mB");

        var inserted = ctx.insert(what, extracted, Actionable.MODULATE);
        helper.check(inserted > 0, "бутылка " + n + ": жидкость ушла из сети, а в предмет не попала");
        return inserted;
    }

    /**
     * Тело {@code AEBaseMenu.handleEmptyHeldItem} для одного клика (emptyAll = false).
     *
     * @return сколько mB реально вернулось из предмета в сеть
     */
    private static long empty(PlotTestHelper helper, MEStorage storage, ContainerItemContext ctx, int n) {
        GenericStack content = ctx.getExtractableContent();
        helper.check(content != null && content.amount() > 0,
                "бутылка воды " + n + ": предмет не сообщает содержимого");

        var what = content.what();
        var amount = (long) what.getAmountPerUnit();

        var canExtract = ctx.extract(what, amount, Actionable.SIMULATE);
        helper.check(canExtract > 0, "бутылка воды " + n + ": предмет не отдаёт жидкость (симуляция)");

        var amountAllowed = storage.insert(what, canExtract, Actionable.SIMULATE, SOURCE);
        helper.check(amountAllowed > 0, "бутылка воды " + n + ": сеть не принимает жидкость (симуляция)");

        var extracted = ctx.extract(what, amountAllowed, Actionable.MODULATE);
        helper.check(extracted == amountAllowed, "бутылка воды " + n + ": предмет отдал " + extracted
                + " вместо обещанных " + amountAllowed + " mB");

        var inserted = storage.insert(what, extracted, Actionable.MODULATE, SOURCE);
        helper.check(inserted == extracted, "бутылка воды " + n + ": в сеть попало " + inserted + " вместо "
                + extracted + " mB");
        return inserted;
    }

    private static long waterMb(MEStorage storage, AEKey water) {
        return storage.getAvailableStacks().get(water);
    }

    /**
     * Минимальное меню: контейнерной стратегии нужен только переносимый (carried) стек,
     * а он живёт в {@link AbstractContainerMenu}.
     */
    private static final class CarriedMenu extends AbstractContainerMenu {
        CarriedMenu() {
            super(null, 0);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }
}

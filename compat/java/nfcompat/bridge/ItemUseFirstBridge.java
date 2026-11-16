/*
 * Часть nfcompat. Точка вызова `Item#onItemUseFirst` / `Item#doesSneakBypassUse`.
 *
 * На NeoForge оба хука воткнуты в патченный `useItemOn` (сервер — `ServerPlayerGameMode`,
 * клиент — `MultiPlayerGameMode`) и работают ДО обработки блока:
 *
 *   var result = stack.onItemUseFirst(ctx);              // 1. предмет
 *   if (result == PASS && !blockedBySneak)               // 2. блок
 *       result = blockState.useItemOn(...);
 *   if (result == PASS) result = stack.useOn(ctx);        // 3. предмет ещё раз, уже "по блоку"
 *
 * Ваниль 26.3 про этот хук не знает, поэтому AE2-шные `//#if FABRIC //#else @Override //#endif`
 * переопределения просто никто не зовёт: фасад не ставится на кабель (единственный вход в
 * `FacadeItem#placeFacade`), карта улучшения вставляется только через GUI, карта памяти
 * шифт-ПКМ стирается/открывает меню вместо копирования настроек, и т.д.
 *
 * Реализация — `UseBlockCallback` из Fabric API: он стреляет в HEAD `useItemOn` на обеих
 * сторонах (см. подробности в шапке `RightClickBlockBridge`, там же про предсказание),
 * то есть раньше ванильных проверок спектатора, шифта и кулдауна.
 *
 * Порядок регистрации: ЭТОТ мост ставится в `LifecycleBridge` ДО `RightClickBlockBridge`,
 * потому что Fabric перебирает хэндлеры в порядке регистрации. Так предмет получает клик
 * раньше, чем постится наш аналог `PlayerInteractEvent.RightClickBlock` (её слушает
 * `appeng.hooks.WrenchHook`). Сам по себе хук предмета от гаечного ключа не зависит, но
 * так гарантированно нет двух обработок одного клика.
 *
 * Клиент/сервер: как и в 1.20.1 (`ToolItemHook`), хук работает на обеих сторонах — иначе
 * клиент не начнёт предсказание и не отправит `ServerboundUseItemOnPacket`, а сервер один
 * не узнает о клике. Не-`PASS` результат отменяет ванильную обработку (Fabric API на клиенте
 * при `consumesAction()` ещё и запустит предсказание с отправкой пакета).
 */
package nfcompat.bridge;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.extensions.IItemExtension;

public final class ItemUseFirstBridge {
    private static boolean installed;

    private ItemUseFirstBridge() {
    }

    /** Идемпотентно: вызывается из LifecycleBridge.install() (клиентская и серверная точка входа). */
    public static void install() {
        if (installed) {
            return;
        }
        installed = true;

        // Сигнатура UseBlockCallback#interact: (Player, Level, InteractionHand, BlockHitResult) -> InteractionResult.
        UseBlockCallback.EVENT.register(ItemUseFirstBridge::onUseBlock);
    }

    private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand,
            BlockHitResult hitResult) {
        // В 1.20.1 ToolItemHook тоже отсекал только спектатора: остальное (шифт, кулдаун, пустая рука)
        // решала ваниль ниже по стеку, а нам важно не сломать её поведение.
        if (player.isSpectator()) {
            return InteractionResult.PASS;
        }

        var context = new UseOnContext(player, hand, hitResult);
        var stack = context.getItemInHand();
        if (stack.isEmpty()) {
            return InteractionResult.PASS;
        }

        if (!(stack.getItem() instanceof IItemExtension item)) {
            // Чужой/ванильный предмет: ничего не делаем, ваниль работает как обычно.
            return InteractionResult.PASS;
        }

        // --- 1. Item#onItemUseFirst, до блока ---
        var result = item.onItemUseFirst(stack, context);
        if (result != InteractionResult.PASS) {
            return result;
        }

        // --- 2. doesSneakBypassUse ---
        // Ваниль в `ServerPlayerGameMode#useItemOn` при шифте с непустой рукой вообще не вызывает
        // `BlockState#useItemOn`. NeoForge снимает это ограничение, если предмет отвечает true
        // (карта памяти: шифт-ПКМ копирует настройки устройства вместо открытия GUI). Повторяем
        // ровно эту ветку ванили, только без шифта: блок — и дальше обычный стек.
        if (isSneakBlocking(player) && item.doesSneakBypassUse(stack, level, hitResult.getBlockPos(), player)) {
            return useBlockIgnoringSneak(player, level, hand, hitResult, stack);
        }

        return InteractionResult.PASS;
    }

    /**
     * Условие ванильной заглушки блока: шифт зажат и хотя бы одна рука не пуста. NeoForge
     * проверяет именно это (`player.isSecondaryUseActive()` + непустые руки), а не "шифт + предмет
     * в конкретной руке".
     */
    private static boolean isSneakBlocking(Player player) {
        return player.isSecondaryUseActive()
                && !(player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty());
    }

    /**
     * Копия ванильной ветки `ServerPlayerGameMode#useItemOn`, которая при шифте пропускается:
     * вызов блока, затем (для основной руки) пустая рука, если блок попросил именно её.
     * `PASS` означает "дальше как обычно" — ваниль сама доделает `Item#useOn`.
     */
    private static InteractionResult useBlockIgnoringSneak(Player player, Level level, InteractionHand hand,
            BlockHitResult hitResult, ItemStack stack) {
        BlockPos pos = hitResult.getBlockPos();
        var state = level.getBlockState(pos);

        var blockResult = state.useItemOn(stack, level, player, hand, hitResult);
        if (blockResult == InteractionResult.TRY_WITH_EMPTY_HAND) {
            // Ваниль разворачивает этот результат в "ПКМ пустой рукой", но только для основной руки.
            if (hand == InteractionHand.MAIN_HAND) {
                blockResult = state.useWithoutItem(level, player, hitResult);
            }
            // Fabric-колбэк такого спец-результата не знает: непонятное ему отдаём как PASS.
            if (blockResult == InteractionResult.TRY_WITH_EMPTY_HAND) {
                blockResult = InteractionResult.PASS;
            }
        }

        return blockResult;
    }
}

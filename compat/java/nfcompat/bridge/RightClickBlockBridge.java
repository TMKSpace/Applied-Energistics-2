/*
 * Часть nfcompat. Точка поста `PlayerInteractEvent.RightClickBlock`.
 *
 * На NeoForge событие постится из патченных `ServerPlayerGameMode#useItemOn` (сервер) и
 * `MultiPlayerGameMode#useItemOn` (клиент, до клиентского предсказания). Раньше у нас это были
 * два миксина nfcompat, теперь — прямой аналог из Fabric API: `UseBlockCallback`.
 *
 * Куда именно он попадает (проверено по байткоду fabric-events-interaction-v0 5.3.10+b9b88df45d,
 * это версия из fabric-api 0.166.2+26.3):
 *   - сервер: @Inject(method = "useItemOn", at = HEAD, cancellable = true) в
 *     `ServerPlayerGameMode` — ровно то же место, где стоял наш ServerPlayerGameModeUseItemOnMixin;
 *   - клиент: @Inject(method = "useItemOn", at = INVOKE startPrediction, cancellable = true) в
 *     `MultiPlayerGameMode` — то есть до запуска клиентского предсказания, ровно тот момент, когда
 *     срабатывал наш MultiPlayerGameModeUseItemOnMixin (HEAD `performUseItemOn`).
 *
 * Возврат `InteractionResult.PASS` означает "ваниль работает как обычно" (и на клиенте, и на сервере
 * Fabric API просто выходит из своего инжекта). Любой другой результат отменяет ванильную обработку;
 * на клиенте при `result.consumesAction()` Fabric API ещё и запустит предсказание с отправкой
 * `ServerboundUseItemOnPacket`, поэтому сервер тоже получит клик и сделает свою половину работы.
 *
 * Нужно `appeng.hooks.WrenchHook` (гаечные ключи AE2: разбор с шифтом, поворот без шифта) — он слушает
 * `RightClickBlock` и без этого события молчит.
 */
package nfcompat.bridge;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.world.InteractionResult;
import net.neoforged.neoforge.event.EventHooks;

public final class RightClickBlockBridge {
    private static boolean installed;

    private RightClickBlockBridge() {
    }

    /** Идемпотентно: вызывается из LifecycleBridge.install(), а тот — из клиентской и серверной точки входа. */
    public static void install() {
        if (installed) {
            return;
        }
        installed = true;

        // Сигнатура UseBlockCallback#interact: (Player, Level, InteractionHand, BlockHitResult) -> InteractionResult.
        // Двойного применения серверной мутации не будет: WrenchHook работает каждый на своей копии мира
        // (ClientLevel / ServerLevel), а дропы и инвентарь внутри disassembleWithWrench под `instanceof ServerLevel`.
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            var result = EventHooks.onRightClickBlock(player, level, hand, hitResult);
            // EventHooks отдаёт PASS, если событие никто не отменил — тогда ваниль продолжает штатно.
            return result == InteractionResult.PASS ? InteractionResult.PASS : result;
        });
    }
}

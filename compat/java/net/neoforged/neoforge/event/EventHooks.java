/*
 * Часть nfcompat.
 *
 * В NeoForge EventHooks — это десятки статических хуков, вызываемых из патченного
 * ванильного кода (размещение блока, взрыв, крафт, сон, харвест...). Нам ванильный
 * код патчить нечем, поэтому здесь только те хуки, которые AE2 зовёт сам.
 *
 * Если понадобится ещё какой-то — добавить сюда метод с той же сигнатурой и
 * постом соответствующего события на NeoForge.EVENT_BUS.
 */
package net.neoforged.neoforge.event;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;

/**
 * Только используемое AE2 подмножество NeoForge EventHooks.
 */
public final class EventHooks {
    private EventHooks() {
    }

    public static void firePlayerCraftingEvent(Player player, ItemStack crafted, Container craftMatrix) {
        NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerEvent.ItemCraftedEvent(player, crafted,
                craftMatrix));
    }

    public static boolean onBlockPlace(@Nullable Entity entity, BlockSnapshot blockSnapshot, Direction direction) {
        var level = blockSnapshot.getLevel();
        if (level == null) {
            return false;
        }
        var placedAgainst = level.getBlockState(blockSnapshot.getPos().relative(direction.getOpposite()));
        var event = new net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent(blockSnapshot, placedAgainst, entity);
        return NeoForge.EVENT_BUS.post(event).isCanceled();
    }

    public static void onExplosionDetonate(Level level, ServerExplosion explosion, List<Entity> entities,
            List<BlockPos> blocks) {
        NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.ExplosionEvent.Detonate(level, explosion, entities,
                blocks));
    }

    /**
     * NeoForge зовёт это из патченного {@code Player#getDestroySpeed(BlockState)} (у нас — миксин
     * {@code nfcompat.mixin.PlayerDestroySpeedMixin}). Нужен AE2: {@code SkyStoneBreakSpeed} делит время
     * копания скайстоуна на 10 для инструментов лучше железного.
     * <p>
     * Отменённое событие в NeoForge означает "блок не ломается вообще", поэтому отдаём 0 —
     * прогресс разрушения не растёт.
     */
    public static float getBreakSpeed(Player player, BlockState state, float original, @Nullable BlockPos pos) {
        var event = new net.neoforged.neoforge.event.entity.player.PlayerEvent.BreakSpeed(player, state, original, pos);
        return NeoForge.EVENT_BUS.post(event).isCanceled() ? 0f : event.getNewSpeed();
    }

    /**
     * NeoForge зовёт это из патченных {@code ServerPlayerGameMode#useItemOn} (сервер) и
     * {@code MultiPlayerGameMode#useItemOn} (клиент, до клиентского предсказания). У нас вместо патчей —
     * {@code UseBlockCallback} из Fabric API, зарегистрированный в
     * {@code nfcompat.bridge.RightClickBlockBridge} (инжекты Fabric API стоят в тех же местах).
     * <p>
     * Нужно AE2: {@code WrenchHook} ловит {@code RightClickBlock} и делает всю работу гаечного ключа
     * (разбор с шифтом, поворот без шифта). Отмена события означает "ваниль не должна ничего делать
     * с блоком/предметом" — возвращаем {@link InteractionResult} из {@code setCancellationResult},
     * иначе {@link InteractionResult#PASS} (ваниль продолжает штатно).
     */
    public static InteractionResult onRightClickBlock(Player player, Level level, InteractionHand hand,
            BlockHitResult hitResult) {
        var event = new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player, hand,
                hitResult.getBlockPos(), hitResult);
        NeoForge.EVENT_BUS.post(event);
        return event.isCanceled() ? event.getCancellationResult() : InteractionResult.PASS;
    }
}

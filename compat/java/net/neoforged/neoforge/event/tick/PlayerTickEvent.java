/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

/*
 * Часть nfcompat. Копия NeoForge 26.3 (`PlayerTickEvent.java` с ветки `26.3.x`),
 * сигнатуры не менялись.
 *
 * Постим у себя не миксином, а из мостов:
 *  - сервер  — `nfcompat.LifecycleBridge`, из `ServerTickEvents.START/END_SERVER_TICK`:
 *              Pre для всех игроков в начале тика, Post — в конце;
 *  - клиент  — `nfcompat.ClientEventBridge`, из `ClientTickEvents.START/END_CLIENT_TICK`.
 *
 * На NeoForge события летят по каждому игроку вокруг его собственного тика
 * (`EventHooks.firePlayerTickPre/Post` вызываются из `Player#tick()`, head и tail).
 * Наша группировка даёт тот же порядок относительно тика: все Pre — до тиков
 * игроков, все Post — после. Разница только в чередовании между игроками
 * (Pre1 Pre2 ... Post1 Post2 вместо Pre1 Post1 Pre2 Post2) — для слушателей
 * вида "раз в N тиков" (extendedae_plus: `JeiSyncManager.onPlayerTick(Post)`)
 * это неотличимо.
 *
 * При обновлении MC: сигнатура стабильна; если NeoForge переедет с `Player#tick`
 * на другой хук, нам это не важно — точки постинга у нас в мостах, а не в миксине.
 */
package net.neoforged.neoforge.event.tick;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Base class of the two player tick events.
 * <p>
 * These events are separate from `LivingTickEvent` (в шиме такого класса нет) due to the semantics of player ticks.
 * On the client, players tick from the usual {@link Entity#tick()} method, but on the server, they rely
 * on {@link ServerPlayer#doTick()} which is called from {@link ServerGamePacketListenerImpl#tick()}.
 * <p>
 * Use of these events should only be necessary if you rely on this specific timing.
 *
 * @see PlayerTickEvent.Pre
 * @see PlayerTickEvent.Post
 */
public abstract class PlayerTickEvent extends PlayerEvent {
    protected PlayerTickEvent(Player player) {
        super(player);
    }

    /**
     * {@link PlayerTickEvent.Pre} is fired once per game tick, per player, before the player performs work for the current tick.
     * <p>
     * This event will fire on both the logical server and logical client, for subclasses of {@link Player} on their respective sides.
     * <p>
     * As such, be sure to check {@link Level#isClientSide()} before performing any operations.
     */
    public static class Pre extends PlayerTickEvent {
        public Pre(Player player) {
            super(player);
        }
    }

    /**
     * {@link PlayerTickEvent.Post} is fired once per game tick, per player, after the player performs work for the current tick.
     * <p>
     * This event will fire on both the logical server and logical client, for subclasses of {@link Player} on their respective sides.
     * <p>
     * As such, be sure to check {@link Level#isClientSide()} before performing any operations.
     */
    public static class Post extends PlayerTickEvent {
        public Post(Player player) {
            super(player);
        }
    }
}

/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

/*
 * Часть nfcompat. Копия NeoForge 26.3 (`ServerStartingEvent.java` с ветки `26.3.x`),
 * сигнатура не менялась.
 *
 * Постится из `nfcompat.LifecycleBridge` на Fabric-событии
 * `ServerLifecycleEvents.SERVER_STARTING`, сразу после `ServerAboutToStartEvent` —
 * порядок как на NeoForge: AboutToStart -> Starting -> Started.
 *
 * Пример аддона: extendedae_plus (`ExtendedAEPlus.onServerStarting`).
 *
 * При обновлении MC: класс пустой и стабильный, ломается только вместе с
 * `ServerLifecycleEvent`.
 */
package net.neoforged.neoforge.event.server;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Called after {@link ServerAboutToStartEvent} and before {@link ServerStartedEvent}.
 * This event allows for customizations of the server.
 *
 * If you need to add commands use {@link RegisterCommandsEvent}.
 *
 * @author cpw
 */
public class ServerStartingEvent extends ServerLifecycleEvent {
    public ServerStartingEvent(final MinecraftServer server) {
        super(server);
    }
}

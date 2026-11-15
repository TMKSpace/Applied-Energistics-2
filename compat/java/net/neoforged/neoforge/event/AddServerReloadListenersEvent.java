/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.event;

import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.resource.ListenerKey;
import org.jetbrains.annotations.ApiStatus;

/**
 * The main ResourceManager is recreated on each reload, just after {@link ReloadableServerResources}'s creation.
 *
 * The event is fired on each reload and lets modders add their own ReloadListeners, for server-side resources.
 * The event is fired on the {@link NeoForge#EVENT_BUS}
 *
 * nfcompat: в NeoForge здесь ещё есть ICondition.IContext и таблица имён ванильных слушателей
 * (VanillaServerListeners). Ни то, ни другое AE2 не использует, поэтому выкинуто: имя слушателя
 * берётся из его класса.
 */
public class AddServerReloadListenersEvent extends SortedReloadListenerEvent {
    private final ReloadableServerResources serverResources;

    @ApiStatus.Internal
    public AddServerReloadListenersEvent(ReloadableServerResources serverResources,
            Map<ListenerKey<?>, PreparableReloadListener> retainedListeners) {
        super(serverResources.listeners(), AddServerReloadListenersEvent::lookupName);
        this.serverResources = serverResources;
    }

    /**
     * Adds a new reload listener to the resource manager, running after all vanilla listeners unless
     * dependency ordering says otherwise.
     *
     * @throws IllegalArgumentException if another listener with that key was already registered.
     */
    public <T extends PreparableReloadListener> void addRetainedListener(ListenerKey<T> key, T listener) {
        this.addListener(key.getListenerId(), listener);
    }

    /**
     * @return The {@link ReloadableServerResources} being reloaded.
     */
    public ReloadableServerResources getServerResources() {
        return serverResources;
    }

    private static Identifier lookupName(PreparableReloadListener listener) {
        return Identifier.fromNamespaceAndPath("minecraft", sanitize(listener.getClass().getName()));
    }

    private static String sanitize(String className) {
        StringBuilder sb = new StringBuilder(className.length());
        for (int i = 0; i < className.length(); i++) {
            char c = className.charAt(i);
            sb.append(Character.isLetterOrDigit(c) ? Character.toLowerCase(c) : '_');
        }
        return sb.toString();
    }
}

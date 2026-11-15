/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.client.resources;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;

/**
 * Keys for vanilla reload listeners, used to express dependency ordering in
 * {@link net.neoforged.neoforge.client.event.AddClientReloadListenersEvent}.
 *
 * nfcompat: в NeoForge это полная таблица «класс -> ключ» для всех ванильных слушателей
 * (и вспомогательный VanillaClassToKey). AE2 нужен ровно один ключ, поэтому таблица урезана
 * до него, а имена остальных генерируются из класса.
 */
public final class VanillaClientListeners {
    /** Block entity renderers, i.e. the dispatcher that part renderers hook into. */
    public static final Identifier BLOCK_ENTITY_RENDERER = Identifier.fromNamespaceAndPath("minecraft",
            "block_entity_renderers");

    private static final Map<Class<?>, Identifier> BY_CLASS = new IdentityHashMap<>();

    static {
        for (Class<?> cls : new Class<?>[] {
                net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher.class }) {
            BY_CLASS.put(cls, BLOCK_ENTITY_RENDERER);
        }
    }

    private VanillaClientListeners() {
    }

    /**
     * @return the key for a vanilla reload listener class, or {@code null} if unknown.
     */
    public static Identifier getNameForClass(Class<?> cls) {
        Identifier direct = BY_CLASS.get(cls);
        if (direct != null) {
            return direct;
        }
        if (!cls.getPackageName().startsWith("net.minecraft")) {
            return null;
        }
        return Identifier.fromNamespaceAndPath("minecraft", sanitize(cls.getName()));
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

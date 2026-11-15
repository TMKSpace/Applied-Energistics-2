package net.neoforged.neoforge.common;

import net.neoforged.bus.api.IEventBus;

/**
 * nfcompat: replaces NeoForge#EVENT_BUS. Bus implementation is SimpleEventBus.
 */
public final class NeoForge {
    public static final IEventBus EVENT_BUS = new net.neoforged.bus.api.SimpleEventBus();

    private NeoForge() {
    }
}

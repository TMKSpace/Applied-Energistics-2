/*
 * Часть nfcompat. События конфигов.
 *
 * Forge Config API Port предоставляет сам конфиг-механизм (ModConfigSpec, ModConfig),
 * но не эти события, поэтому они объявлены здесь. Поля названы как в NeoForge.
 */
package net.neoforged.fml.event.config;

import net.neoforged.bus.api.Event;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.IModBusEvent;

public abstract class ModConfigEvent extends Event implements IModBusEvent {
    private final ModConfig config;

    protected ModConfigEvent(ModConfig config) {
        this.config = config;
    }

    public ModConfig getConfig() {
        return config;
    }

    /** Конфиг загружен впервые. */
    public static class Loading extends ModConfigEvent {
        public Loading(ModConfig config) {
            super(config);
        }
    }

    /** Конфиг перечитан. */
    public static class Reloading extends ModConfigEvent {
        public Reloading(ModConfig config) {
            super(config);
        }
    }

    /** Конфиг выгружен. */
    public static class Unloading extends ModConfigEvent {
        public Unloading(ModConfig config) {
            super(config);
        }
    }
}

package net.neoforged.neoforge.client.event;

import net.neoforged.bus.api.Event;

/**
 * nfcompat: NeoForge event skeleton. Members are added on demand.
 */
public class ViewportEvent extends Event {
    public ViewportEvent() {
    }

    public static class ComputeFogColor extends ViewportEvent {
        public ComputeFogColor() {
        }
    }

    public static class ComputeCameraAngles extends ViewportEvent {
        public ComputeCameraAngles() {
        }
    }
}


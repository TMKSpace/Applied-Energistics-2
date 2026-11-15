package net.neoforged.bus.api;

/**
 * Маркер отменяемого события. В NeoForge события реализуют его, а не получают
 * отмену от базового Event.
 */
public interface ICancellableEvent {
    boolean isCanceled();

    void setCanceled(boolean canceled);
}

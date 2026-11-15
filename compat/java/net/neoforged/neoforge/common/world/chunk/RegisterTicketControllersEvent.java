/*
 * Vendored from NeoForge (LGPL-2.1-only), adapted for the Fabric shim.
 * Upstream: net/neoforged/neoforge/common/world/chunk/RegisterTicketControllersEvent.java
 *
 * SHIM: this event is never posted by the Fabric shim. {@link #register(TicketController)} keeps the controller in
 * a static registry so code that queries it behaves sanely, and {@link #getRegisteredControllers()} gives a
 * potential host a way to drive ticket validation.
 */

package net.neoforged.neoforge.common.world.chunk;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

/**
 * Fired in order to register chunk {@link TicketController ticket controllers}.
 */
public class RegisterTicketControllersEvent extends Event implements IModBusEvent {
    private static final Map<String, TicketController> REGISTERED = new LinkedHashMap<>();

    /**
     * Registers a controller.
     *
     * @param controller the controller to register
     * @throws IllegalArgumentException if a controller with the same {@link TicketController#id() id} is already registered
     */
    public void register(TicketController controller) {
        if (REGISTERED.putIfAbsent(controller.id().toString(), controller) != null) {
            throw new IllegalArgumentException("Duplicate ticket controller id: " + controller.id());
        }
    }

    /**
     * {@return all controllers registered through this event}
     */
    public static Collection<TicketController> getRegisteredControllers() {
        return new ArrayList<>(REGISTERED.values());
    }

    /**
     * {@return the registered controller with the given id, or an empty list if it is unknown}
     */
    public static List<TicketController> getRegisteredControllers(String id) {
        var controller = REGISTERED.get(id);
        return controller == null ? List.of() : List.of(controller);
    }
}

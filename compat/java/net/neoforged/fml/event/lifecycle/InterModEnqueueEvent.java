/*
 * Часть nfcompat. Этап, на котором моды рассылают IMC-сообщения другим модам.
 */
package net.neoforged.fml.event.lifecycle;

import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

public class InterModEnqueueEvent extends Event implements IModBusEvent {
    public InterModEnqueueEvent() {
    }
}

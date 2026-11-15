/*
 * Часть nfcompat. Заглушка события InterModProcessEvent.
 *
 * Живёт в NeoForge загрузчике (модуль fml), не в самом NeoForge. Здесь нужна
 * только ради javadoc-ссылок из скопированных классов событий.
 */
package net.neoforged.fml.event.lifecycle;

import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

public class InterModProcessEvent extends Event implements IModBusEvent {
    public InterModProcessEvent() {
    }
}

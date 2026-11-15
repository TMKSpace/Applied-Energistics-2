/*
 * Часть nfcompat. NeoForge-овское событие сбора payload-обработчиков.
 *
 * В NeoForge его публикует mod event bus во время инициализации мода, и моды
 * регистрируют в нём свои каналы. На Fabric аналога нет: каналы регистрируются
 * напрямую в PayloadTypeRegistry. Поэтому здесь событие - просто точка входа:
 * шим публикует его сам (см. nfcompat-хук NetworkPayloadBootstrap), а
 * PayloadRegistrar, который оно отдаёт, сразу пишет в Fabric API.
 *
 * Если событие так никто и не опубликует, мод просто не зарегистрирует каналы,
 * и первая же попытка отправки payload'а упадёт с внятной ошибкой Fabric'а
 * ("Payload ... is not registered").
 */
package net.neoforged.neoforge.network.event;

import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class RegisterPayloadHandlersEvent extends Event implements IModBusEvent {
    public RegisterPayloadHandlersEvent() {
    }

    /**
     * Создаёт регистратор payload'ов.
     *
     * @param version версия сети. В NeoForge обязана быть непустой и
     *                участвует в переговорах; на Fabric запоминается и
     *                игнорируется (см. PayloadRegistrar).
     */
    public PayloadRegistrar registrar(String version) {
        return new PayloadRegistrar(version);
    }
}

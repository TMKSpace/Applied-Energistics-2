/*
 * Часть nfcompat. Событие этапа загрузки: общая инициализация.
 *
 * У нас этот этап не разбит на фазы: событие существует как тип и может быть
 * отправлено вручную. Подписчики AE2 получат его, когда шим решит, что пора.
 */
package net.neoforged.fml.event.lifecycle;

public class FMLCommonSetupEvent extends ParallelDispatchEvent {
    public FMLCommonSetupEvent(net.neoforged.fml.ModContainer modContainer, net.neoforged.fml.DeferredWorkQueue deferredWorkQueue) {
        super(modContainer, deferredWorkQueue);
    }
}

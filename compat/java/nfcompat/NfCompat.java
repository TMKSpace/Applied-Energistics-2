/*
 * Часть nfcompat. Мост точек входа AE2.
 *
 * На NeoForge конструктор мода вызывается загрузчиком, и уже он:
 *  - регистрирует объекты (наши DeferredRegister'ы жадные, поэтому фактически уже готовы);
 *  - вешает слушателей на мод-шину;
 *  - а загрузчик потом рассылает RegisterEvent по каждому реестру, NewRegistryEvent,
 *    RegisterCapabilitiesEvent, RegisterPayloadHandlersEvent и этапы инициализации.
 *
 * На Fabric ничего этого нет, поэтому здесь мы вручную делаем то же самое и в том же
 * порядке. Если порядок нарушить, часть AE2 просто не инициализируется (например,
 * реестр AEKeyType создаётся в NewRegistryEvent, и без него RegisterEvent для него
 * нечего обрабатывать).
 */
package nfcompat;

import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.DeferredWorkQueue;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;
import net.neoforged.fml.loading.moddiscovery.ModInfo;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.NewRegistryEvent;

import appeng.api.stacks.AEKeyType;

public final class NfCompat {
    /** Должен совпадать с appeng.api.ids.AEConstants.MOD_ID, иначе AEConfig упадёт. */
    public static final String MOD_ID = "ae2";

    private static ModContainer container;

    private NfCompat() {
    }

    public static ModContainer container() {
        if (container == null) {
            container = new ModContainer(
                    new ModInfo(MOD_ID, "Applied Energistics 2", "26.3.1-probe"),
                    NeoForge.EVENT_BUS);
        }
        return container;
    }

    /**
     * Этапы, которые на NeoForge загрузчик проходит после создания мода.
     */
    public static void runRegistrationPhases() {
        var bus = NeoForge.EVENT_BUS;

        // 1. Собственные реестры мода. Раньше всех: AEKeyType создаётся здесь,
        //    и без него следующий шаг для этого ключа ничего не сделает.
        bus.post(new NewRegistryEvent());

        // 2. Регистрация в ванильные реестры — по одному событию на реестр.
        bus.post(new RegisterEvent(Registries.SOUND_EVENT));
        bus.post(new RegisterEvent(Registries.CREATIVE_MODE_TAB));
        bus.post(new RegisterEvent(Registries.CUSTOM_STAT));
        bus.post(new RegisterEvent(Registries.TRIGGER_TYPE));
        bus.post(new RegisterEvent(Registries.PARTICLE_TYPE));
        bus.post(new RegisterEvent(Registries.MENU));
        bus.post(new RegisterEvent(Registries.CHUNK_GENERATOR));
        bus.post(new RegisterEvent(Registries.VILLAGER_PROFESSION));
        bus.post(new RegisterEvent(Registries.POINT_OF_INTEREST_TYPE));
        bus.post(new RegisterEvent(AEKeyType.REGISTRY_KEY));
        bus.post(new RegisterEvent(Registries.TEST_INSTANCE_TYPE));

        // 3. Провайдеры capability и сетевые каналы.
        bus.post(new RegisterCapabilitiesEvent());
        bus.post(new RegisterPayloadHandlersEvent());
        bus.post(new RegisterTicketControllersEvent());

        // 4. Этап общей инициализации: здесь AE2 доделывает то, что требует
        //    уже зарегистрированных объектов.
        var queue = new DeferredWorkQueue();
        bus.post(new FMLCommonSetupEvent(container(), queue));
        for (Runnable work : queue.drain()) {
            work.run();
        }

        // 5. Межмодовые сообщения.
        bus.post(new InterModEnqueueEvent());
    }
}

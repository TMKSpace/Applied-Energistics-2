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
            container = container(MOD_ID, "Applied Energistics 2", "26.3.1-probe");
        }
        return container;
    }

    /** Контейнер для библиотек, которые на NeoForge были бы отдельными модами (GuideME). */
    public static ModContainer container(String modId, String name, String version) {
        return new ModContainer(new ModInfo(modId, name, version), NeoForge.EVENT_BUS);
    }

    /**
     * Этапы, которые на NeoForge загрузчик проходит после создания мода.
     */
    public static void runRegistrationPhases() {
        var bus = NeoForge.EVENT_BUS;

        // 0.0. Автоподписка по @EventBusSubscriber. На NeoForge её делает загрузчик
        //      ДО первых событий (в момент построения мода). Обычно вызов уже сделан
        //      точками входа (NfCompatClient/NfCompatServer) — здесь он идемпотентно
        //      доигрывается для тех, кто пришёл другим путём.
        nfcompat.event.AutoEventSubscriber.install();

        // 0. Конфиги. На NeoForge загрузчик грузит их после того, как все моды
        //    построены: AE2 вешает слушателя Loading в конструкторе уже ПОСЛЕ
        //    registerConfig, поэтому событие нельзя постить в момент регистрации.
        //    Значения при этом прочитаны раньше (ModConfig#load), так что get() работает.
        int loadedConfigs = ModContainer.postAllPendingConfigLoads();
        if (loadedConfigs > 0) {
            System.out.println("[nfcompat] конфигов загружено: " + loadedConfigs);
        }

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

        // 2a. «Запекаем» собственные реестры: в NeoForge колбэки реестра
        //     срабатывают после всех регистраций. Без этого AEKeyTypes.getAll()
        //     остаётся пустым набором и терминал отсекает все записи сетки.
        nfcompat.registry.RegistryCallbacks.bakeAll();

        // 3. Провайдеры capability и сетевые каналы.
        bus.post(new RegisterCapabilitiesEvent());

        // 3a. Мост энергии в обратную сторону: NeoForge-ные провайдеры энергии (приёмники
        //     AE2, FE P2P-туннели) отдаём в Fabric-ный EnergyStorage.SIDED, чтобы кабели
        //     Team Reborn видели AE2. Строго после события: список блоков берётся из уже
        //     собранных провайдеров. Без мода team_reborn_energy — тихо ничего не делает.
        //     Обратное направление (Fabric -> AE2) живёт в FabricCapabilities.
        nfcompat.bridge.FabricEnergy.registerOutbound();

        // 3b. То же для предметов и жидкостей: NeoForge-ные провайдеры AE2 отдаём в
        //     Fabric-ные ItemStorage.SIDED / FluidStorage.SIDED. Тоже строго после
        //     события (список блоков берётся из собранных провайдеров) и идемпотентно.
        nfcompat.bridge.StorageBridge.registerOutbound();

        // 3c. Item-capability: предметы с содержимым (заряжаемые ячейки и инструменты,
        //     а также всё, что в будущем зарегистрирует Fluid/Item.ITEM) — в
        //     Fabric-ные ItemStorage.ITEM / FluidStorage.ITEM / EnergyStorage.ITEM.
        //     Обратное направление живёт в ItemCapability/FabricItemCapabilities.
        nfcompat.bridge.ItemCapabilityBridge.registerOutbound();

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

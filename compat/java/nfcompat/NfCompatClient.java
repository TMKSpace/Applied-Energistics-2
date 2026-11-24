/*
 * Часть nfcompat. Точка входа клиента.
 *
 * AppEngClient наследует AppEngBase, поэтому его конструктор делает и общую часть.
 * Порядок важен: типы пакетов регистрируются в общем этапе
 * (`RegisterPayloadHandlersEvent`), а клиентские обработчики
 * (`RegisterClientPayloadHandlersEvent`) ссылаются на уже известные типы — иначе
 * NeoForge'вский PayloadRegistrar падает с «no payload type has been registered».
 */
package nfcompat;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.color.item.ItemTintSources;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties;
import net.neoforged.fml.DeferredWorkQueue;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.client.event.InitializeClientRegistriesEvent;
import net.neoforged.neoforge.client.event.RegisterBlockStateModels;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterItemModelsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterRangeSelectItemModelPropertyEvent;
import net.neoforged.neoforge.client.model.block.BlockStateModelHooks;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import appeng.client.AppEngClient;
import nfcompat.compat.guideme.GuideMEBootstrap;

public final class NfCompatClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // 0. GuideME. На NeoForge библиотека — отдельный мод, и его создаёт загрузчик;
        //    на Fabric этого никто не делал, поэтому `GuideME.<clinit>` (регистрация
        //    предмета `guideme:guide`) срабатывал лениво — при первом рендере
        //    SimpleRenderContext, то есть уже после заморозки реестров. Тогда
        //    `Item.<init>` падал с "This registry can't create intrusive holders".
        //    Поднимаем здесь — в том же окне, где AE2 регистрирует свои предметы.
        //    Строго до runRegistrationPhases (см. ниже) и до `new AppEngClient`.
        GuideMEBootstrap.init();

        // Рантайм-события (тики, жизненный цикл сервера, команды) — то, что на NeoForge
        // рассылает загрузчик. Без них в одиночной игре не выставляется текущий сервер,
        // не тикает очередь блок-сущностей и не считается питание сети.
        LifecycleBridge.install();

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ServerLifecycleHooks.setCurrentServer(null));

        new AppEngClient(NeoForge.EVENT_BUS, NfCompat.container());

        // 0b. Клиентские рантайм-события (тики, вход в мир, клавиши, рендереры,
        //    частицы, команды). На NeoForge их рассылает сам загрузчик; без них
        //    клиент AE2 наполовину мёртв: нет тиков кабелей, нет BER-ов блоков.
        //    Ставим после конструктора: часть событий рассылается сразу.
        ClientEventBridge.install();

        // GuideME поднят в начале метода (GuideMEBootstrap.init): кнопка «?» и открытие
        // гайда работают штатно, отдельной заглушки для `GuidesCommon.openGuide` больше нет.

        // 1. Общие этапы: регистрация реестров, типов пакетов, capability.
        NfCompat.runRegistrationPhases();

        // 2. Клиентские обработчики — только после того, как типы пакетов известны.
        NeoForge.EVENT_BUS.post(new RegisterClientPayloadHandlersEvent());

        // 2b. Фабрики клиентских тултипов. NeoForge'вский ClientTooltipComponent.create не
        //     знает чужих типов, а у Fabric для этого есть официальный
        //     TooltipComponentCallback (так же сделано в порте AE2 под 1.20.1).
        NeoForge.EVENT_BUS.post(new RegisterClientTooltipComponentFactoriesEvent(
                nfcompat.client.TooltipFactories.FACTORIES));

        // 2c. Экраны меню. NeoForge патчит MenuScreens#register в public; у нас метод
        //     открыт через accesswidener, поэтому карту из события применяем сами.
        var screens = new java.util.HashMap<MenuType<?>, MenuScreens.ScreenConstructor<?, ?>>();
        NeoForge.EVENT_BUS.post(new RegisterMenuScreensEvent(screens));
        screens.forEach((type, ctor) -> {
            @SuppressWarnings({ "unchecked", "rawtypes" })
            var unused = registerScreen((MenuType) type, (MenuScreens.ScreenConstructor) ctor);
        });

        // 2d. Клиентские reload-слушатели (стили экранов AE2, модели частей).
        //     На NeoForge событие получает настоящий ReloadableResourceManager; на Fabric
        //     слушателей надо отдать ResourceManagerHelper. Без этого StyleManager не
        //     получает ResourceManager и открытие любого экрана AE2 падает —
        //     клиент выкидывает с Network Protocol Error.
        nfcompat.client.ReloadListeners.registerAll();

        // 2e. Кастомные элементы item-моделей (ae2:color, ae2:energy_fill_level, ae2:facade ...).
        //     В 26.3 ваниль умеет диспетчеризацию по "type", но мапперы типов приватные и
        //     пустые: NeoForge в конце своего bootstrap() постит эти события, а моды в них
        //     регистрируют свои типы. Поля открыты через accesswidener (ae2.accesswidener).
        NeoForge.EVENT_BUS.post(new RegisterItemModelsEvent(ItemModels.ID_MAPPER));
        NeoForge.EVENT_BUS.post(new RegisterColorHandlersEvent.ItemTintSources(ItemTintSources.ID_MAPPER));
        NeoForge.EVENT_BUS.post(new RegisterRangeSelectItemModelPropertyEvent(
                RangeSelectItemModelProperties.ID_MAPPER));

        // 2e2. Кастомные blockstate-модели (ae2:crafting_cube, ae2:drive, ae2:cable_bus ...).
        //      Ваниль не принимает одиночный объект с "type" в варианте; NeoForge это
        //      разрешает своим кодеком. Типы регистрирует AE2, а читает кодек из наших
        //      мапперов (см. nfcompat.mixin.SimpleModelSelectorsMixin / MultipartSelectorMixin).
        NeoForge.EVENT_BUS.post(new RegisterBlockStateModels(
                BlockStateModelHooks.BLOCK_STATE_MODEL_IDS,
                BlockStateModelHooks.BLOCK_MODEL_DEFINITION_IDS));

        // 2e3. Кастомные рендереры окружения (небо/облака/погода измерения). На NeoForge
        //      событие постит сам загрузчик; рендереры складываются в карты, а связь
        //      "измерение -> id рендерера" мод делает сам (EnvironmentRenderers.bind),
        //      потому что атрибутов neoforge:custom_* в ванильном реестре нет.
        NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.client.event.RegisterCustomEnvironmentEffectRendererEvent(
                nfcompat.client.EnvironmentRenderers.cloudRenderers(),
                nfcompat.client.EnvironmentRenderers.skyboxRenderers(),
                nfcompat.client.EnvironmentRenderers.weatherRenderers()));

        // 2f. Группы частиц (`RegisterParticleGroupsEvent`) и picture-in-picture
        //     рендереры (`RegisterPictureInPictureRenderersEvent`). На NeoForge их
        //     рассылает патч `ParticleEngine`/`GameRenderer`; у нас карты копятся здесь,
        //     а применяются в nfcompat.mixin.client.ParticleEngineMixin / GameRendererMixin.
        //     Строго до создания `ParticleEngine` и `GameRenderer` (оба — на вход в мир/старт).
        nfcompat.client.ParticleGroups.install();
        nfcompat.client.PictureInPicture.install();

        // 3. Клиентская инициализация.
        var queue = new DeferredWorkQueue();
        NeoForge.EVENT_BUS.post(new FMLClientSetupEvent(NfCompat.container(), queue));
        for (Runnable work : queue.drain()) {
            work.run();
        }

        // 4. Клиентские реестры AE2 (модели частей, модели ячеек хранения).
        //    На NeoForge событие приходит из Minecraft перед запеканием моделей; без него
        //    AppEngClient.partModels остаётся null, и разрешение модели cable_bus падает
        //    с "Client registries have not been initialized yet" — падает весь reload.
        NeoForge.EVENT_BUS.post(new InitializeClientRegistriesEvent());

        // 5. Standalone-модели (`ModelEvent.RegisterStandalone`): модель ручки кривошипа и
        //    модели ячеек хранения для ME-сундука. Строго после шага 4: обработчик AE2
        //    обходит `StorageCellModels`, который наполняется как раз в
        //    `InitializeClientRegistriesEvent` — иначе в карте окажется только дефолтная
        //    модель ячейки. И до первого reload'а ресурсов (клиентская инициализация
        //    загрузчика случается раньше), иначе Fabric-плагин их не увидит.
        //    Запекание делает сам Fabric: `ModelLoadingPlugin.Context#addModel` кладёт
        //    их в `ModelDiscovery` и общий `ModelBakery`, а `StandaloneModels.get(...)`
        //    читает готовое из `ModelManager` (карта пересобирается на каждом reload'е).
        nfcompat.client.StandaloneModels.install();
        NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.client.event.ModelEvent.RegisterStandalone(
                nfcompat.client.StandaloneModels.models()));
        System.out.println("[nfcompat] standalone-моделей AE2: "
                + nfcompat.client.StandaloneModels.models().size());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static boolean registerScreen(MenuType type, MenuScreens.ScreenConstructor ctor) {
        MenuScreens.register(type, ctor);
        return true;
    }
}
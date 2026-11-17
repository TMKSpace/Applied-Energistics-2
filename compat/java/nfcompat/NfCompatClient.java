/*
 * ╨з╨░╤Б╤В╤М nfcompat. ╨в╨╛╤З╨║╨░ ╨▓╤Е╨╛╨┤╨░ ╨║╨╗╨╕╨╡╨╜╤В╨░.
 *
 * AppEngClient ╨╜╨░╤Б╨╗╨╡╨┤╤Г╨╡╤В AppEngBase, ╨┐╨╛╤Н╤В╨╛╨╝╤Г ╨╡╨│╨╛ ╨║╨╛╨╜╤Б╤В╤А╤Г╨║╤В╨╛╤А ╨┤╨╡╨╗╨░╨╡╤В ╨╕ ╨╛╨▒╤Й╤Г╤О ╤З╨░╤Б╤В╤М.
 * ╨Я╨╛╤А╤П╨┤╨╛╨║ ╨▓╨░╨╢╨╡╨╜: ╤В╨╕╨┐╤Л ╨┐╨░╨║╨╡╤В╨╛╨▓ ╤А╨╡╨│╨╕╤Б╤В╤А╨╕╤А╤Г╤О╤В╤Б╤П ╨▓ ╨╛╨▒╤Й╨╡╨╝ ╤Н╤В╨░╨┐╨╡
 * (`RegisterPayloadHandlersEvent`), ╨░ ╨║╨╗╨╕╨╡╨╜╤В╤Б╨║╨╕╨╡ ╨╛╨▒╤А╨░╨▒╨╛╤В╤З╨╕╨║╨╕
 * (`RegisterClientPayloadHandlersEvent`) ╤Б╤Б╤Л╨╗╨░╤О╤В╤Б╤П ╨╜╨░ ╤Г╨╢╨╡ ╨╕╨╖╨▓╨╡╤Б╤В╨╜╤Л╨╡ ╤В╨╕╨┐╤Л тАФ ╨╕╨╜╨░╤З╨╡
 * NeoForge'╨▓╤Б╨║╨╕╨╣ PayloadRegistrar ╨┐╨░╨┤╨░╨╡╤В ╤Б ┬лno payload type has been registered┬╗.
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
        // 0. GuideME. ╨Э╨░ NeoForge ╨▒╨╕╨▒╨╗╨╕╨╛╤В╨╡╨║╨░ тАФ ╨╛╤В╨┤╨╡╨╗╤М╨╜╤Л╨╣ ╨╝╨╛╨┤, ╨╕ ╨╡╨│╨╛ ╤Б╨╛╨╖╨┤╨░╤С╤В ╨╖╨░╨│╤А╤Г╨╖╤З╨╕╨║;
        //    ╨╜╨░ Fabric ╤Н╤В╨╛╨│╨╛ ╨╜╨╕╨║╤В╨╛ ╨╜╨╡ ╨┤╨╡╨╗╨░╨╗, ╨┐╨╛╤Н╤В╨╛╨╝╤Г `GuideME.<clinit>` (╤А╨╡╨│╨╕╤Б╤В╤А╨░╤Ж╨╕╤П
        //    ╨┐╤А╨╡╨┤╨╝╨╡╤В╨░ `guideme:guide`) ╤Б╤А╨░╨▒╨░╤В╤Л╨▓╨░╨╗ ╨╗╨╡╨╜╨╕╨▓╨╛ тАФ ╨┐╤А╨╕ ╨┐╨╡╤А╨▓╨╛╨╝ ╤А╨╡╨╜╨┤╨╡╤А╨╡
        //    SimpleRenderContext, ╤В╨╛ ╨╡╤Б╤В╤М ╤Г╨╢╨╡ ╨┐╨╛╤Б╨╗╨╡ ╨╖╨░╨╝╨╛╤А╨╛╨╖╨║╨╕ ╤А╨╡╨╡╤Б╤В╤А╨╛╨▓. ╨в╨╛╨│╨┤╨░
        //    `Item.<init>` ╨┐╨░╨┤╨░╨╗ ╤Б "This registry can't create intrusive holders".
        //    ╨Я╨╛╨┤╨╜╨╕╨╝╨░╨╡╨╝ ╨╖╨┤╨╡╤Б╤М тАФ ╨▓ ╤В╨╛╨╝ ╨╢╨╡ ╨╛╨║╨╜╨╡, ╨│╨┤╨╡ AE2 ╤А╨╡╨│╨╕╤Б╤В╤А╨╕╤А╤Г╨╡╤В ╤Б╨▓╨╛╨╕ ╨┐╤А╨╡╨┤╨╝╨╡╤В╤Л.
        //    ╨б╤В╤А╨╛╨│╨╛ ╨┤╨╛ runRegistrationPhases (╤Б╨╝. ╨╜╨╕╨╢╨╡) ╨╕ ╨┤╨╛ `new AppEngClient`.
        GuideMEBootstrap.init();

        // ╨а╨░╨╜╤В╨░╨╣╨╝-╤Б╨╛╨▒╤Л╤В╨╕╤П (╤В╨╕╨║╨╕, ╨╢╨╕╨╖╨╜╨╡╨╜╨╜╤Л╨╣ ╤Ж╨╕╨║╨╗ ╤Б╨╡╤А╨▓╨╡╤А╨░, ╨║╨╛╨╝╨░╨╜╨┤╤Л) тАФ ╤В╨╛, ╤З╤В╨╛ ╨╜╨░ NeoForge
        // ╤А╨░╤Б╤Б╤Л╨╗╨░╨╡╤В ╨╖╨░╨│╤А╤Г╨╖╤З╨╕╨║. ╨С╨╡╨╖ ╨╜╨╕╤Е ╨▓ ╨╛╨┤╨╕╨╜╨╛╤З╨╜╨╛╨╣ ╨╕╨│╤А╨╡ ╨╜╨╡ ╨▓╤Л╤Б╤В╨░╨▓╨╗╤П╨╡╤В╤Б╤П ╤В╨╡╨║╤Г╤Й╨╕╨╣ ╤Б╨╡╤А╨▓╨╡╤А,
        // ╨╜╨╡ ╤В╨╕╨║╨░╨╡╤В ╨╛╤З╨╡╤А╨╡╨┤╤М ╨▒╨╗╨╛╨║-╤Б╤Г╤Й╨╜╨╛╤Б╤В╨╡╨╣ ╨╕ ╨╜╨╡ ╤Б╤З╨╕╤В╨░╨╡╤В╤Б╤П ╨┐╨╕╤В╨░╨╜╨╕╨╡ ╤Б╨╡╤В╨╕.
        LifecycleBridge.install();

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ServerLifecycleHooks.setCurrentServer(null));

        new AppEngClient(NeoForge.EVENT_BUS, NfCompat.container());

        // 0b. ╨Ъ╨╗╨╕╨╡╨╜╤В╤Б╨║╨╕╨╡ ╤А╨░╨╜╤В╨░╨╣╨╝-╤Б╨╛╨▒╤Л╤В╨╕╤П (╤В╨╕╨║╨╕, ╨▓╤Е╨╛╨┤ ╨▓ ╨╝╨╕╤А, ╨║╨╗╨░╨▓╨╕╤И╨╕, ╤А╨╡╨╜╨┤╨╡╤А╨╡╤А╤Л,
        //    ╤З╨░╤Б╤В╨╕╤Ж╤Л, ╨║╨╛╨╝╨░╨╜╨┤╤Л). ╨Э╨░ NeoForge ╨╕╤Е ╤А╨░╤Б╤Б╤Л╨╗╨░╨╡╤В ╤Б╨░╨╝ ╨╖╨░╨│╤А╤Г╨╖╤З╨╕╨║; ╨▒╨╡╨╖ ╨╜╨╕╤Е
        //    ╨║╨╗╨╕╨╡╨╜╤В AE2 ╨╜╨░╨┐╨╛╨╗╨╛╨▓╨╕╨╜╤Г ╨╝╤С╤А╤В╨▓: ╨╜╨╡╤В ╤В╨╕╨║╨╛╨▓ ╨║╨░╨▒╨╡╨╗╨╡╨╣, ╨╜╨╡╤В BER-╨╛╨▓ ╨▒╨╗╨╛╨║╨╛╨▓.
        //    ╨б╤В╨░╨▓╨╕╨╝ ╨┐╨╛╤Б╨╗╨╡ ╨║╨╛╨╜╤Б╤В╤А╤Г╨║╤В╨╛╤А╨░: ╤З╨░╤Б╤В╤М ╤Б╨╛╨▒╤Л╤В╨╕╨╣ ╤А╨░╤Б╤Б╤Л╨╗╨░╨╡╤В╤Б╤П ╤Б╤А╨░╨╖╤Г.
        ClientEventBridge.install();

        // GuideME ╨┐╨╛╨┤╨╜╤П╤В ╨▓ ╨╜╨░╤З╨░╨╗╨╡ ╨╝╨╡╤В╨╛╨┤╨░ (GuideMEBootstrap.init): ╨║╨╜╨╛╨┐╨║╨░ ┬л?┬╗ ╨╕ ╨╛╤В╨║╤А╤Л╤В╨╕╨╡
        // ╨│╨░╨╣╨┤╨░ ╤А╨░╨▒╨╛╤В╨░╤О╤В ╤И╤В╨░╤В╨╜╨╛, ╨╛╤В╨┤╨╡╨╗╤М╨╜╨╛╨╣ ╨╖╨░╨│╨╗╤Г╤И╨║╨╕ ╨┤╨╗╤П `GuidesCommon.openGuide` ╨▒╨╛╨╗╤М╤И╨╡ ╨╜╨╡╤В.

        // 1. ╨Ю╨▒╤Й╨╕╨╡ ╤Н╤В╨░╨┐╤Л: ╤А╨╡╨│╨╕╤Б╤В╤А╨░╤Ж╨╕╤П ╤А╨╡╨╡╤Б╤В╤А╨╛╨▓, ╤В╨╕╨┐╨╛╨▓ ╨┐╨░╨║╨╡╤В╨╛╨▓, capability.
        NfCompat.runRegistrationPhases();

        // 2. ╨Ъ╨╗╨╕╨╡╨╜╤В╤Б╨║╨╕╨╡ ╨╛╨▒╤А╨░╨▒╨╛╤В╤З╨╕╨║╨╕ тАФ ╤В╨╛╨╗╤М╨║╨╛ ╨┐╨╛╤Б╨╗╨╡ ╤В╨╛╨│╨╛, ╨║╨░╨║ ╤В╨╕╨┐╤Л ╨┐╨░╨║╨╡╤В╨╛╨▓ ╨╕╨╖╨▓╨╡╤Б╤В╨╜╤Л.
        NeoForge.EVENT_BUS.post(new RegisterClientPayloadHandlersEvent());

        // 2b. ╨д╨░╨▒╤А╨╕╨║╨╕ ╨║╨╗╨╕╨╡╨╜╤В╤Б╨║╨╕╤Е ╤В╤Г╨╗╤В╨╕╨┐╨╛╨▓. NeoForge'╨▓╤Б╨║╨╕╨╣ ClientTooltipComponent.create ╨╜╨╡
        //     ╨╖╨╜╨░╨╡╤В ╤З╤Г╨╢╨╕╤Е ╤В╨╕╨┐╨╛╨▓, ╨░ ╤Г Fabric ╨┤╨╗╤П ╤Н╤В╨╛╨│╨╛ ╨╡╤Б╤В╤М ╨╛╤Д╨╕╤Ж╨╕╨░╨╗╤М╨╜╤Л╨╣
        //     TooltipComponentCallback (╤В╨░╨║ ╨╢╨╡ ╤Б╨┤╨╡╨╗╨░╨╜╨╛ ╨▓ ╨┐╨╛╤А╤В╨╡ AE2 ╨┐╨╛╨┤ 1.20.1).
        NeoForge.EVENT_BUS.post(new RegisterClientTooltipComponentFactoriesEvent(
                nfcompat.client.TooltipFactories.FACTORIES));

        // 2c. ╨н╨║╤А╨░╨╜╤Л ╨╝╨╡╨╜╤О. NeoForge ╨┐╨░╤В╤З╨╕╤В MenuScreens#register ╨▓ public; ╤Г ╨╜╨░╤Б ╨╝╨╡╤В╨╛╨┤
        //     ╨╛╤В╨║╤А╤Л╤В ╤З╨╡╤А╨╡╨╖ accesswidener, ╨┐╨╛╤Н╤В╨╛╨╝╤Г ╨║╨░╤А╤В╤Г ╨╕╨╖ ╤Б╨╛╨▒╤Л╤В╨╕╤П ╨┐╤А╨╕╨╝╨╡╨╜╤П╨╡╨╝ ╤Б╨░╨╝╨╕.
        var screens = new java.util.HashMap<MenuType<?>, MenuScreens.ScreenConstructor<?, ?>>();
        NeoForge.EVENT_BUS.post(new RegisterMenuScreensEvent(screens));
        screens.forEach((type, ctor) -> {
            @SuppressWarnings({ "unchecked", "rawtypes" })
            var unused = registerScreen((MenuType) type, (MenuScreens.ScreenConstructor) ctor);
        });

        // 2d. ╨Ъ╨╗╨╕╨╡╨╜╤В╤Б╨║╨╕╨╡ reload-╤Б╨╗╤Г╤И╨░╤В╨╡╨╗╨╕ (╤Б╤В╨╕╨╗╨╕ ╤Н╨║╤А╨░╨╜╨╛╨▓ AE2, ╨╝╨╛╨┤╨╡╨╗╨╕ ╤З╨░╤Б╤В╨╡╨╣).
        //     ╨Э╨░ NeoForge ╤Б╨╛╨▒╤Л╤В╨╕╨╡ ╨┐╨╛╨╗╤Г╤З╨░╨╡╤В ╨╜╨░╤Б╤В╨╛╤П╤Й╨╕╨╣ ReloadableResourceManager; ╨╜╨░ Fabric
        //     ╤Б╨╗╤Г╤И╨░╤В╨╡╨╗╨╡╨╣ ╨╜╨░╨┤╨╛ ╨╛╤В╨┤╨░╤В╤М ResourceManagerHelper. ╨С╨╡╨╖ ╤Н╤В╨╛╨│╨╛ StyleManager ╨╜╨╡
        //     ╨┐╨╛╨╗╤Г╤З╨░╨╡╤В ResourceManager ╨╕ ╨╛╤В╨║╤А╤Л╤В╨╕╨╡ ╨╗╤О╨▒╨╛╨│╨╛ ╤Н╨║╤А╨░╨╜╨░ AE2 ╨┐╨░╨┤╨░╨╡╤В тАФ
        //     ╨║╨╗╨╕╨╡╨╜╤В ╨▓╤Л╨║╨╕╨┤╤Л╨▓╨░╨╡╤В ╤Б Network Protocol Error.
        nfcompat.client.ReloadListeners.registerAll();

        // 2e. ╨Ъ╨░╤Б╤В╨╛╨╝╨╜╤Л╨╡ ╤Н╨╗╨╡╨╝╨╡╨╜╤В╤Л item-╨╝╨╛╨┤╨╡╨╗╨╡╨╣ (ae2:color, ae2:energy_fill_level, ae2:facade ...).
        //     ╨Т 26.3 ╨▓╨░╨╜╨╕╨╗╤М ╤Г╨╝╨╡╨╡╤В ╨┤╨╕╤Б╨┐╨╡╤В╤З╨╡╤А╨╕╨╖╨░╤Ж╨╕╤О ╨┐╨╛ "type", ╨╜╨╛ ╨╝╨░╨┐╨┐╨╡╤А╤Л ╤В╨╕╨┐╨╛╨▓ ╨┐╤А╨╕╨▓╨░╤В╨╜╤Л╨╡ ╨╕
        //     ╨┐╤Г╤Б╤В╤Л╨╡: NeoForge ╨▓ ╨║╨╛╨╜╤Ж╨╡ ╤Б╨▓╨╛╨╡╨│╨╛ bootstrap() ╨┐╨╛╤Б╤В╨╕╤В ╤Н╤В╨╕ ╤Б╨╛╨▒╤Л╤В╨╕╤П, ╨░ ╨╝╨╛╨┤╤Л ╨▓ ╨╜╨╕╤Е
        //     ╤А╨╡╨│╨╕╤Б╤В╤А╨╕╤А╤Г╤О╤В ╤Б╨▓╨╛╨╕ ╤В╨╕╨┐╤Л. ╨Я╨╛╨╗╤П ╨╛╤В╨║╤А╤Л╤В╤Л ╤З╨╡╤А╨╡╨╖ accesswidener (ae2.accesswidener).
        NeoForge.EVENT_BUS.post(new RegisterItemModelsEvent(ItemModels.ID_MAPPER));
        NeoForge.EVENT_BUS.post(new RegisterColorHandlersEvent.ItemTintSources(ItemTintSources.ID_MAPPER));
        NeoForge.EVENT_BUS.post(new RegisterRangeSelectItemModelPropertyEvent(
                RangeSelectItemModelProperties.ID_MAPPER));

        // 2e2. ╨Ъ╨░╤Б╤В╨╛╨╝╨╜╤Л╨╡ blockstate-╨╝╨╛╨┤╨╡╨╗╨╕ (ae2:crafting_cube, ae2:drive, ae2:cable_bus ...).
        //      ╨Т╨░╨╜╨╕╨╗╤М ╨╜╨╡ ╨┐╤А╨╕╨╜╨╕╨╝╨░╨╡╤В ╨╛╨┤╨╕╨╜╨╛╤З╨╜╤Л╨╣ ╨╛╨▒╤К╨╡╨║╤В ╤Б "type" ╨▓ ╨▓╨░╤А╨╕╨░╨╜╤В╨╡; NeoForge ╤Н╤В╨╛
        //      ╤А╨░╨╖╤А╨╡╤И╨░╨╡╤В ╤Б╨▓╨╛╨╕╨╝ ╨║╨╛╨┤╨╡╨║╨╛╨╝. ╨в╨╕╨┐╤Л ╤А╨╡╨│╨╕╤Б╤В╤А╨╕╤А╤Г╨╡╤В AE2, ╨░ ╤З╨╕╤В╨░╨╡╤В ╨║╨╛╨┤╨╡╨║ ╨╕╨╖ ╨╜╨░╤И╨╕╤Е
        //      ╨╝╨░╨┐╨┐╨╡╤А╨╛╨▓ (╤Б╨╝. nfcompat.mixin.SimpleModelSelectorsMixin / MultipartSelectorMixin).
        NeoForge.EVENT_BUS.post(new RegisterBlockStateModels(
                BlockStateModelHooks.BLOCK_STATE_MODEL_IDS,
                BlockStateModelHooks.BLOCK_MODEL_DEFINITION_IDS));

        // 2e3. ╨Ъ╨░╤Б╤В╨╛╨╝╨╜╤Л╨╡ ╤А╨╡╨╜╨┤╨╡╤А╨╡╤А╤Л ╨╛╨║╤А╤Г╨╢╨╡╨╜╨╕╤П (╨╜╨╡╨▒╨╛/╨╛╨▒╨╗╨░╨║╨░/╨┐╨╛╨│╨╛╨┤╨░ ╨╕╨╖╨╝╨╡╤А╨╡╨╜╨╕╤П). ╨Э╨░ NeoForge
        //      ╤Б╨╛╨▒╤Л╤В╨╕╨╡ ╨┐╨╛╤Б╤В╨╕╤В ╤Б╨░╨╝ ╨╖╨░╨│╤А╤Г╨╖╤З╨╕╨║; ╤А╨╡╨╜╨┤╨╡╤А╨╡╤А╤Л ╤Б╨║╨╗╨░╨┤╤Л╨▓╨░╤О╤В╤Б╤П ╨▓ ╨║╨░╤А╤В╤Л, ╨░ ╤Б╨▓╤П╨╖╤М
        //      "╨╕╨╖╨╝╨╡╤А╨╡╨╜╨╕╨╡ -> id ╤А╨╡╨╜╨┤╨╡╤А╨╡╤А╨░" ╨╝╨╛╨┤ ╨┤╨╡╨╗╨░╨╡╤В ╤Б╨░╨╝ (EnvironmentRenderers.bind),
        //      ╨┐╨╛╤В╨╛╨╝╤Г ╤З╤В╨╛ ╨░╤В╤А╨╕╨▒╤Г╤В╨╛╨▓ neoforge:custom_* ╨▓ ╨▓╨░╨╜╨╕╨╗╤М╨╜╨╛╨╝ ╤А╨╡╨╡╤Б╤В╤А╨╡ ╨╜╨╡╤В.
        NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.client.event.RegisterCustomEnvironmentEffectRendererEvent(
                nfcompat.client.EnvironmentRenderers.cloudRenderers(),
                nfcompat.client.EnvironmentRenderers.skyboxRenderers(),
                nfcompat.client.EnvironmentRenderers.weatherRenderers()));

        // 2f. ╨У╤А╤Г╨┐╨┐╤Л ╤З╨░╤Б╤В╨╕╤Ж (`RegisterParticleGroupsEvent`) ╨╕ picture-in-picture
        //     ╤А╨╡╨╜╨┤╨╡╤А╨╡╤А╤Л (`RegisterPictureInPictureRenderersEvent`). ╨Э╨░ NeoForge ╨╕╤Е
        //     ╤А╨░╤Б╤Б╤Л╨╗╨░╨╡╤В ╨┐╨░╤В╤З `ParticleEngine`/`GameRenderer`; ╤Г ╨╜╨░╤Б ╨║╨░╤А╤В╤Л ╨║╨╛╨┐╤П╤В╤Б╤П ╨╖╨┤╨╡╤Б╤М,
        //     ╨░ ╨┐╤А╨╕╨╝╨╡╨╜╤П╤О╤В╤Б╤П ╨▓ nfcompat.mixin.client.ParticleEngineMixin / GameRendererMixin.
        //     ╨б╤В╤А╨╛╨│╨╛ ╨┤╨╛ ╤Б╨╛╨╖╨┤╨░╨╜╨╕╤П `ParticleEngine` ╨╕ `GameRenderer` (╨╛╨▒╨░ тАФ ╨╜╨░ ╨▓╤Е╨╛╨┤ ╨▓ ╨╝╨╕╤А/╤Б╤В╨░╤А╤В).
        nfcompat.client.ParticleGroups.install();
        nfcompat.client.PictureInPicture.install();

        // 3. ╨Ъ╨╗╨╕╨╡╨╜╤В╤Б╨║╨░╤П ╨╕╨╜╨╕╤Ж╨╕╨░╨╗╨╕╨╖╨░╤Ж╨╕╤П.
        var queue = new DeferredWorkQueue();
        NeoForge.EVENT_BUS.post(new FMLClientSetupEvent(NfCompat.container(), queue));
        for (Runnable work : queue.drain()) {
            work.run();
        }

        // 4. ╨Ъ╨╗╨╕╨╡╨╜╤В╤Б╨║╨╕╨╡ ╤А╨╡╨╡╤Б╤В╤А╤Л AE2 (╨╝╨╛╨┤╨╡╨╗╨╕ ╤З╨░╤Б╤В╨╡╨╣, ╨╝╨╛╨┤╨╡╨╗╨╕ ╤П╤З╨╡╨╡╨║ ╤Е╤А╨░╨╜╨╡╨╜╨╕╤П).
        //    ╨Э╨░ NeoForge ╤Б╨╛╨▒╤Л╤В╨╕╨╡ ╨┐╤А╨╕╤Е╨╛╨┤╨╕╤В ╨╕╨╖ Minecraft ╨┐╨╡╤А╨╡╨┤ ╨╖╨░╨┐╨╡╨║╨░╨╜╨╕╨╡╨╝ ╨╝╨╛╨┤╨╡╨╗╨╡╨╣; ╨▒╨╡╨╖ ╨╜╨╡╨│╨╛
        //    AppEngClient.partModels ╨╛╤Б╤В╨░╤С╤В╤Б╤П null, ╨╕ ╤А╨░╨╖╤А╨╡╤И╨╡╨╜╨╕╨╡ ╨╝╨╛╨┤╨╡╨╗╨╕ cable_bus ╨┐╨░╨┤╨░╨╡╤В
        //    ╤Б "Client registries have not been initialized yet" тАФ ╨┐╨░╨┤╨░╨╡╤В ╨▓╨╡╤Б╤М reload.
        NeoForge.EVENT_BUS.post(new InitializeClientRegistriesEvent());

        // 5. Standalone-╨╝╨╛╨┤╨╡╨╗╨╕ (`ModelEvent.RegisterStandalone`): ╨╝╨╛╨┤╨╡╨╗╤М ╤А╤Г╤З╨║╨╕ ╨║╤А╨╕╨▓╨╛╤И╨╕╨┐╨░ ╨╕
        //    ╨╝╨╛╨┤╨╡╨╗╨╕ ╤П╤З╨╡╨╡╨║ ╤Е╤А╨░╨╜╨╡╨╜╨╕╤П ╨┤╨╗╤П ME-╤Б╤Г╨╜╨┤╤Г╨║╨░. ╨б╤В╤А╨╛╨│╨╛ ╨┐╨╛╤Б╨╗╨╡ ╤И╨░╨│╨░ 4: ╨╛╨▒╤А╨░╨▒╨╛╤В╤З╨╕╨║ AE2
        //    ╨╛╨▒╤Е╨╛╨┤╨╕╤В `StorageCellModels`, ╨║╨╛╤В╨╛╤А╤Л╨╣ ╨╜╨░╨┐╨╛╨╗╨╜╤П╨╡╤В╤Б╤П ╨║╨░╨║ ╤А╨░╨╖ ╨▓
        //    `InitializeClientRegistriesEvent` тАФ ╨╕╨╜╨░╤З╨╡ ╨▓ ╨║╨░╤А╤В╨╡ ╨╛╨║╨░╨╢╨╡╤В╤Б╤П ╤В╨╛╨╗╤М╨║╨╛ ╨┤╨╡╤Д╨╛╨╗╤В╨╜╨░╤П
        //    ╨╝╨╛╨┤╨╡╨╗╤М ╤П╤З╨╡╨╣╨║╨╕. ╨Ш ╨┤╨╛ ╨┐╨╡╤А╨▓╨╛╨│╨╛ reload'╨░ ╤А╨╡╤Б╤Г╤А╤Б╨╛╨▓ (╨║╨╗╨╕╨╡╨╜╤В╤Б╨║╨░╤П ╨╕╨╜╨╕╤Ж╨╕╨░╨╗╨╕╨╖╨░╤Ж╨╕╤П
        //    ╨╖╨░╨│╤А╤Г╨╖╤З╨╕╨║╨░ ╤Б╨╗╤Г╤З╨░╨╡╤В╤Б╤П ╤А╨░╨╜╤М╤И╨╡), ╨╕╨╜╨░╤З╨╡ Fabric-╨┐╨╗╨░╨│╨╕╨╜ ╨╕╤Е ╨╜╨╡ ╤Г╨▓╨╕╨┤╨╕╤В.
        //    ╨Ч╨░╨┐╨╡╨║╨░╨╜╨╕╨╡ ╨┤╨╡╨╗╨░╨╡╤В ╤Б╨░╨╝ Fabric: `ModelLoadingPlugin.Context#addModel` ╨║╨╗╨░╨┤╤С╤В
        //    ╨╕╤Е ╨▓ `ModelDiscovery` ╨╕ ╨╛╨▒╤Й╨╕╨╣ `ModelBakery`, ╨░ `StandaloneModels.get(...)`
        //    ╤З╨╕╤В╨░╨╡╤В ╨│╨╛╤В╨╛╨▓╨╛╨╡ ╨╕╨╖ `ModelManager` (╨║╨░╤А╤В╨░ ╨┐╨╡╤А╨╡╤Б╨╛╨▒╨╕╤А╨░╨╡╤В╤Б╤П ╨╜╨░ ╨║╨░╨╢╨┤╨╛╨╝ reload'╨╡).
        nfcompat.client.StandaloneModels.install();
        NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.client.event.ModelEvent.RegisterStandalone(
                nfcompat.client.StandaloneModels.models()));
        System.out.println("[nfcompat] standalone-╨╝╨╛╨┤╨╡╨╗╨╡╨╣ AE2: "
                + nfcompat.client.StandaloneModels.models().size());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static boolean registerScreen(MenuType type, MenuScreens.ScreenConstructor ctor) {
        MenuScreens.register(type, ctor);
        return true;
    }
}
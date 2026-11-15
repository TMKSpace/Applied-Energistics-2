/*
 * Часть nfcompat. Клиентские рантайм-события NeoForge.
 *
 * На NeoForge загрузчик сам рассылает всё, что происходит после старта: тики
 * клиента, вход в мир, регистрацию клавиш, рендереров, частиц и так далее.
 * На Fabric этого нет ни для чего, кроме пары хуков, поэтому AE2 в клиенте
 * молча не инициализировал половину вещей: не было тиков (не обновлялся режим
 * отображения кабелей, не проверялись хоткеи), не регистрировались BER'ы
 * (блоки-механизмы не рисовались бы вовсе), не было слоёв моделей (падение на
 * bakeLayer), не было клавиш в настройках управления.
 *
 * Здесь собраны события, которые можно разослать из Fabric-хуков напрямую.
 * События, которым нужен хук внутри ванили (рендереры, клавиши, частицы,
 * тинты), рассылаются из миксинов, но сама рассылка и применение результата
 * всё равно здесь — чтобы вся логика моста лежала в одном месте.
 */
package nfcompat;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import net.minecraft.client.Options;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleResources;
import net.minecraft.core.particles.ParticleType;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class ClientEventBridge {
    private static boolean installed;

    /** `EntityRenderersEvent.RegisterRenderers` имеет право разослаться только один раз. */
    private static boolean renderersPosted;

    private ClientEventBridge() {
    }

    /**
     * Вешает слушателей Fabric. Вызывать после конструктора AppEngClient: часть
     * событий (например, client extensions) рассылается сразу, и без уже
     * зарегистрированных слушателей AE2 их не увидит.
     */
    public static void install() {
        if (installed) {
            return;
        }
        installed = true;

        // --- Тики клиента ---
        // От них зависит обновление режима отображения кабелей (фасады/прозрачность),
        // очистка закреплённых ключей в терминалах и обработка хоткеев AE2.
        ClientTickEvents.START_CLIENT_TICK.register(client ->
                NeoForge.EVENT_BUS.post(new ClientTickEvent.Pre()));
        ClientTickEvents.END_CLIENT_TICK.register(client ->
                NeoForge.EVENT_BUS.post(new ClientTickEvent.Post()));

        // --- Вход/выход из мира ---
        // LoggingIn чистит очередь крафта и закреплённые ключи: без него они
        // переезжают из одного мира в другой.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                NeoForge.EVENT_BUS.post(new ClientPlayerNetworkEvent.LoggingIn(
                        client.gameMode, client.player, handler.getConnection())));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                NeoForge.EVENT_BUS.post(new ClientPlayerNetworkEvent.LoggingOut(
                        client.gameMode, client.player, handler.getConnection())));

        // --- Клиентские команды (/ae2client ...) ---
        // У Fabric диспетчер типизирован FabricClientCommandSource, у NeoForge -
        // CommandSourceStack. Приводим к сырому типу: AE2 регистрирует только
        // литералы, а source ему нужен лишь на этапе выполнения отладочных команд.
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> {
            @SuppressWarnings({ "unchecked", "rawtypes" })
            var neoDispatcher = (com.mojang.brigadier.CommandDispatcher) dispatcher;
            NeoForge.EVENT_BUS.post(new RegisterClientCommandsEvent(neoDispatcher, buildContext));
        });

        // --- Client extensions (CableBusBlockClientExtensions) ---
        // У нас событие складывает расширения в свою статическую мапу, поэтому
        // достаточно один раз его разослать.
        NeoForge.EVENT_BUS.post(new RegisterClientExtensionsEvent());

        // --- Пайплайны рендера (area overlay, lightning fx, spatial skybox) ---
        postRenderPipelines();
    }

    /**
     * `RegisterRenderPipelinesEvent`.
     *
     * NeoForge патчит `RenderPipelines`, добавляя метод, который постит событие и
     * складывает пайплайны в те же две приватные карты, что использует ваниль
     * (`PIPELINES_BY_LOCATION` и `OPTIONAL_PIPELINES_BY_LOCATION`, открыты через
     * accesswidener). Делаем то же самое: до компиляции GPU-пайплайнов карты
     * должны быть наполнены, иначе AE2 рисует молнии/области несуществующим
     * пайплайном.
     *
     * В отличие от NeoForge не падаем на дубликате: в FABRIC-ветке AE2 два
     * константы (LINES_BEHIND_BLOCK, STORAGE_CELL_LEDS) специально указывают на
     * ванильные пайплайны, потому что `RenderPipeline#toBuilder` в 26.3 нет.
     */
    public static void postRenderPipelines() {
        NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent(
                (pipeline, optional) -> {
                    var registry = optional
                            ? net.minecraft.client.renderer.RenderPipelines.OPTIONAL_PIPELINES_BY_LOCATION
                            : net.minecraft.client.renderer.RenderPipelines.PIPELINES_BY_LOCATION;
                    registry.putIfAbsent(pipeline.getLocation(), pipeline);
                }));
    }

    // ------------------------------------------------------------------
    // Ниже — то, что вызывается из миксинов по ванильным хукам.
    // ------------------------------------------------------------------

    /**
     * `EntityRenderersEvent.RegisterRenderers`. NeoForge рассылает его перед тем,
     * как `EntityRenderers`/`BlockEntityRenderers` соберут карту провайдеров
     * рендереров; обработчик AE2 регистрирует и рендереры сущностей, и BER'ы,
     * поэтому одного раза хватает на обе карты.
     */
    public static void postRegisterRenderers() {
        if (renderersPosted) {
            return;
        }
        renderersPosted = true;
        NeoForge.EVENT_BUS.post(new EntityRenderersEvent.RegisterRenderers());

        // Диагностика: если BER кабеля не попал в ванильную карту, кабели рисует
        // не модель, а блок-сущность — и мы увидим только хитбокс.
        var providers = net.minecraft.client.renderer.blockentity.BlockEntityRenderers.PROVIDERS;
        System.out.println("[nfcompat] BER-провайдеров: " + providers.size()
                + ", cable_bus: " + providers.containsKey(appeng.core.definitions.AEBlockEntities.CABLE_BUS.get())
                + ", drive: " + providers.containsKey(appeng.core.definitions.AEBlockEntities.DRIVE.get()));
    }

    /**
     * `EntityRenderersEvent.RegisterLayerDefinitions`. На NeoForge событие
     * приходит до сборки карты слоёв моделей; у нас его постим в начале
     * LayerDefinitions#createRoots, а применяем в конце
     * ({@link #withModdedLayerDefinitions}). Без этого bakeLayer для модели
     * сундука AE2 кидает IllegalArgumentException.
     */
    public static void postRegisterLayerDefinitions() {
        NeoForge.EVENT_BUS.post(new EntityRenderersEvent.RegisterLayerDefinitions());
    }

    /**
     * Докладывает в собранную ванилью карту слоёв то, что зарегистрировали моды.
     * Ванильная карта immutable, поэтому возвращаем новую.
     */
    public static Map<ModelLayerLocation, LayerDefinition> withModdedLayerDefinitions(
            Map<ModelLayerLocation, LayerDefinition> vanilla) {
        Map<ModelLayerLocation, Supplier<LayerDefinition>> extra =
                net.neoforged.neoforge.client.ClientHooks.getLayerDefinitions();
        if (extra.isEmpty()) {
            return vanilla;
        }
        var merged = new HashMap<>(vanilla);
        extra.forEach((location, supplier) -> merged.put(location, supplier.get()));
        return merged;
    }

    /**
     * `RegisterKeyMappingsEvent`. NeoForge постит его из конструктора `Options`
     * перед `Options#load`, чтобы раскладка мода сразу подхватилась из options.txt.
     * Кроме самих клавиш надо доложить категорию AE2 в `KeyMapping.Category.SORT_ORDER`,
     * иначе её не будет в списке категорий экрана управления.
     */
    public static void postKeyMappings(Options options) {
        var event = new RegisterKeyMappingsEvent(options);
        NeoForge.EVENT_BUS.post(event);
        event.sortAndStoreCategories(net.minecraft.client.KeyMapping.Category.SORT_ORDER);
    }

    /**
     * `RegisterColorHandlersEvent.BlockTintSources`. Рассылается на ванильной
     * фабрике `BlockColors#createDefault`: AE2 регистрирует там тинты для
     * беспроводной точки доступа, кабельной шины и ME-сундука.
     */
    public static void postBlockTintSources(BlockColors blockColors) {
        NeoForge.EVENT_BUS.post(new RegisterColorHandlersEvent.BlockTintSources(blockColors));
    }

    /**
     * `RegisterParticleProvidersEvent`.
     *
     * NeoForge подменяет `ParticleResources#register` на своё событие. У нас
     * событие есть, но применить его результат к ванили надо руками: у
     * `ParticleResources` для этого ровно те же два приватных метода, которые
     * открыты через accesswidener. Фабрика со спрайт-сетом должна получать
     * свежий `MutableSpriteSet` на каждый экземпляр `ParticleResources`
     * (он создаётся заново при каждом мире), поэтому регистрируем через
     * `register(type, SpriteParticleRegistration)`, а не в обход.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void applyParticleProviders(ParticleResources resources) {
        var event = new RegisterParticleProvidersEvent();
        NeoForge.EVENT_BUS.post(event);

        // Провайдеры без спрайтов (в AE2 таких нет, но событие их допускает).
        event.getSpecialProviders().forEach((type, provider) ->
                resources.register((ParticleType) type, (ParticleProvider) provider));

        // Провайдеры со спрайт-сетом: фабрика из события получает набор спрайтов.
        event.getSpriteProviders().forEach((type, factory) ->
                resources.register((ParticleType) type, new ParticleResources.SpriteParticleRegistration() {
                    @Override
                    public ParticleProvider create(net.minecraft.client.particle.SpriteSet spriteSet) {
                        return (ParticleProvider) factory.apply(spriteSet);
                    }
                }));
    }
}

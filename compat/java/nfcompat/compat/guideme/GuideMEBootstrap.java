/*
 * Часть nfcompat. Подъём GuideME в правильном окне инициализации.
 *
 * Проблема. GuideME — отдельная библиотека (org.appliedenergistics:guideme), на NeoForge
 * это самостоятельный мод: загрузчик сам создаёт `new GuideME(шина)` и
 * `new GuideMEClient(контейнер, шина)`. У нас такого нет, и классы GuideME
 * инициализировались ЛЕНИВО — при первом обращении из рендера AE2
 * (`MEStorageScreen.renderLinkStatus` -> `new SimpleRenderContext(...)` ->
 * `GuideMEClient.currentLightDarkMode()`). К этому моменту реестры уже заморожены,
 * а `<clinit>` GuideME регистрирует предмет `guideme:guide` через DeferredRegister,
 * который в шиме регистрирует нетерпеливо: `Item.<init>` пробует создать intrusive
 * holder и падает с "This registry can't create intrusive holders".
 *
 * Решение. Поднимаем GuideME в начале `onInitializeClient()` — там же, где AE2
 * регистрирует свои предметы, то есть до Bootstrap/BootStrap-заморозки.
 *
 * Порядок внутри метода важен:
 *  1. `new GuideME(bus)` — предмет, компонент `guideme:guide_id`, типы аргументов
 *     команд, слушатели `RegisterCommandsEvent` / `RegisterPayloadHandlersEvent` /
 *     `OnDatapackSyncEvent`. Слушатель `RegisterEvent` для звука клика GuideME
 *     регистрирует клиентский конструктор, а событие мы постим позже
 *     (`NfCompat.runRegistrationPhases`), так что порядок сходится.
 *  2. `new GuideMEClient(контейнер, bus)` — клиентский прокси (`GuideME.PROXY`),
 *     конфиг `guideme.toml`, reload-слушатель гайдов (без него `GuideRegistry`
 *     пуст и гайд не открывается), клавиши, рендереры и т.п.
 *
 * Всё в try/catch(Throwable): если библиотека не поднялась, клиент должен жить
 * дальше (тогда в логе будет понятная причина, а не стек из `Item.<init>`).
 */
package nfcompat.compat.guideme;

import net.neoforged.neoforge.common.NeoForge;

import guideme.internal.GuideME;
import guideme.internal.GuideMEClient;
import guideme.internal.hotkey.OpenGuideHotkey;
import nfcompat.NfCompat;

public final class GuideMEBootstrap {

    /** Должно совпадать с версией зависимости в build.gradle (guideme_version). */
    private static final String VERSION = "26.3.7-alpha";

    private static boolean initialized;
    private static boolean hotkeyWarned;

    private GuideMEBootstrap() {
    }

    /**
     * Идемпотентно. Вызывать строго до `NfCompat.runRegistrationPhases()` и до
     * заморозки реестров (то есть в начале клиентской инициализации).
     */
    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        // 1. Общая часть: предмет `guideme:guide` + типы аргументов команд.
        //    Именно она раньше падала на замороженном реестре предметов.
        try {
            new GuideME(NeoForge.EVENT_BUS);
            System.out.println("[nfcompat] GuideME: предмет и команды зарегистрированы");
        } catch (Throwable t) {
            System.out.println("[nfcompat] GuideME не поднялся: " + t);
            t.printStackTrace(System.out);
        }

        // 2. Клиентская часть. Нужна даже если шаг 1 упал: `SimpleRenderContext`
        //    (кнопка «?»/статус связи в терминалах) дёргает
        //    `GuideMEClient.currentLightDarkMode()` и без инициализации класса
        //    валит рендер с NoClassDefFoundError.
        try {
            new GuideMEClient(NfCompat.container("guideme", "GuideME", VERSION), NeoForge.EVENT_BUS);
            System.out.println("[nfcompat] GuideME: клиент поднят (прокси, конфиг, reload-слушатель)");
        } catch (Throwable t) {
            System.out.println("[nfcompat] GuideME-клиент не поднялся: " + t);
            t.printStackTrace(System.out);
        }
    }

    /**
     * Обёртка над `OpenGuideHotkey.init()` — вызывается миксином вместо прямого вызова
     * из конструктора `GuideMEClient`.
     *
     * Зачем: `<clinit>` этого класса создаёт `KeyMapping` неофорджевским конструктором
     * `(String, IKeyConflictContext, Type, int, Category)`. В ванильном `KeyMapping` такого
     * конструктора нет (Mixin не умеет добавлять конструкторы), поэтому на Fabric это
     * NoSuchMethodError. Само по себе оно не смертельно: хоткей «зажми клавишу у предмета»
     * просто не появится, а гайд открывается кнопкой/предметом/командой.
     *
     * Ловим по месту вызова, а не гейтим весь GuideME: остальная часть конструктора
     * (прокси, конфиг, reload-слушатель) должна доделаться — иначе гайд не откроется.
     */
    public static void initHotkey() {
        try {
            OpenGuideHotkey.init();
        } catch (Throwable t) {
            if (!hotkeyWarned) {
                hotkeyWarned = true;
                System.out.println("[nfcompat] хоткей GuideME пропущен: в ванильном KeyMapping нет"
                        + " неофорджевского конструктора с IKeyConflictContext (" + t + ")");
            }
        }
    }
}

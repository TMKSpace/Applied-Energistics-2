/*
 * Часть nfcompat (GuideME). Конструктор `KeyMapping` без `IKeyConflictContext`.
 *
 * ЗАЧЕМ. GuideME собирает горячую клавишу гайда через неофорджевский конструктор
 * `KeyMapping(String, IKeyConflictContext, InputConstants.Type, int, KeyMapping.Category)`
 * (NeoForge добавляет контекст конфликтов). В ванили 26.3 конструктора с контекстом нет —
 * есть только `(String, Type, int, Category)`.
 *
 * ЧТО БЫЛО. Мы просто глотали `NoSuchMethodError` в `GuideMEBootstrap.initHotkey`, и это
 * работало ровно до первого обращения к классу: `<clinit>` падал с `ExceptionInInitializerError`,
 * класс навсегда помечался ошибочным, и следующее обращение (из `GuideMEClient.registerHotkeys`
 * при инициализации `Options`) роняло клиент на
 * `NoClassDefFoundError: Could not initialize class OpenGuideHotkey`.
 *
 * ЧТО ДЕЛАЕМ. Миксин-редирект на `NEW KeyMapping` внутри `OpenGuideHotkey.<clinit>` подменяет
 * создание объекта на ванильный конструктор (контекст конфликтов на Fabric влияет только на
 * подсветку конфликтов в настройках управления, поэтому просто теряется). Созданная клавиша
 * запоминается здесь же: её же отдаёт `GuideMEHotkeyMixin` вместо `OpenGuideHotkey.getHotkey()`,
 * чтобы `registerHotkeys` не загружал класс вообще, даже если `<clinit>` почему-то не сработал.
 */
package nfcompat.compat.guideme;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;

import org.jetbrains.annotations.Nullable;

public final class GuideMEHotkeyCompat {

    private static final String DEFAULT_DESCRIPTION = "key.guideme.guide";
    private static final int DEFAULT_KEY = 71; // GLFW_KEY_G, как в GuideME

    @Nullable
    private static volatile KeyMapping mapping;

    private GuideMEHotkeyCompat() {
    }

    /** Вызывается из редиректа `NEW KeyMapping` в `OpenGuideHotkey.<clinit>`. */
    public static KeyMapping create(String description, IKeyConflictContext context,
            InputConstants.Type type, int keyCode, KeyMapping.Category category) {
        var created = new KeyMapping(description, type, keyCode, category);
        mapping = created;
        return created;
    }

    /**
     * {@return клавиша гайда} Если `OpenGuideHotkey` так и не инициализировался (например,
     * его `<clinit>` упал по другой причине), отдаём свою — зарегистрировать её в настройках
     * управления безопасно, обработчика у неё просто не будет.
     */
    public static KeyMapping mapping() {
        var current = mapping;
        if (current == null) {
            current = new KeyMapping(DEFAULT_DESCRIPTION, InputConstants.Type.KEYBOARD, DEFAULT_KEY,
                    guideme.internal.GuideMEClient.KEYBIND_CATEGORY);
            mapping = current;
        }
        return current;
    }
}

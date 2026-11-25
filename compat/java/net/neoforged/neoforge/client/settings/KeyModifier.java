/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

/*
 * Часть nfcompat. Шим `net.neoforged.neoforge.client.settings.KeyModifier`.
 *
 * Сверено с NeoForge 26.3.x (src/client/java/net/neoforged/neoforge/client/settings/KeyModifier.java).
 * Взят весь набор констант (CONTROL, CONTROL_OR_COMMAND, SHIFT, ALT, NONE), `matches`,
 * `isActive(IKeyConflictContext)`, `getCombinedName`, `codes`, `getActiveModifiers`,
 * `getKeyModifier`, `isKeyCodeModifier`, `valueFromString` и `MODIFIER_VALUES`.
 *
 * Кому нужно: extendedae_plus зовёт `KeyModifier.CONTROL` и `KeyModifier.NONE` в
 * конструкторе `KeyMapping` (см. «расхождения», п.1).
 *
 * СОЗНАТЕЛЬНЫЕ РАСХОЖДЕНИЯ:
 *  1. `KeyModifier.isActive(InputConstants.Key)` — такого метода в NeoForge НЕТ (там он
 *     называется `matches`); добавлен как удобная обёртка (`matches && isActive(null)`),
 *     потому что именно так его описал заказчик. Обратная сторона: голый `isActive(null)`
 *     теперь неоднозначен, поэтому ВНУТРИ файла такие вызовы пишутся с приведением
 *     `isActive((IKeyConflictContext) null)`. Если будете копировать сюда код NeoForge
 *     (например патч `KeyMapping`) — там `isActive(null)` встречается, приводите тип.
 *  2. В NeoForge порядок модификаторов зависит от `InputQuirks.ON_OSX`. В нашей 26.3
 *     константы `ON_OSX` нет, поэтому «мы на маке» определяем по
 *     `InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY` — это тот же самый признак
 *     (именно он и переключает CONTROL на Command).
 *  3. `KeyMapping` с NeoForge-ным конструктором `(String, IKeyConflictContext, KeyModifier,
 *     InputConstants.Type, int, Category)` у нас пока НЕТ: это патч ванильного класса,
 *     а не класс шима. Пока его нет, extendedae_plus упадёт на инициализации
 *     `ModKeybindings` (NoSuchMethodError на конструкторе), хотя сам этот enum уже есть.
 */
package net.neoforged.neoforge.client.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.InputQuirks;
import net.minecraft.network.chat.Component;

import org.jspecify.annotations.Nullable;

public enum KeyModifier {
    /**
     * Всегда Control, даже на OSX.
     */
    CONTROL {
        private static final InputConstants.Key[] KEYS = new InputConstants.Key[] {
                InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_LCONTROL),
                InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_RCONTROL)
        };

        @Override
        public boolean matches(InputConstants.Key key) {
            int keyCode = key.getValue();
            return keyCode == InputConstants.KEY_LCONTROL || keyCode == InputConstants.KEY_RCONTROL;
        }

        @Override
        public boolean isActive(@Nullable IKeyConflictContext conflictContext) {
            return Minecraft.getInstance().hasControlDown();
        }

        @Override
        public Component getCombinedName(InputConstants.Key key, Supplier<Component> defaultLogic) {
            return Component.translatable("neoforge.controlsgui.control", defaultLogic.get());
        }

        @Override
        public InputConstants.Key[] codes() {
            return KEYS;
        }
    },
    /**
     * На Windows/Linux это Control, на OSX — Command (⌘).
     */
    CONTROL_OR_COMMAND {
        private static final InputConstants.Key[] COMMAND_KEYS = new InputConstants.Key[] {
                InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_LGUI),
                InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_RGUI),
        };

        @Override
        public boolean matches(InputConstants.Key key) {
            if (InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY) {
                int keyCode = key.getValue();
                return keyCode == InputConstants.KEY_LGUI || keyCode == InputConstants.KEY_RGUI;
            }
            return CONTROL.matches(key);
        }

        @Override
        public boolean isActive(@Nullable IKeyConflictContext conflictContext) {
            if (InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY) {
                return InputConstants.isKeyDown(InputConstants.KEY_LGUI)
                        || InputConstants.isKeyDown(InputConstants.KEY_RGUI);
            }
            return CONTROL.isActive(conflictContext);
        }

        @Override
        public Component getCombinedName(InputConstants.Key key, Supplier<Component> defaultLogic) {
            if (InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY) {
                return Component.translatable("neoforge.controlsgui.control.mac", defaultLogic.get());
            }
            return CONTROL.getCombinedName(key, defaultLogic);
        }

        @Override
        public InputConstants.Key[] codes() {
            if (InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY) {
                return COMMAND_KEYS;
            }
            return CONTROL.codes();
        }
    },
    SHIFT {
        private static final InputConstants.Key[] KEYS = new InputConstants.Key[] {
                InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_LSHIFT),
                InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_RSHIFT)
        };

        @Override
        public boolean matches(InputConstants.Key key) {
            return key.getValue() == InputConstants.KEY_LSHIFT || key.getValue() == InputConstants.KEY_RSHIFT;
        }

        @Override
        public boolean isActive(@Nullable IKeyConflictContext conflictContext) {
            return Minecraft.getInstance().hasShiftDown();
        }

        @Override
        public Component getCombinedName(InputConstants.Key key, Supplier<Component> defaultLogic) {
            return Component.translatable("neoforge.controlsgui.shift", defaultLogic.get());
        }

        @Override
        public InputConstants.Key[] codes() {
            return KEYS;
        }
    },
    ALT {
        private static final InputConstants.Key[] KEYS = new InputConstants.Key[] {
                InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_LALT),
                InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_RALT)
        };

        @Override
        public boolean matches(InputConstants.Key key) {
            return key.getValue() == InputConstants.KEY_LALT || key.getValue() == InputConstants.KEY_RALT;
        }

        @Override
        public boolean isActive(@Nullable IKeyConflictContext conflictContext) {
            return Minecraft.getInstance().hasAltDown();
        }

        @Override
        public Component getCombinedName(InputConstants.Key key, Supplier<Component> defaultLogic) {
            return Component.translatable("neoforge.controlsgui.alt", defaultLogic.get());
        }

        @Override
        public InputConstants.Key[] codes() {
            return KEYS;
        }
    },
    NONE {
        private static final InputConstants.Key[] KEYS = new InputConstants.Key[0];

        @Override
        public boolean matches(InputConstants.Key key) {
            return false;
        }

        @Override
        public boolean isActive(@Nullable IKeyConflictContext conflictContext) {
            if (conflictContext != null && !conflictContext.conflicts(KeyConflictContext.IN_GAME)) {
                for (KeyModifier keyModifier : MODIFIER_VALUES) {
                    if (keyModifier.isActive(conflictContext)) {
                        return false;
                    }
                }
            }
            return true;
        }

        @Override
        public Component getCombinedName(InputConstants.Key key, Supplier<Component> defaultLogic) {
            return defaultLogic.get();
        }

        @Override
        public InputConstants.Key[] codes() {
            return KEYS;
        }
    };

    public static final KeyModifier[] MODIFIER_VALUES = InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY
            ? new KeyModifier[] { SHIFT, CONTROL_OR_COMMAND, CONTROL, ALT }
            : new KeyModifier[] { SHIFT, CONTROL, ALT };

    public static List<KeyModifier> getActiveModifiers() {
        List<KeyModifier> modifiers = new ArrayList<>();
        for (KeyModifier keyModifier : MODIFIER_VALUES) {
            // Приведение типа обязательно: у нас есть ещё isActive(InputConstants.Key),
            // и голый null стал бы неоднозначным (см. шапку, п.1).
            if (keyModifier.isActive((IKeyConflictContext) null)) {
                modifiers.add(keyModifier);
            }
        }
        return modifiers;
    }

    public static KeyModifier getKeyModifier(InputConstants.Key key) {
        for (KeyModifier keyModifier : MODIFIER_VALUES) {
            if (keyModifier.matches(key)) {
                return keyModifier;
            }
        }
        return NONE;
    }

    public static boolean isKeyCodeModifier(InputConstants.Key key) {
        return getKeyModifier(key) != NONE;
    }

    public static KeyModifier valueFromString(String stringValue) {
        try {
            return valueOf(stringValue);
        } catch (NullPointerException | IllegalArgumentException ignored) {
            return NONE;
        }
    }

    public abstract boolean matches(InputConstants.Key key);

    public abstract boolean isActive(@Nullable IKeyConflictContext conflictContext);

    /**
     * Удобная обёртка, которой в NeoForge нет (см. шапку файла, п.1):
     * «нажата ли клавиша-модификатор и активен ли он сейчас».
     */
    public boolean isActive(InputConstants.Key key) {
        return matches(key) && isActive((IKeyConflictContext) null);
    }

    public abstract Component getCombinedName(InputConstants.Key key, Supplier<Component> defaultLogic);

    public abstract InputConstants.Key[] codes();
}

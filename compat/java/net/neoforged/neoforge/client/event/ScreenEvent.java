/*
 * Часть nfcompat. Неофорджевский `ScreenEvent` (клиентские события экранов).
 *
 * ЗАЧЕМ. GuideME подписывается на `ScreenEvent.Opening` в `GuideOnStartup.init()`
 * (показ гайда при входе). `extendedae_plus` слушает `ScreenEvent.KeyPressed.Pre` и
 * `ScreenEvent.MouseButtonPressed.Pre` (подстановка поиска из JEI по Ctrl+Q / клику и
 * «крафт из JEI» средней кнопкой), оба с `setCanceled(true)`. Классов в шиме не было, а
 * шина ловит только `ClassCastException` (`SimpleEventBus.dispatchFuzzy`), тогда как JVM на
 * `checkcast` в лямбде кидает `NoClassDefFoundError` — и это роняло клиент без понятной
 * причины.
 *
 * СИГНАТУРЫ СВЕРЕНЫ С NeoForge 26.3.x
 * (`src/client/java/net/neoforged/neoforge/client/event/ScreenEvent.java`):
 *  - `MouseButtonPressed(Screen, MouseButtonEvent, boolean doubleClick)` + `getMouseButtonEvent()`,
 *    `getButton()`, `isDoubleClick()`, `getMouseX()/getMouseY()` (double) из `MouseInput`;
 *  - `KeyPressed(Screen, KeyEvent)` + `getKeyEvent()`, `getKey()`, `getKeycode()`, `getModifiers()`
 *    из `KeyInput`;
 *  - `Pre` — отменяемые (`ICancellableEvent`), `Post` — нет (у мыши `Post` умеет `setResult`;
 *    у клавиши `Post` в 26.3 отменяемый);
 *  - `Opening(Screen currentScreen, Screen newScreen)` + `getCurrentScreen()`, `getNewScreen()`,
 *    `setNewScreen(@Nullable)`.
 *
 * РАСХОЖДЕНИЯ С NeoForge (осознанные):
 *  1) `getKeyCode()`/`getScanCode()` — В NeoForge 26.3 их уже нет (`getKeycode()`/`getKey()`
 *     вместо них). Но `extendedae_plus` собран с этими вызовами (`CtrlQPatternKeyHandler`
 *     передаёт `getKeyCode()` в `InputConstants.Type.KEYSYM.getOrCreate`), поэтому держим их
 *     как deprecated-делегаты: `getKeyCode()` → `keyEvent.keycode()` (виртуальный код),
 *     `getScanCode()` → `keyEvent.key()` (физический код, SDL scancode).
 *  2) `Opening` пока не отменяемый: наш постинг (`MinecraftScreenOpeningMixin`) умеет только
 *     подменять экран. GuideME отменой не пользуется — поведение не меняем.
 *  3) `Render`, `Init`, `MouseScrolled`, `KeyReleased` и т.д. не заведены: их никто из живых
 *     аддонов не слушает, а раздувать шим «на всякий» — лишние поломки при обновлении MC.
 *
 * ГДЕ ПОСТИМ. `KeyPressed.Pre/Post` — из `KeyboardHandler#keyPress` вокруг
 * `Screen#keyPressed`; `MouseButtonPressed.Pre/Post` — из `MouseHandler#onButton` вокруг
 * `Screen#mouseClicked`. Точки — ровно те, что патчит сам NeoForge, поэтому порядок
 * «до обработки экраном» совпадает. См. `nfcompat/mixin/client/KeyboardHandlerScreenEventMixin`
 * и `nfcompat/mixin/client/MouseHandlerScreenEventMixin`.
 */
package net.neoforged.neoforge.client.event;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

import org.jetbrains.annotations.Nullable;

public abstract class ScreenEvent extends Event {
    @Nullable
    private final Screen screen;

    protected ScreenEvent(@Nullable Screen screen) {
        this.screen = screen;
    }

    @Nullable
    public Screen getScreen() {
        return screen;
    }

    /**
     * Событие открытия экрана: позволяет подменить открываемый экран.
     *
     * Сюда же (`currentScreen`) попадает экран, который был открыт до этого вызова.
     * Постится из `MinecraftScreenOpeningMixin` (`Minecraft#setScreenAndShow`).
     */
    public static class Opening extends ScreenEvent {
        @Nullable
        private final Screen currentScreen;
        @Nullable
        private Screen newScreen;

        /** Совместимость с нашим миксином: «текущим» считаем тот же экран. */
        public Opening(Screen newScreen) {
            this(newScreen, newScreen);
        }

        public Opening(@Nullable Screen currentScreen, @Nullable Screen newScreen) {
            super(newScreen);
            this.currentScreen = currentScreen;
            this.newScreen = newScreen;
        }

        @Nullable
        public Screen getCurrentScreen() {
            return currentScreen;
        }

        @Nullable
        public Screen getNewScreen() {
            return newScreen;
        }

        public void setNewScreen(@Nullable Screen newScreen) {
            this.newScreen = newScreen;
        }
    }

    /** Общая база мышиных под-событий: координаты курсора в координатах экрана. */
    private static abstract class MouseInput extends ScreenEvent {
        private final double mouseX;
        private final double mouseY;

        protected MouseInput(@Nullable Screen screen, double mouseX, double mouseY) {
            super(screen);
            this.mouseX = mouseX;
            this.mouseY = mouseY;
        }

        public double getMouseX() {
            return mouseX;
        }

        public double getMouseY() {
            return mouseY;
        }
    }

    /**
     * Нажатие кнопки мыши на экране.
     *
     * `Pre` — до обработки экраном, отменяемое (отмена = экран клик не увидит).
     * `Post` — после, только если `Pre` не отменили: `setResult` позволяет заставить
     * клик считаться обработанным/необработанным.
     */
    public static abstract class MouseButtonPressed extends MouseInput {
        private final MouseButtonEvent mouseEvent;
        private final boolean doubleClick;

        public MouseButtonPressed(@Nullable Screen screen, MouseButtonEvent mouseEvent, boolean doubleClick) {
            super(screen, mouseEvent.x(), mouseEvent.y());
            this.mouseEvent = mouseEvent;
            this.doubleClick = doubleClick;
        }

        public MouseButtonEvent getMouseButtonEvent() {
            return mouseEvent;
        }

        /** {@return код кнопки (GLFW `GLFW_MOUSE_BUTTON_*`)} */
        public int getButton() {
            return mouseEvent.button();
        }

        public boolean isDoubleClick() {
            return doubleClick;
        }

        public static class Pre extends MouseButtonPressed implements ICancellableEvent {
            public Pre(@Nullable Screen screen, MouseButtonEvent mouseEvent, boolean doubleClick) {
                super(screen, mouseEvent, doubleClick);
            }
        }

        public static class Post extends MouseButtonPressed {
            private final boolean handled;
            private Result result = Result.DEFAULT;

            public Post(@Nullable Screen screen, MouseButtonEvent mouseEvent, boolean doubleClick, boolean handled) {
                super(screen, mouseEvent, doubleClick);
                this.handled = handled;
            }

            /** {@return был ли клик обработан экраном до этого события} */
            public boolean wasClickHandled() {
                return handled;
            }

            public void setResult(Result result) {
                this.result = result;
            }

            public Result getResult() {
                return result;
            }

            /** {@return итог клика с учётом `setResult`, как в NeoForge} */
            public boolean getClickResult() {
                if (result == Result.FORCE_HANDLED) {
                    return true;
                }
                return result == Result.DEFAULT && handled;
            }

            public enum Result {
                FORCE_HANDLED,
                DEFAULT,
                FORCE_UNHANDLED
            }
        }
    }

    /** Общая база клавиатурных под-событий. */
    private static abstract class KeyInput extends ScreenEvent {
        private final KeyEvent keyEvent;

        protected KeyInput(@Nullable Screen screen, KeyEvent keyEvent) {
            super(screen);
            this.keyEvent = keyEvent;
        }

        public KeyEvent getKeyEvent() {
            return keyEvent;
        }

        /** {@return физический код клавиши (SDL scancode)} */
        public int getKey() {
            return keyEvent.key();
        }

        /** {@return виртуальный код клавиши (SDL keycode)} */
        public int getKeycode() {
            return keyEvent.keycode();
        }

        /** {@return битовая маска модификаторов (Ctrl/Shift/Alt/...)} */
        public int getModifiers() {
            return keyEvent.modifiers();
        }

        /**
         * Старое (до 26.3) имя виртуального кода клавиши.
         *
         * @deprecated в NeoForge 26.3 метода нет, оставлен для уже собранных аддонов
         *             (`extendedae_plus` зовёт его при Ctrl+Q). Используйте {@link #getKeycode()}.
         */
        @Deprecated
        public int getKeyCode() {
            return keyEvent.keycode();
        }

        /**
         * Старое (до 26.3) имя физического кода клавиши.
         *
         * @deprecated в NeoForge 26.3 метода нет, оставлен для уже собранных аддонов.
         *             Используйте {@link #getKey()}.
         */
        @Deprecated
        public int getScanCode() {
            return keyEvent.key();
        }
    }

    /**
     * Нажатие клавиши на экране.
     *
     * `Pre` — до обработки экраном, отменяемое (отмена = экран клавишу не увидит).
     * `Post` — после, только если экран клавишу не обработал; в NeoForge отменяемое
     * (отмена = «клавиша обработана»), поэтому реализуем `ICancellableEvent`.
     */
    public static abstract class KeyPressed extends KeyInput {
        public KeyPressed(@Nullable Screen screen, KeyEvent keyEvent) {
            super(screen, keyEvent);
        }

        public static class Pre extends KeyPressed implements ICancellableEvent {
            public Pre(@Nullable Screen screen, KeyEvent keyEvent) {
                super(screen, keyEvent);
            }
        }

        public static class Post extends KeyPressed implements ICancellableEvent {
            public Post(@Nullable Screen screen, KeyEvent keyEvent) {
                super(screen, keyEvent);
            }
        }
    }
}

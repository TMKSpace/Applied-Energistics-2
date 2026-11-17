/*
 * Часть nfcompat. Заглушка неофорджевского `ScreenEvent` (клиентские события экранов).
 *
 * ЗАЧЕМ. GuideME подписывается на `ScreenEvent.Opening` в `GuideOnStartup.init()`
 * (показ гайда при входе). Класса в шиме не было, а шина ловит только `ClassCastException`
 * (`SimpleEventBus.dispatchFuzzy`), тогда как JVM на `checkcast` в лямбде кидает
 * `NoClassDefFoundError` — и это роняло клиент без понятной причины.
 *
 * Само событие мы не постим (`ScreenEvent.Opening` — перехват открытия экрана, у нас его
 * эмулировать нечем и незачем), но класс обязан существовать и быть `Event`-ом, чтобы
 * регистрация слушателя и резолв типа события работали.
 */
package net.neoforged.neoforge.client.event;

import net.minecraft.client.gui.screens.Screen;
import net.neoforged.bus.api.Event;

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

    /** Событие открытия экрана: позволяет подменить открываемый экран. */
    public static class Opening extends ScreenEvent {
        private Screen newScreen;

        public Opening(Screen newScreen) {
            super(newScreen);
            this.newScreen = newScreen;
        }

        public Screen getNewScreen() {
            return newScreen;
        }

        public void setNewScreen(Screen newScreen) {
            this.newScreen = newScreen;
        }
    }
}

/*
 * Часть nfcompat. Базовый класс событий в стиле NeoForge EventBus.
 *
 * Отмена: сам Event НЕ реализует ICancellableEvent (в NeoForge тоже), но даёт
 * методы isCanceled/setCanceled. Классы событий добавляют интерфейс сами,
 * и это позволяет им писать super.setCanceled(...).
 */
package net.neoforged.bus.api;

public abstract class Event {

    private boolean canceled;

    protected Event() {
    }

    public boolean isCanceled() {
        return canceled;
    }

    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }

    /** {@return true если у события есть результат, который можно менять} */
    public boolean hasResult() {
        return false;
    }
}

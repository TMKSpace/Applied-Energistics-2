/*
 * Часть nfcompat. Маркер события мод-шины.
 *
 * В NeoForge у мода свой IEventBus, а события-регистрации помечаются этим интерфейсом.
 * У нас шина одна, поэтому интерфейс нужен только чтобы исходники компилировались.
 */
package net.neoforged.fml.event;

public interface IModBusEvent {
}

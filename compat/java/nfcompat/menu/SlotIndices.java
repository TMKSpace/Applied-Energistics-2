/*
 * Часть nfcompat. Индекс слота.
 *
 * NeoForge патчит `Slot`, добавляя `getSlotIndex()` — номер слота в контейнере.
 * В ванили он лежит в приватном поле `slot`, поэтому читаем его рефлексией
 * (поле ищется один раз). AE2 зовёт это в горячих путях меню, так что кэш обязателен.
 *
 * TODO: заменить на миксин с `@Shadow`, когда появится миксин-полоса; рефлексия
 * в горячем пути — не то, что хочется в финальной сборке.
 */
package nfcompat.menu;

import java.lang.reflect.Field;

import net.minecraft.world.inventory.Slot;

public final class SlotIndices {
    private static Field slotField;

    private SlotIndices() {
    }

    public static int get(Slot slot) {
        try {
            Field f = slotField;
            if (f == null) {
                f = Slot.class.getDeclaredField("slot");
                f.setAccessible(true);
                slotField = f;
            }
            return f.getInt(slot);
        } catch (ReflectiveOperationException e) {
            // Запасной путь: публичный index (номер слота в меню), чтобы не падать.
            return slot.index;
        }
    }
}

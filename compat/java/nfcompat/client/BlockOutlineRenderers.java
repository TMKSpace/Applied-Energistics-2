/*
 * Часть nfcompat. Кастомные рендереры обводки блока (per-part outline).
 *
 * ЗАЧЕМ. NeoForge дописывает в запись `BlockOutlineRenderState` ещё один компонент —
 * `List<CustomBlockOutlineRenderer>`, — и передаёт тот же список в
 * `ExtractBlockOutlineRenderStateEvent`. Дальше в `LevelRenderer#submitBlockOutline`
 * эти рендереры вызываются до ванильной отрисовки, а `true` из любого из них
 * подавляет ванильную обводку. AE2 наполняет список через `addCustomRenderer`
 * (обводка отдельной части кабеля, превью установки), но сам список никогда не читает,
 * поэтому нам достаточно хранить его на стороне шима.
 *
 * ПОЧЕМУ НЕ В ЗАПИСИ. `BlockOutlineRenderState` — ванильный record, лишний компонент
 * в него миксином не добавить (нужны правки конструктора и всех вызовов), а ради
 * одного поля это дороже, чем одна статическая пара «state -> рендереры».
 *
 * ПОЧЕМУ ОДИН СЛОТ. Извлечение состояния и отправка геометрии идут в одном кадре
 * на одном потоке, state между ними не пересоздаётся. Сравнение — по идентичности,
 * чтобы не поймать чужой (равный по полям) state из другого кадра. Ссылка на state
 * живёт до следующего кадра, но state не тянет за собой уровень, так что течь нечему.
 */
package nfcompat.client;

import java.util.List;

import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.neoforged.neoforge.client.CustomBlockOutlineRenderer;

public final class BlockOutlineRenderers {
    private static BlockOutlineRenderState state;
    private static List<CustomBlockOutlineRenderer> renderers = List.of();

    private BlockOutlineRenderers() {
    }

    public static void set(BlockOutlineRenderState newState, List<CustomBlockOutlineRenderer> newRenderers) {
        state = newState;
        renderers = newRenderers;
    }

    public static void clear() {
        state = null;
        renderers = List.of();
    }

    /** {@return рендереры, добавленные для этого state (пустой список, если чужие)} */
    public static List<CustomBlockOutlineRenderer> get(BlockOutlineRenderState currentState) {
        return currentState != null && currentState == state ? renderers : List.of();
    }
}

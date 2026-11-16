/*
 * Часть nfcompat. Наполнение ванильных креативных вкладок (`BuildCreativeModeTabContentsEvent`).
 *
 * AE2 не создаёт свои вкладки под инструменты: он докладывает предметы в уже
 * существующие (Tools/Combat/Ingredients и т.п.) через
 * `MainCreativeTab#addExternal`, а NeoForge рассылает событие из пропатченного
 * `CreativeModeTab#buildContents` — на каждую собираемую вкладку, после того как её
 * собственный генератор отработал, но до того как содержимое закешировано в
 * `displayItems`/`displayItemsSearchTab`.
 *
 * У ванили такого хука нет, поэтому событие постит миксин
 * `nfcompat.mixin.CreativeModeTabMixin` в конце `buildContents`, а здесь — сборка
 * "правильных" коллекций (InsertableLinkedOpenCustomHashSet: AE2 вставляет предметы
 * через `accept`, а событию нужен именно этот тип) и запись их обратно.
 *
 * Ванильные коллекции — `ItemStackLinkedSet` (Set по типу+компонентам), так что
 * перекладывание в такие же set'ы семантику не меняет.
 */
package nfcompat;

import java.util.Collection;
import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.InsertableLinkedOpenCustomHashSet;
import net.neoforged.neoforge.common.util.strategy.BasicStrategy;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.jetbrains.annotations.Nullable;

public final class CreativeTabContents {

    private CreativeTabContents() {
    }

    /**
     * Постит событие для одной вкладки. Вызывается из миксина на `CreativeModeTab#buildContents`
     * (TAIL), т.е. уже после того, как ванильный генератор и все зарегистрированные вкладки
     * отработали.
     *
     * @return новые коллекции содержимого вкладки, либо null, если вкладка не в реестре
     *         (тогда трогать её не надо)
     */
    @Nullable
    public static Result build(CreativeModeTab tab,
            CreativeModeTab.ItemDisplayParameters parameters,
            Collection<ItemStack> parentEntries,
            Set<ItemStack> searchEntries) {
        var key = BuiltInRegistries.CREATIVE_MODE_TAB.getResourceKey(tab).orElse(null);
        if (key == null) {
            return null;
        }

        var parent = new InsertableLinkedOpenCustomHashSet<ItemStack>(BasicStrategy.BASIC);
        parent.addAll(parentEntries);

        var search = new InsertableLinkedOpenCustomHashSet<ItemStack>(BasicStrategy.BASIC);
        search.addAll(searchEntries);

        NeoForge.EVENT_BUS.post(new BuildCreativeModeTabContentsEvent(tab, key, parameters, parent, search));

        // Ничего не изменилось (обычный случай: AE2 докладывает предметы в 3-4 вкладки из ~15) —
        // оставляем ванильные коллекции как есть, чтобы не менять их семантику без нужды.
        if (parent.equals(parentEntries) && search.equals(searchEntries)) {
            return null;
        }

        return new Result(parent, search);
    }

    public record Result(InsertableLinkedOpenCustomHashSet<ItemStack> parentEntries,
            InsertableLinkedOpenCustomHashSet<ItemStack> searchEntries) {
    }
}

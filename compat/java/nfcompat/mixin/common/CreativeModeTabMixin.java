/*
 * Часть nfcompat. Мост `BuildCreativeModeTabContentsEvent`.
 *
 * NeoForge постит это событие из пропатченного `CreativeModeTab#buildContents` — после
 * того как генератор вкладки собрал содержимое, но до того как оно закешировано.
 * Повторяем это на TAIL: к этому моменту `displayItems`/`displayItemsSearchTab` уже
 * выставлены из билдера, и их можно заменить версиями, в которые AE2 доложил свои
 * предметы (`MainCreativeTab#initExternal`).
 *
 * Побочный эффект: событие летит и на сервере (там креативные вкладки собираются при
 * входе игрока) — как и на NeoForge, где патч стоит в общем коде.
 */
package nfcompat.mixin.common;

import java.util.Collection;
import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import nfcompat.CreativeTabContents;

@Mixin(CreativeModeTab.class)
public class CreativeModeTabMixin {

    @Shadow
    private Collection<ItemStack> displayItems;

    @Shadow
    private Set<ItemStack> displayItemsSearchTab;

    @Inject(method = "buildContents", at = @At("TAIL"))
    private void nfcompat$buildContents(CreativeModeTab.ItemDisplayParameters parameters, CallbackInfo ci) {
        if (displayItems == null || displayItemsSearchTab == null) {
            return;
        }

        var result = CreativeTabContents.build((CreativeModeTab) (Object) this, parameters,
                displayItems, displayItemsSearchTab);
        if (result != null) {
            displayItems = result.parentEntries();
            displayItemsSearchTab = result.searchEntries();
        }
    }
}

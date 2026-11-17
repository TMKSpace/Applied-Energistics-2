/*
 * Часть nfcompat (GuideME). Горячая клавиша гайда на ванильном `KeyMapping`.
 *
 * `OpenGuideHotkey.<clinit>` создаёт клавишу неофорджевским конструктором с
 * `IKeyConflictContext`, которого в ванили нет. См. `GuideMEHotkeyCompat`: редирект `NEW`
 * (Mixin умеет подменять создание объекта вместе с вызовом конструктора) отдаёт ванильный
 * `KeyMapping`, из-за чего `<clinit>` больше не падает.
 *
 * `require = 0`: если GuideME переедет на другой конструктор, миксин промолчит и мы останемся
 * с клавишей из `GuideMEHotkeyCompat.mapping()` (её регистрирует `GuideMEClientMixin`).
 */
package nfcompat.mixin.guideme;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import nfcompat.compat.guideme.GuideMEHotkeyCompat;

@Mixin(targets = "guideme.internal.hotkey.OpenGuideHotkey")
public class GuideMEHotkeyMixin {

    @Redirect(method = "<clinit>", require = 0,
            at = @At(value = "NEW", target = "net/minecraft/client/KeyMapping"))
    private static KeyMapping nfcompat$newKeyMapping(String description, IKeyConflictContext context,
            InputConstants.Type type, int keyCode, KeyMapping.Category category) {
        return GuideMEHotkeyCompat.create(description, context, type, keyCode, category);
    }
}

/*
 * Часть nfcompat. Постинг неофорджевского `ScreenEvent.Opening`.
 *
 * ЗАЧЕМ. GuideME подписывается на это событие в `GuideOnStartup` (фича «показать гайд при
 * входе в мир» / `showOnStartup`), а также может подменять открываемый экран. Класс событий у
 * нас был только заглушкой (`net.neoforged.neoforge.client.event.ScreenEvent`), и событие
 * никто не постил — фича молча не работала.
 *
 * ГДЕ. Ванильная точка — `Minecraft#setScreen(Screen)`: любой экран (в том числе сразу после
 * входа в мир) проходит через неё. `@ModifyVariable` подменяет аргумент на `getNewScreen()`,
 * если слушатель его изменил, — это ровно неофорджевская семантика `Opening`.
 *
 * ВАЖНО: событие постится на каждый вызов `setScreen`, включая `null` (закрытие экрана).
 * GuideME сам проверяет, что ему интересно; на `null` реагировать не должен, поэтому
 * `ScreenEvent` допускает `null`-экран (см. его шапку).
 */
package nfcompat.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Minecraft.class)
public class MinecraftScreenOpeningMixin {

    @ModifyVariable(method = "setScreenAndShow", at = @At("HEAD"), argsOnly = true)
    private Screen nfcompat$postScreenOpening(@Nullable Screen screen) {
        if (screen == null) {
            return null;
        }

        var event = new ScreenEvent.Opening(screen);
        NeoForge.EVENT_BUS.post(event);
        var replacement = event.getNewScreen();
        return replacement != null ? replacement : screen;
    }
}

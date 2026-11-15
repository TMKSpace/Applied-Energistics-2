/*
 * Часть nfcompat. Хук регистрации клавиш NeoForge.
 *
 * NeoForge патчит конструктор `Options` и постит `RegisterKeyMappingsEvent`
 * перед `Options#load` - то есть после того, как поле keyMappings заполнено
 * ванильными клавишами, но до того, как из options.txt прочитаны сохранённые
 * значения. Повторяем ровно этот порядок: иначе пользовательские привязки
 * AE2 сбрасывались бы при каждом запуске.
 */
package nfcompat.mixin.client;

import java.io.File;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

@Mixin(Options.class)
public class OptionsMixin {

    @Inject(method = "<init>",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;load()V", shift = At.Shift.BEFORE))
    private void nfcompat$registerKeyMappings(Minecraft minecraft, File workingDirectory, CallbackInfo ci) {
        nfcompat.ClientEventBridge.postKeyMappings((Options) (Object) this);
    }
}

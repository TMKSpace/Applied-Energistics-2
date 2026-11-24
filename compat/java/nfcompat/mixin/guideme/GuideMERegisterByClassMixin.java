/*
 * Часть nfcompat. Мост для GuideME: `ArgumentTypeInfos.registerByClass` -> наш компат.
 *
 * GuideME — сборка под NeoForge, её `GuideME.<init>` в двух лямбдах регистрирует
 * аргументы команд через неофорджевский `ArgumentTypeInfos.registerByClass`. Такого
 * метода в ванили нет, а добавить его миксином нельзя (non-private static).
 * Поэтому перенаправляем оба вызова на `nfcompat.compat.GuideMECompat`, который делает
 * то же самое (кладёт в приватную карту и возвращает info).
 */
package nfcompat.mixin.guideme;

import net.minecraft.commands.synchronization.ArgumentTypeInfo;

import nfcompat.compat.guideme.GuideMECompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "guideme.internal.GuideME")
@SuppressWarnings({"rawtypes", "unchecked"})
public class GuideMERegisterByClassMixin {

    @Redirect(method = "lambda$new$0", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/commands/synchronization/ArgumentTypeInfos;registerByClass(Ljava/lang/Class;Lnet/minecraft/commands/synchronization/ArgumentTypeInfo;)Lnet/minecraft/commands/synchronization/ArgumentTypeInfo;"))
    private static ArgumentTypeInfo<?, ?> nfcompat$registerByClass0(Class<?> clazz, ArgumentTypeInfo<?, ?> info) {
        return GuideMECompat.registerByClassRaw(clazz, info);
    }

    @Redirect(method = "lambda$new$1", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/commands/synchronization/ArgumentTypeInfos;registerByClass(Ljava/lang/Class;Lnet/minecraft/commands/synchronization/ArgumentTypeInfo;)Lnet/minecraft/commands/synchronization/ArgumentTypeInfo;"))
    private static ArgumentTypeInfo<?, ?> nfcompat$registerByClass1(Class<?> clazz, ArgumentTypeInfo<?, ?> info) {
        return GuideMECompat.registerByClassRaw(clazz, info);
    }
}
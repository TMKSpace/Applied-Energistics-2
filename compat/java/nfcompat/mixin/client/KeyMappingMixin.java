/*
 * Часть nfcompat. Неофорджевский геттер `KeyMapping#getKey()`.
 *
 * NeoForge добавляет к ванильному `KeyMapping` пару методов поверх поля `key`
 * (`getKey()`, `isDown()` и т.п.). GuideME (сборка под NeoForge) зовёт `getKey()` из
 * `OpenGuideHotkey.isKeyHeld` — то есть при каждом тике, когда игрок держит клавишу гайда:
 *
 *     NoSuchMethodError: KeyMapping.getKey()Lcom/mojang/blaze3d/platform/InputConstants$Key;
 *
 * В ванили поле есть (`protected InputConstants.Key key`), но геттера нет — добавляем.
 */
package nfcompat.mixin.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin {

    @Shadow
    protected InputConstants.Key key;

    public InputConstants.Key getKey() {
        return key;
    }
}

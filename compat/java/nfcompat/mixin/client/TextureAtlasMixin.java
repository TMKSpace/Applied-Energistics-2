/*
 * Часть nfcompat. Постинг `TextureAtlasStitchedEvent` (атлас склеен, спрайты готовы).
 *
 * ЗАЧЕМ. GuideME подписывается на это событие и в `GuideMEClient.resetSprites` очищает кэш
 * спрайтов (`GuiAssets.resetSprites`). Если событие не постить, кэш остаётся в состоянии
 * «missing», и гайд рисуется чёрно-фиолетовыми квадратами (missingno), хотя страницы
 * открываются и текст виден.
 *
 * ПОЧЕМУ МИКСИН. В ванили 26.3 точка «атлас склеен» — это `TextureAtlas#upload(Preparations)`,
 * после него у атласа есть все спрайты. У Fabric нет события на этот момент, поэтому
 * инжектимся в хвост метода и постим неофорджевское событие на MOD-шину: GuideME получает
 * её в конструкторе `GuideMEClient` (см. `nfcompat.compat.guideme.GuideMEBootstrap`).
 */
package nfcompat.mixin.client;

import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.neoforged.neoforge.client.event.TextureAtlasStitchedEvent;
import net.neoforged.neoforge.common.NeoForge;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureAtlas.class)
public abstract class TextureAtlasMixin {

    @Inject(method = "upload", at = @At("TAIL"))
    private void nfcompat$postAtlasStitched(SpriteLoader.Preparations preparations, CallbackInfo ci) {
        NeoForge.EVENT_BUS.post(new TextureAtlasStitchedEvent((TextureAtlas) (Object) this));
    }
}

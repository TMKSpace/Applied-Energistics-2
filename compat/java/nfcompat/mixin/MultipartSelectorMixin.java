/*
 * Часть nfcompat. Вторая точка, где ваниль читает `BlockStateModel.Unbaked.CODEC` - multipart.
 *
 * `multipart.Selector.CODEC` собирается в приватной лямбде <clinit>:
 *   RecordCodecBuilder... варианта через BlockStateModel.Unbaked.CODEC.fieldOf("apply")
 * поэтому {"apply": {"type": "ae2:...", ...}} тоже не парсится без NeoForge-диспетчеризации
 * по "type". Подменяем чтение поля на `BlockStateModelHooks.codec()` - подробности и причина,
 * почему нельзя просто сделать поле mutable, в nfcompat.mixin.SimpleModelSelectorsMixin.
 *
 * Целимся в synthetic-лямбду по её точному имени и дескриптору (MC 26.3 не обфусцирован,
 * javap по minecraft-merged-deobf-26.3.jar: `lambda$static$0`). Если при обновлении MC
 * лямбда переименуется, миксин упадёт на старте (required=true, defaultRequire=1) -
 * это лучше, чем молча нерабочие модели.
 */
package nfcompat.mixin;

import com.mojang.serialization.Codec;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.multipart.Selector;
import net.neoforged.neoforge.client.model.block.BlockStateModelHooks;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Selector.class)
public abstract class MultipartSelectorMixin {

    @Redirect(method = "lambda$static$0(Lcom/mojang/serialization/codecs/RecordCodecBuilder$Instance;)Lcom/mojang/datafixers/kinds/App;",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel$Unbaked;CODEC:Lcom/mojang/serialization/Codec;",
                    opcode = Opcodes.GETSTATIC))
    private static Codec<BlockStateModel.Unbaked> nfcompat$customBlockStateModelCodec() {
        return BlockStateModelHooks.codec();
    }
}

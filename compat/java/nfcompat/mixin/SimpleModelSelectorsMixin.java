/*
 * Часть nfcompat. Подмена ванильного кодека blockstate-варианта на NeoForge-подобный.
 *
 * Ванильный `BlockStateModelDispatcher.SimpleModelSelectors.CODEC` собирается в <clinit> как
 *   ExtraCodecs.nonEmptyMap(Codec.unboundedMap(Codec.STRING, BlockStateModel.Unbaked.CODEC)).xmap(...)
 * из-за чего значение варианта обязано быть либо списком весов, либо SingleVariant
 * ({"model": "..."}). Одиночный объект с "type" ваниль не понимает - отсюда
 * "Failed to parse either. First: Not a json array... Second: No key model in MapLike".
 *
 * NeoForge правит сам `BlockStateModel.Unbaked.CODEC` (добавляет диспетчеризацию по "type"),
 * но это поле лежит в интерфейсе: снять с него final нельзя - JVM при загрузке класса
 * падает с ClassFormatError: Illegal field modifiers in class (проверено на JDK 25).
 * Поэтому подменяем не поле, а оба места, где ваниль его читает:
 *   - здесь ("variants" / simple models);
 *   - nfcompat.mixin.MultipartSelectorMixin ("multipart").
 * Если в новой версии MC появится третье чтение - его тоже придётся перенаправить.
 */
package nfcompat.mixin;

import com.mojang.serialization.Codec;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelDispatcher;
import net.neoforged.neoforge.client.model.block.BlockStateModelHooks;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BlockStateModelDispatcher.SimpleModelSelectors.class)
public abstract class SimpleModelSelectorsMixin {

    @Redirect(method = "<clinit>", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel$Unbaked;CODEC:Lcom/mojang/serialization/Codec;",
            opcode = Opcodes.GETSTATIC))
    private static Codec<BlockStateModel.Unbaked> nfcompat$customBlockStateModelCodec() {
        return BlockStateModelHooks.codec();
    }
}

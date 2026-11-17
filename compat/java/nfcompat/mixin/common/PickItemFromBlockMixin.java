/*
 * Часть nfcompat. Pick-block (СКМ) по кабель-шине AE2.
 *
 * В ванили 26.3 pick-block считается на сервере:
 *   ServerGamePacketListenerImpl#handlePickItemFromBlock(ServerboundPickItemFromBlockPacket)
 *   -> BlockState#getCloneItemStack(LevelReader, BlockPos, boolean)
 * Пять аргументов (с `Player`) - это NeoForge-патч; ванильная сигнатура игрока не знает.
 * Поэтому `CableBusBlock#getCloneItemStack(LevelReader, BlockPos, BlockState, boolean, Player)`
 * на Fabric никем не вызывается (у него снят @Override), и в руку попадает предмет блока
 * `ae2:cable_bus`, у которого item-модель `minecraft:empty` - то есть «невидимый предмет».
 *
 * Подменяем единственный вызов внутри `handlePickItemFromBlock` на диспетчер:
 * для CableBusBlock зовём AE2-метод (он сам делает raycast, selectPartLocal и возвращает
 * part.getPartItem() / facade.getItemStack()), для всех остальных блоков - ваниль как была.
 *
 * Пустой результат AE2 (клик по пустому месту шины / луч не попал в шейп) НЕ подменяем
 * ванильным стеком: ваниль для cable_bus отдаёт тот самый невидимый предмет блока,
 * а на NeoForge в этом случае не выдаётся ничего (в handlePickItemFromBlock есть
 * ранний `return` на пустом стеке).
 *
 * Точка инжекта (javap по minecraft-merged-deobf-26.3.jar):
 *   ServerGamePacketListenerImpl.handlePickItemFromBlock  - объявление на строке 1589,
 *   вызов getCloneItemStack - байткод offset 78 (строка 1633 дампа).
 * Поле `player` там `public` (не final), поэтому просто @Shadow.
 */
package nfcompat.mixin.common;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

import appeng.block.networking.CableBusBlock;

@Mixin(ServerGamePacketListenerImpl.class)
public class PickItemFromBlockMixin {

    @Shadow
    public ServerPlayer player;

    @Redirect(method = "handlePickItemFromBlock(Lnet/minecraft/network/protocol/game/ServerboundPickItemFromBlockPacket;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;getCloneItemStack(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Z)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack nfcompat$pickCableBusPart(BlockState state, LevelReader level, BlockPos pos,
            boolean includeData) {
        if (state.getBlock() instanceof CableBusBlock cableBus) {
            return cableBus.getCloneItemStack(level, pos, state, includeData, this.player);
        }

        return state.getCloneItemStack(level, pos, includeData);
    }
}

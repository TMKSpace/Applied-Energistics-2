/*
 * Часть nfcompat. Вызов `BlockEntity#onChunkUnloaded()` при выгрузке чанка.
 *
 * ГДЕ. NeoForge патчит `LevelChunk#clearAllBlockEntities()` одной строкой перед
 * `setRemoved()`:
 *
 *     public void clearAllBlockEntities() {
 *         this.blockEntities.values().forEach(BlockEntity::onChunkUnloaded);
 *         this.blockEntities.values().forEach(BlockEntity::setRemoved);
 *         ...
 *     }
 *
 * То же место берём и мы (инъекция в HEAD метода). Это и есть «после сохранения»:
 * единственный путь выгрузки в ванили 26.3 — `ChunkMap#scheduleUnload`,
 * который сначала ждёт `ChunkHolder#getSaveSyncFuture`, потом на главном потоке
 * делает `LevelChunk#setLoaded(false)` -> `ChunkMap#save(chunk)` -> `ServerLevel#unload(chunk)`,
 * а `ServerLevel#unload` первым делом зовёт `clearAllBlockEntities()`. То есть к моменту
 * инъекции чанк (и NBT всех BE) уже записан; `setRemoved()` в BE ещё не срабатывал.
 * Единственные два вызывающих `clearAllBlockEntities` в jar'е — `ServerLevel#unload`
 * (после save) и `ClientLevel#unload` (там сохранять нечего, а на клиенте AE2 узлы
 * не создаёт — `ManagedGridNode#create` выходит при `isClientSide`), так что хук
 * безопасен на обеих сторонах, как и на NeoForge.
 *
 * ПОЧЕМУ НЕ `DeferredBlockEntityUnloader`. В NeoForge 1.20.x хук откладывали в очередь,
 * потому что выгрузка чанка и `setRemoved()` случались ДО сохранения. В 1.21+/26.x
 * ваниль уже сама сохраняет чанк перед выгрузкой, и NeoForge выкинул очередь: патч
 * `LevelChunk` теперь просто зовёт `onChunkUnloaded` синхронно. Поэтому никакой
 * собственной очереди не заводим — она бы только добавила окно, в котором BE уже
 * выгружен из чанка, но ещё не отцепил ноду.
 *
 * ЧТО ЭТО ДАЁТ AE2. `AENetworkedBlockEntity#onChunkUnloaded` (и `...InvBlockEntity`,
 * `...PoweredBlockEntity`), `QuantumBridgeBlockEntity`, `SpatialPylonBlockEntity`,
 * `CableBusBlockEntity` вызывают `getMainNode().destroy()`/`disconnect(false)`: нода
 * отцепляется от сети при выгрузке, а не живёт до следующего `setRemoved()`.
 */
package nfcompat.mixin.common;

import java.util.ArrayList;

import net.minecraft.world.level.chunk.LevelChunk;

import nfcompat.blockentity.ChunkUnloadListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelChunk.class)
public abstract class LevelChunkUnloadMixin {

    // Имя с префиксом: у самого LevelChunk уже есть приватное статическое поле LOGGER,
    // а поля миксина не переименовываются.
    private static final Logger NFCOMPAT_LOGGER = LoggerFactory.getLogger("nfcompat/chunkunload");

    @Inject(method = "clearAllBlockEntities", at = @At("HEAD"))
    private void nfcompat$fireOnChunkUnloaded(CallbackInfo ci) {
        var chunk = (LevelChunk) (Object) this;

        // Копия, а не живая map: хук в теории может тронуть blockEntities (например,
        // через соседний блок). NeoForge итерирует живую map и в этом случае упал бы
        // с ConcurrentModificationException; нам лишняя копия на выгрузку чанка дешевле.
        for (var blockEntity : new ArrayList<>(chunk.getBlockEntities().values())) {
            if (!(blockEntity instanceof ChunkUnloadListener listener)) {
                continue;
            }

            try {
                listener.onChunkUnloaded();
            } catch (Throwable t) {
                // NeoForge не ловит исключения здесь (они роняют выгрузку чанка).
                // Нам важнее не развалить весь процесс выгрузки из-за одной BE:
                // логируем с координатами и идём дальше.
                NFCOMPAT_LOGGER.error("Block entity {} at {} threw during onChunkUnloaded",
                        blockEntity.getClass().getName(), blockEntity.getBlockPos(), t);
            }
        }
    }
}

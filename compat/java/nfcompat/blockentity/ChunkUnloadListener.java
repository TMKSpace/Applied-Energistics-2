/*
 * Часть nfcompat. Контракт для `BlockEntity#onChunkUnloaded()`.
 *
 * Зачем отдельный интерфейс. В NeoForge `BlockEntity` пропатчен и сам объявляет
 * `onChunkUnloaded()` (пустой дефолт), поэтому патч `LevelChunk#clearAllBlockEntities`
 * может просто позвать `BlockEntity::onChunkUnloaded`. У нас `BlockEntity` ванильный,
 * и метод добавляется миксином (`nfcompat.mixin.common.BlockEntityMixin`). Ссылаться
 * из одного миксина на метод, который добавил другой миксин, компилятор не даст,
 * поэтому общий контракт вынесен в интерфейс: `BlockEntityMixin` его реализует,
 * а `nfcompat.mixin.common.LevelChunkUnloadMixin` дергает через `instanceof`.
 *
 * Диспетчеризация остаётся обычной виртуальной: `AEBaseBlockEntity#onChunkUnloaded()`
 * (и переопределения в `AENetworkedBlockEntity`, `QuantumBridgeBlockEntity`,
 * `SpatialPylonBlockEntity`, `CableBusBlockEntity`) объявляют метод сами, и вызов
 * через интерфейс попадает именно в них — как на NeoForge.
 */
package nfcompat.blockentity;

public interface ChunkUnloadListener {

    /**
     * NeoForge `BlockEntity#onChunkUnloaded()`: чанк с этой блок-сущностью выгружается,
     * данные уже записаны на диск. Дефолт пустой.
     */
    void onChunkUnloaded();
}

/*
 * Часть nfcompat. Сущность, которая сама пишет свои данные при спавне.
 *
 * В ванили `Entity` за спавн-данные отвечает механизм `EntityType`/`SpawnPacket`, а
 * NeoForge даёт сущности хуки: `writeSpawnData`/`readSpawnData`. Реализация на стороне
 * Fabric должна вызывать их из своего spawn-пакета — пока это TODO, интерфейс нужен
 * для компиляции AE2.
 */
package net.neoforged.neoforge.entity;

import net.minecraft.network.RegistryFriendlyByteBuf;

public interface IEntityWithComplexSpawn {
    void writeSpawnData(RegistryFriendlyByteBuf buffer);

    void readSpawnData(RegistryFriendlyByteBuf additionalData);
}

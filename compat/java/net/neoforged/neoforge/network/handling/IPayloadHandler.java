/*
 * Часть nfcompat. NeoForge-овский IPayloadHandler.
 *
 * Полностью идентичен оригиналу, платформенной специфики нет.
 */
package net.neoforged.neoforge.network.handling;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Обработчик пришедшего payload'а. В NeoForge его регистрируют либо через
 * PayloadRegistrar, либо через RegisterClientPayloadHandlersEvent.
 */
@FunctionalInterface
public interface IPayloadHandler<T extends CustomPacketPayload> {
    void handle(T payload, IPayloadContext context);
}

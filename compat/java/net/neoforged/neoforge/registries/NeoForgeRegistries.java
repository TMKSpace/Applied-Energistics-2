/*
 * Часть nfcompat. Шим net.neoforged.neoforge.registries.NeoForgeRegistries для Fabric.
 *
 * ОТЛИЧИЕ ОТ NeoForge: там все поля - готовые Registry, созданные RegistryBuilder'ом.
 * На Fabric таких реестров нет и создать их из шима нельзя, поэтому здесь поля - это
 * ResourceKey реестра, а не сам реестр. Для AE2 это безразлично: DeferredRegister.create
 * принимает и ключ, и реестр, а значения для отсутствующего реестра складываются в
 * локальную мапу DeferredRegister (см. пункт 2 в DeferredRegister.java).
 *
 * Если какому-то моду придётся РЕАЛЬНО читать такой реестр (get/id/итерация) - это
 * придётся доделывать: нужен настоящий Registry (MappedRegistry) и его регистрация
 * в BuiltInRegistries.REGISTRY до заморозки.
 */
package net.neoforged.neoforge.registries;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.attachment.AttachmentType;

/**
 * Статические ссылки на реестры NeoForge (в нашей версии - только ключи).
 *
 * <p>Ванильные реестры лежат в {@link BuiltInRegistries}, их ключи - в
 * {@link net.minecraft.core.registries.Registries}.
 */
public class NeoForgeRegistries {

    // Ключ реестра attachment-типов. В NeoForge здесь лежит Registry<AttachmentType<?>>.
    public static final ResourceKey<Registry<AttachmentType<?>>> ATTACHMENT_TYPES = Keys.ATTACHMENT_TYPES;

    public static final class Keys {
        public static final ResourceKey<Registry<AttachmentType<?>>> ATTACHMENT_TYPES = key("attachment_types");

        private static <T> ResourceKey<Registry<T>> key(String name) {
            return ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath("neoforge", name));
        }
    }
}

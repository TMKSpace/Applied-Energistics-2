/*
 * Часть nfcompat. В NeoForge этот класс — большая склейка "по умолчанию":
 * он регистрирует провайдеры для сундуков, вёдер, бандлов, печек, компостеров и т.д.
 *
 * Здесь оставлен каркас: ванильную интероперабельность будем давать не копированием
 * этого класса, а мостом в Fabric Transfer API (ItemStorage.SIDED / FluidStorage.SIDED),
 * чтобы AE2 видел контейнеры других модов и наоборот.
 */
package net.neoforged.neoforge.capabilities;

public final class CapabilityHooks {
    /**
     * В NeoForge выставляется в true после того, как RegisterCapabilitiesEvent отработал.
     * Используется как защита от поздней регистрации.
     */
    public static boolean initFinished = false;

    private CapabilityHooks() {
    }

    /**
     * Регистрация ванильных/дефолтных провайдеров.
     * Пока пусто: см. план про мост в Fabric Transfer API.
     */
    public static void registerVanillaProviders(RegisterCapabilitiesEvent event) {
        // TODO: мост в Fabric Storage — отдельный этап.
    }
}

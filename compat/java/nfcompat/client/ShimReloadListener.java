/*
 * Часть nfcompat. Обёртка NeoForge'вского reload-слушателя под Fabric.
 *
 * NeoForge разрешает добавлять произвольный PreparableReloadListener и сам заботится
 * о порядке (граф зависимостей). Fabric требует, чтобы у слушателя был Identifier,
 * а зависимости объявлялись явно через getFabricDependencies(). Обёртка только
 * делегирует вызовы и отдаёт эти два идентификатора — вся логика остаётся у AE2.
 */
package nfcompat.client;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;

public final class ShimReloadListener implements IdentifiableResourceReloadListener {
    private final Identifier id;
    private final List<Identifier> dependencies;
    private final PreparableReloadListener delegate;

    public ShimReloadListener(Identifier id, List<Identifier> dependencies, PreparableReloadListener delegate) {
        this.id = id;
        this.dependencies = List.copyOf(dependencies);
        this.delegate = delegate;
    }

    @Override
    public Identifier getFabricId() {
        return id;
    }

    /** Слушатели, которые должны выполниться раньше этого. */
    @Override
    public Collection<Identifier> getFabricDependencies() {
        return dependencies;
    }

    @Override
    public CompletableFuture<Void> reload(PreparableReloadListener.SharedState state,
            Executor preparationExecutor,
            PreparableReloadListener.PreparationBarrier barrier,
            Executor reloadExecutor) {
        return delegate.reload(state, preparationExecutor, barrier, reloadExecutor);
    }

    @Override
    public void prepareSharedState(PreparableReloadListener.SharedState state) {
        delegate.prepareSharedState(state);
    }

    @Override
    public String getName() {
        return delegate.getName();
    }
}

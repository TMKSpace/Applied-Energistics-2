package appeng.mixins.client;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.client.resources.model.BlockStateModelLoader;
import net.minecraft.server.packs.resources.ResourceManager;

import appeng.client.AppEngClient;

@Mixin(BlockStateModelLoader.class)
public class PartModelLoaderMixin {
    private static final Logger LOGGER = LogManager.getLogger("nfcompat/models");

    @WrapMethod(method = "loadBlockStates")
    private static CompletableFuture<BlockStateModelLoader.LoadedModels> loadBlockStates(
            ResourceManager resourceManager,
            Executor executor,
            Operation<CompletableFuture<BlockStateModelLoader.LoadedModels>> operation) {
        // Модели частей AE2 грузятся ПАРАЛЛЕЛЬНО с ванильным лоадером blockstate.
        // Если наша загрузка упадёт, необработанное исключение утопит общий future
        // и модели не загрузятся у ВСЕХ модов. Поэтому ошибку изолируем: логируем
        // и продолжаем с ванильным результатом.
        CompletableFuture<Void> partModelReload;
        try {
            partModelReload = AppEngClient.instance().getPartModels()
                    .reload(resourceManager, executor)
                    .thenApply(_ -> (Void) null);
        } catch (RuntimeException e) {
            LOGGER.error("AE2: модели частей не загрузились", e);
            partModelReload = CompletableFuture.completedFuture(null);
        }
        partModelReload = partModelReload.exceptionally(e -> {
            LOGGER.error("AE2: модели частей не загрузились", e);
            return null;
        });
        return partModelReload.thenCombine(operation.call(resourceManager, executor),
                (_, loadedModels) -> loadedModels);
    }
}

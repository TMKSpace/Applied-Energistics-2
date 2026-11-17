/*
 * Часть nfcompat (GuideME). `CommonListenerCookie` без `ConnectionType`.
 *
 * GuideME создаёт «фейкового игрока» для 3D-сцены (`FakeRenderEnvironment.createFakePlayer`)
 * и конструирует `CommonListenerCookie`, передавая последним аргументом неофорджевский
 * `ConnectionType`. В ванили конструктор на один аргумент короче:
 *
 *     ... Map<UUID, PlayerInfo>, boolean                     (ваниль, 14)
 *     ... Map<UUID, PlayerInfo>, boolean, ConnectionType     (NeoForge, 15)
 *
 * Конструктором это не лечится (Mixin не добавляет конструкторы), поэтому подменяем создание
 * объекта на ванильный 14-аргументный: `ConnectionType` нужен только сетевому слою NeoForge,
 * а фейковый клиент никуда не подключается.
 */
package nfcompat.mixin.guideme;

import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.telemetry.WorldSessionTelemetryManager;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ServerLinks;
import net.minecraft.world.flag.FeatureFlagSet;
import net.neoforged.neoforge.network.connection.ConnectionType;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "guideme.internal.scene.FakeRenderEnvironment")
public class FakeRenderEnvironmentMixin {

    @SuppressWarnings("rawtypes")
    @Redirect(method = "createFakePlayer", require = 0,
            at = @At(value = "NEW", target = "net/minecraft/client/multiplayer/CommonListenerCookie"))
    private static CommonListenerCookie nfcompat$cookie(LevelLoadTracker levelLoadTracker,
            GameProfile profile,
            WorldSessionTelemetryManager telemetry,
            RegistryAccess.Frozen registries,
            FeatureFlagSet enabledFeatures,
            String serverBrand,
            ServerData serverData,
            Screen screen,
            Map<Identifier, byte[]> resourcePackIds,
            ChatComponent.State chatState,
            Map<String, String> language,
            ServerLinks serverLinks,
            Map<UUID, PlayerInfo> playerInfo,
            boolean flag,
            ConnectionType connectionType) {
        return new CommonListenerCookie(levelLoadTracker, profile, telemetry, registries, enabledFeatures,
                serverBrand, serverData, screen, resourcePackIds, chatState, language, serverLinks,
                playerInfo, flag);
    }
}

$ErrorActionPreference = 'Stop'
# Generates skeleton NeoForge event classes. Members are added iteratively from compile errors.
$base = 'D:\Code\java\ae2-fabric-probe\compat\java\net\neoforged'

$events = [ordered]@{
  'net.neoforged.neoforge.common.NeoForge'                        = @()
  'net.neoforged.neoforge.event.EventHooks'                       = @()
  'net.neoforged.neoforge.event.OnDatapackSyncEvent'              = @()
  'net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent'= @()
  'net.neoforged.neoforge.event.RegisterCommandsEvent'            = @()
  'net.neoforged.neoforge.event.AddServerReloadListenersEvent'    = @()
  'net.neoforged.neoforge.event.RegisterGameTestsEvent'           = @()
  'net.neoforged.neoforge.event.entity.player.PlayerEvent'        = @('ItemCraftedEvent','PlayerLoggedInEvent','PlayerLoggedOutEvent','Clone','BreakSpeed')
  'net.neoforged.neoforge.event.entity.player.PlayerInteractEvent' = @('LeftClickBlock','RightClickBlock','RightClickItem','LeftClickEmpty')
  'net.neoforged.neoforge.event.entity.player.PlayerDestroyItemEvent' = @()
  'net.neoforged.neoforge.event.level.LevelEvent'                 = @('Load','Unload','Save','PotentialSpawns')
  'net.neoforged.neoforge.event.level.ChunkEvent'                 = @('Load','Unload')
  'net.neoforged.neoforge.event.level.block.BreakBlockEvent'      = @()
  'net.neoforged.neoforge.event.server.ServerAboutToStartEvent'   = @()
  'net.neoforged.neoforge.event.server.ServerStartedEvent'        = @()
  'net.neoforged.neoforge.event.server.ServerStoppingEvent'       = @()
  'net.neoforged.neoforge.event.server.ServerStoppedEvent'        = @()
  'net.neoforged.neoforge.event.tick.ServerTickEvent'             = @('Pre','Post')
  'net.neoforged.neoforge.event.tick.LevelTickEvent'              = @('Pre','Post')
  'net.neoforged.neoforge.client.event.ClientTickEvent'           = @('Pre','Post')
  'net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent'  = @('LoggingIn','LoggingOut','Clone')
  'net.neoforged.neoforge.client.event.RegisterClientCommandsEvent' = @()
  'net.neoforged.neoforge.client.event.RecipesReceivedEvent'      = @()
  'net.neoforged.neoforge.client.event.ModelEvent'                = @()
  'net.neoforged.neoforge.client.event.InputEvent'                = @('Key','Mouse','InteractionKeyMappingTriggered')
  'net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent' = @()
  'net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent' = @()
  'net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent' = @()
  'net.neoforged.neoforge.client.event.RegisterBlockStateModels'  = @()
  'net.neoforged.neoforge.client.event.AddClientReloadListenersEvent' = @()
  'net.neoforged.neoforge.client.event.InitializeClientRegistriesEvent' = @()
  'net.neoforged.neoforge.client.event.EntityRenderersEvent'      = @()
  'net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent' = @()
  'net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent' = @()
  'net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent' = @()
  'net.neoforged.neoforge.client.event.RegisterRangeSelectItemModelPropertyEvent' = @()
  'net.neoforged.neoforge.client.event.RegisterMenuScreensEvent'  = @()
  'net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent' = @()
  'net.neoforged.neoforge.client.event.RegisterCustomEnvironmentEffectRendererEvent' = @()
  'net.neoforged.neoforge.client.event.RegisterColorHandlersEvent' = @()
  'net.neoforged.neoforge.client.event.RegisterItemModelsEvent'   = @()
  'net.neoforged.neoforge.client.event.RegisterParticleGroupsEvent' = @()
  'net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent'  = @()
  'net.neoforged.neoforge.client.event.RegisterGuiLayersEvent'    = @()
  'net.neoforged.neoforge.client.event.ViewportEvent'             = @('ComputeFogColor','ComputeCameraAngles')
}

$made = 0
foreach ($fq in $events.Keys) {
    $nested = $events[$fq]
    $rel = ($fq -replace '^net\.neoforged\.', '') -replace '\.', '\'
    $parts = $fq.Split('.')
    $pkg = ($parts[0..($parts.Length - 2)]) -join '.'
    $cls = $parts[-1]
    $dir = Join-Path $base (Split-Path $rel -Parent)
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $file = Join-Path $dir ($cls + '.java')

    if ($fq -eq 'net.neoforged.neoforge.common.NeoForge') {
        $body = @"
package $pkg;

import net.neoforged.bus.api.IEventBus;

/**
 * nfcompat: replaces NeoForge#EVENT_BUS. Bus implementation is SimpleEventBus.
 */
public final class NeoForge {
    public static final IEventBus EVENT_BUS = new net.neoforged.bus.api.SimpleEventBus();

    private NeoForge() {
    }
}
"@
    } elseif ($fq -eq 'net.neoforged.neoforge.event.EventHooks') {
        $body = @"
package $pkg;

/**
 * nfcompat: in NeoForge this class holds dozens of hooks into vanilla code.
 * Left empty, filled on demand.
 */
public final class EventHooks {
    private EventHooks() {
    }
}
"@
    } else {
        $sb = New-Object System.Text.StringBuilder
        [void]$sb.AppendLine("package $pkg;")
        [void]$sb.AppendLine()
        [void]$sb.AppendLine("import net.neoforged.bus.api.Event;")
        [void]$sb.AppendLine()
        [void]$sb.AppendLine("/**")
        [void]$sb.AppendLine(" * nfcompat: NeoForge event skeleton. Members are added on demand.")
        [void]$sb.AppendLine(" */")
        [void]$sb.AppendLine("public class $cls extends Event {")
        [void]$sb.AppendLine("    public $cls() {")
        [void]$sb.AppendLine("    }")
        foreach ($n in $nested) {
            [void]$sb.AppendLine()
            [void]$sb.AppendLine("    public static class $n extends $cls {")
            [void]$sb.AppendLine("        public $n() {")
            [void]$sb.AppendLine("        }")
            [void]$sb.AppendLine("    }")
        }
        [void]$sb.AppendLine("}")
        $body = $sb.ToString()
    }

    [System.IO.File]::WriteAllText($file, $body, (New-Object System.Text.UTF8Encoding($false)))
    $made++
}
"generated files: $made"

$ErrorActionPreference = 'Stop'
$r = 'D:\Code\java\ae2-fabric-probe\src'
$files = Get-ChildItem $r -Recurse -Filter *.java

"=== NeoForge.EVENT_BUS usages ==="
$files | Select-String -Pattern 'NeoForge\.EVENT_BUS' | ForEach-Object {
    ($_.Path -replace '.*\\src\\', '') + ':' + $_.LineNumber + ': ' + $_.Line.Trim()
} | Sort-Object -Unique | Select-Object -First 25
""
"=== импорты net.neoforged.neoforge.event.* / client.event.* / bus.api.* ==="
$files | Select-String -Pattern '^import net\.neoforged\.(neoforge\.(client\.)?event|bus\.api)\.' | ForEach-Object {
    ($_.Line -replace '\s*import ', '').Trim()
} | Group-Object | Sort-Object Count -Descending | ForEach-Object { "{0,4}  {1}" -f $_.Count, $_.Name }

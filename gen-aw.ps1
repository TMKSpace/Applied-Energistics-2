$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot'
$jp = "$env:JAVA_HOME\bin\javap.exe"
$c1 = 'C:\Users\Kot\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft\minecraft-clientonly-deobf\26.3\minecraft-clientonly-deobf-26.3.jar'
$c2 = 'C:\Users\Kot\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft\minecraft-common-deobf\26.3\minecraft-common-deobf-26.3.jar'
$cp = "$c1;$c2"

$atFile = 'C:\Users\Kot\AppData\Local\Temp\opencode\ae2\main\src\main\resources\META-INF\accesstransformer.cfg'
$outFile = 'D:\Code\java\ae2-fabric-probe\src\main\resources\ae2.accesswidener'

$lines = Get-Content $atFile | Where-Object { $_ -match '\S' -and $_ -notmatch '^\s*#' }

$fieldCache = @{}
function Get-FieldDescriptor([string]$owner, [string]$field) {
    if (-not $fieldCache.ContainsKey($owner)) {
        $fieldCache[$owner] = & $jp -p -s -classpath $cp $owner 2>&1
    }
    $out = $fieldCache[$owner]
    for ($i = 0; $i -lt $out.Count - 1; $i++) {
        if ($out[$i] -match ("\b" + [regex]::Escape($field) + ";$") -and $out[$i + 1] -match 'descriptor:\s*(\S+)') {
            return $Matches[1]
        }
    }
    return $null
}

$entries = New-Object System.Collections.Generic.List[string]

foreach ($l in $lines) {
    $l = ($l -split '#')[0].Trim()
    if (-not $l) { continue }
    $parts = $l -split '\s+'
    $access = $parts[0]
    if ($parts.Count -eq 3 -and $parts[2] -notmatch '\(') {
        # поле: <access> <owner> <name>
        $owner = $parts[1]; $name = $parts[2]
        $desc = Get-FieldDescriptor $owner $name
        if (-not $desc) { Write-Warning "no descriptor for $owner.$name"; continue }
        $entries.Add(("accessible field {0} {1} {2}" -f ($owner -replace '\.', '/'), $name, $desc))
    } elseif ($parts.Count -eq 3) {
        # метод: <access> <owner> <name><descriptor> -> в classtweaker это два токена
        $owner = $parts[1]
        $sig = $parts[2]
        $p = $sig.IndexOf('(')
        if ($p -lt 0) { Write-Warning "bad method sig: $sig"; continue }
        $name = $sig.Substring(0, $p)
        $desc = $sig.Substring($p)
        $entries.Add(("accessible method {0} {1} {2}" -f ($owner -replace '\.', '/'), $name, $desc))
    } else {
        Write-Warning "unparsed: $l"
    }
}

# то, что мы добавили сами для шима (NeoForge-ные «пропатченные» поля)
$entries.Add('accessible field net/minecraft/world/level/block/entity/BlockEntityType validBlocks Ljava/util/Set;')
$entries.Add('accessible field net/minecraft/world/entity/EquipmentSlot countLimit I')
$entries.Add('accessible field net/minecraft/world/item/BucketItem content Lnet/minecraft/world/level/material/Fluid;')

$header = @(
    'accessWidener v2 official',
    '# Сгенерировано из accesstransformer.cfg AE2 (31 запись) + 3 поля для шима.',
    '# AT: public/public-f/protected/protected-f -> accessible'
)
$content = ($header + $entries) -join "`n"
Set-Content -Path $outFile -Value $content -Encoding ASCII
"записано записей: " + $entries.Count
$entries | Select-Object -First 8

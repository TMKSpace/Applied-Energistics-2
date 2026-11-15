param([switch]$Apply)
$ErrorActionPreference = 'Stop'
$root = 'D:\Code\java\ae2-fabric-probe'
$compat = Join-Path $root 'compat\java'
$src = Join-Path $root 'src'

$compatFiles = Get-ChildItem $compat -Recurse -Filter *.java
$srcText = (Get-ChildItem $src -Recurse -Filter *.java | Get-Content -Raw) -join "`n"

$keep = New-Object System.Collections.Generic.HashSet[string]
foreach ($f in $compatFiles) {
    $name = [System.IO.Path]::GetFileNameWithoutExtension($f.Name)
    if ($name -eq 'package-info') { continue }
    if ($srcText -match ("\b" + [regex]::Escape($name) + "\b")) { [void]$keep.Add($f.FullName) }
}

$changed = $true
while ($changed) {
    $changed = $false
    $keptText = ($keep | ForEach-Object { Get-Content -Raw $_ }) -join "`n"
    foreach ($f in $compatFiles) {
        if ($keep.Contains($f.FullName)) { continue }
        $name = [System.IO.Path]::GetFileNameWithoutExtension($f.Name)
        if ($name -eq 'package-info') { continue }
        if ($keptText -match ("\b" + [regex]::Escape($name) + "\b")) {
            [void]$keep.Add($f.FullName)
            $changed = $true
        }
    }
}

$drop = $compatFiles | Where-Object { -not $keep.Contains($_.FullName) -and [System.IO.Path]::GetFileNameWithoutExtension($_.Name) -ne 'package-info' }
"keep: " + $keep.Count + "  drop: " + $drop.Count
if ($Apply) {
    foreach ($f in $drop) { Remove-Item $f.FullName -Force }
    "deleted: " + $drop.Count
} else {
    $drop | ForEach-Object { '  ' + ($_.FullName -replace [regex]::Escape($compat + '\'), '') }
}

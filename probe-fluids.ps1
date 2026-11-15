$ErrorActionPreference = 'Stop'
Set-Location 'D:\Code\java\ae2-fabric-probe\src'
$files = Get-ChildItem . -Recurse -Filter *.java | Select-String -Pattern 'import net\.neoforged\.neoforge\.fluids' -List | Select-Object -ExpandProperty Path
"files: " + $files.Count
$files | ForEach-Object { "  " + ($_ -replace '.*\\src\\', '') }
""
"=== FluidStack / FluidType usages ==="
$lines = foreach ($f in $files) {
    Select-String -Path $f -Pattern 'FluidStack|FluidType' | ForEach-Object {
        ($_.Filename) + ':' + $_.LineNumber + ': ' + $_.Line.Trim()
    }
}
$lines | Sort-Object -Unique

param([string]$LogName = 'build-probe2.log')
# логи тестов лежат в .opencode, чтобы не мусорить в корне проекта
$logDir = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) '.opencode'
$logPath = Join-Path $logDir $LogName
if (-not (Test-Path $logPath)) {
    $logPath = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) $LogName
}
$ErrorActionPreference = 'Stop'
$root = 'D:\Code\java\ae2-fabric-probe'
$log  = Get-Content $logPath

$re = '^(?<file>\s*.+?\.java):(?<line>\d+): error: (?<msg>.*)$'
$seen = @{}
foreach ($l in $log) {
    $m = [regex]::Match($l, $re)
    if ($m.Success) {
        $key = $m.Groups['file'].Value.Trim() + '|' + $m.Groups['line'].Value + '|' + $m.Groups['msg'].Value.Trim()
        if (-not $seen.ContainsKey($key)) {
            $seen[$key] = [pscustomobject]@{
                file = $m.Groups['file'].Value.Trim()
                line = [int]$m.Groups['line'].Value
                msg  = $m.Groups['msg'].Value.Trim()
            }
        }
    }
}
$uniq = $seen.Values
"log                  : $LogName"
"unique errors        : " + $uniq.Count
"files with errors    : " + (($uniq | Select-Object -ExpandProperty file -Unique) | Measure-Object).Count
""
"=== TOP 20 ==="
$uniq | Group-Object msg | Sort-Object Count -Descending | Select-Object -First 20 | ForEach-Object {
    "{0,5}  {1}" -f $_.Count, $_.Name
}
""
"=== missing packages (root) ==="
$uniq | Where-Object { $_.msg -like 'package * does not exist' } | ForEach-Object {
    if ($_.msg -match 'package (\S+) does not exist') { $Matches[1] }
} | ForEach-Object {
    if ($_ -match '^net\.neoforged') { 'net.neoforged' }
    elseif ($_ -match '^snownee|^mcp\.mobius') { 'third-party jade/wthit' }
    elseif ($_ -match '^me\.shedaniel|^dev\.emi|^mezz\.jei|^top\.theillusivec4') { 'third-party rei/emi/jei/curios' }
    else { 'other: ' + $_ }
} | Group-Object | Sort-Object Count -Descending | Select-Object -First 12 | ForEach-Object {
    "{0,5}  {1}" -f $_.Count, $_.Name
}

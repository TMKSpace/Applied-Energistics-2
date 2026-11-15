param([string]$Filter = 'compat', [string]$LogName = 'build-probe8.log')
$ErrorActionPreference = 'Stop'
$log = Get-Content (Join-Path 'D:\Code\java\ae2-fabric-probe' $LogName)
$show = $false
$out = New-Object System.Collections.Generic.List[string]
for ($i = 0; $i -lt $log.Count; $i++) {
    $line = $log[$i]
    if ($line -match 'error: ') {
        $show = $line -match $Filter
        if ($show) { $out.Add('---') }
    }
    if ($show) { $out.Add($line.Trim()) }
}
$out | Select-Object -First 220

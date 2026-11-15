param([string]$LogName = 'build-probe6.log')
# логи тестов лежат в .opencode, чтобы не мусорить в корне проекта
$logDir = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) '.opencode'
$logPath = Join-Path $logDir $LogName
if (-not (Test-Path $logPath)) {
    $logPath = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) $LogName
}
$ErrorActionPreference = 'Stop'
$log = Get-Content $logPath
$re = '^(?<f>\s*.+?\.java):(?<l>\d+): error: (?<m>.*)$'
$rows = foreach ($x in $log) {
    $mm = [regex]::Match($x, $re)
    if ($mm.Success) {
        [pscustomobject]@{ file = $mm.Groups['f'].Value.Trim(); line = $mm.Groups['l'].Value; msg = $mm.Groups['m'].Value.Trim() }
    }
}
$compat = $rows | Where-Object { $_.file -match '\\compat\\' } | Sort-Object file, line, msg -Unique
"errors inside shim (compat): " + $compat.Count
$compat | ForEach-Object { ($_.file -replace '.*\\compat\\java\\', '') + ':' + $_.line + ': ' + $_.msg }

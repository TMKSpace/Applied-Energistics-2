param([string]$LogName = 'build-probe15.log')
# логи тестов лежат в .opencode, чтобы не мусорить в корне проекта
$logDir = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) '.opencode'
$logPath = Join-Path $logDir $LogName
if (-not (Test-Path $logPath)) {
    $logPath = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) $LogName
}
$ErrorActionPreference = 'Stop'
$log = Get-Content $logPath
$inCompat = $false
$sym = New-Object System.Collections.Generic.List[string]
$pkg = New-Object System.Collections.Generic.List[string]
for ($i = 0; $i -lt $log.Count; $i++) {
    $l = $log[$i]
    if ($l -match 'error: ') { $inCompat = $l -match '\\compat\\' }
    if (-not $inCompat) { continue }
    if ($l -match 'error: package ([\w\.]+) does not exist') { $pkg.Add($Matches[1]) }
    if ($l -match 'symbol:\s+(class|interface|enum|variable|method)\s+(\S+)') { $sym.Add($Matches[2]) }
}
"=== missing packages (compat) ==="
$pkg | Group-Object | Sort-Object Count -Descending | ForEach-Object { "{0,4}  {1}" -f $_.Count, $_.Name }
""
"=== missing symbols (compat), top 40 ==="
$sym | Group-Object | Sort-Object Count -Descending | Select-Object -First 40 | ForEach-Object { "{0,4}  {1}" -f $_.Count, $_.Name }

param([string]$LogName = 'build-probe18.log')
# логи тестов лежат в .opencode, чтобы не мусорить в корне проекта
$logDir = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) '.opencode'
$logPath = Join-Path $logDir $LogName
if (-not (Test-Path $logPath)) {
    $logPath = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) $LogName
}
$ErrorActionPreference = 'Stop'
$log = Get-Content $logPath
$re = [regex]'^\s*(\S+\.java):(\d+): error: (.*)$'
for ($i = 0; $i -lt $log.Count; $i++) {
    $l = $log[$i]
    $m = $re.Match($l)
    if (-not $m.Success) { continue }
    if ($m.Groups[1].Value -notmatch '\\compat\\') { continue }
    $file = $m.Groups[1].Value -replace '.*\\compat\\java\\net\\neoforged\\neoforge\\', ''
    Write-Output ($file + ':' + $m.Groups[2].Value + ' | ' + $m.Groups[3].Value)
    $j = $i + 1
    while ($j -lt $log.Count) {
        $n = $log[$j]
        if ($n -match 'error:') { break }
        if ($n.Trim() -eq '') { break }
        if ($n -match '^\s*(symbol|location|required|found|reason):') {
            Write-Output ('      ' + $n.Trim())
        } elseif ($n -match '^\s{4,}\S' -and $n -notmatch '^\s*\^') {
            Write-Output ('      ' + $n.Trim())
        } else {
            break
        }
        $j++
    }
}
